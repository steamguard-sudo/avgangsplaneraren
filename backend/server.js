/**
 * backend/server.js
 *
 * Litet backend som:
 *  1. Döljer Google-API-nyckeln (den ligger aldrig i appen).
 *  2. Cachar ruttsvar mellan ALLA användare — samma sträcka (avrundad till
 *     ~1 km) behöver bara hämtas från Google en gång, sen återanvänds den.
 *     Detta är den enskilt största kostnadsbesparingen (se kostnads-
 *     resonemanget i chatten / teknisk-plan-avgangsplaneraren.md).
 *
 * Cachen ligger i SQLite (se cache.js), inte i minnet — det gör att den
 * överlever att processen startar om. Läs kommentaren högst upp i cache.js
 * för vad som fortfarande krävs (en beständig disk) för att den ska
 * garanterat överleva på en PaaS gratisnivå.
 *
 * Kom igång:
 *   cd backend
 *   npm install
 *   cp .env.example .env      # fyll i din GOOGLE_API_KEY
 *   npm start
 */

require("dotenv").config();
const express = require("express");
const cache = require("./cache");
const app = express();

const GOOGLE_API_KEY = process.env.GOOGLE_API_KEY;
// NOBIL-nyckeln är valfri på serverstartsnivå (till skillnad från
// GOOGLE_API_KEY) — saknas den ska bara /charging-nobil sluta fungera
// (tydligt 500-fel), inte hela backend krascha vid start.
const NOBIL_API_KEY = process.env.NOBIL_API_KEY;
// Extra loggning av rådata från NOBIL (avstånd per station, rått attr.st),
// avstängd som standard. Sätt NOBIL_DEBUG=true i miljön för att slå på den
// när task:et om avståndsgränsen/avgiftsfältet ska felsökas mot riktig data.
const NOBIL_DEBUG = process.env.NOBIL_DEBUG === "true";
const PORT = process.env.PORT || 3000;

if (!GOOGLE_API_KEY) {
  console.error("Saknar GOOGLE_API_KEY i miljövariablerna. Se .env.example.");
  process.exit(1);
}

const CACHE_TTL_MS = 1000 * 60 * 60 * 24 * 30; // 30 dagar — vägar ändras sällan
const PLACES_CACHE_TTL_MS = 1000 * 60 * 60 * 24 * 90; // 90 dagar — adresser/orter ändras nästan aldrig
const OVERNIGHT_CACHE_TTL_MS = 1000 * 60 * 60 * 24 * 14; // 14 dagar — OSM-data uppdateras oftare av communityn
const NOBIL_CACHE_TTL_MS = 1000 * 60 * 60 * 24; // 24 timmar — NOBIL anger att deras data uppdateras på timnivå
// Negativ-cache: hur länge ett "alla Overpass-speglar dog"-utfall får kortsluta
// nya anrop för samma område till ett snabbt 502, i stället för att varje
// ruttpunkt i varje ompanering (findOvernight/ChargingStationsAlongRoute kör
// dem parallellt) försöker mot alla fyra speglarna igen.
const NEGATIVE_CACHE_TTL_MS = 1000 * 60 * 10; // 10 minuter

// --- Overpass-robusthet (se runOverpassQuery / postOverpassQuery nedan) ---
// Hård timeout per enskild spegel, via AbortController.
const OVERPASS_ATTEMPT_TIMEOUT_MS = parseInt(process.env.OVERPASS_ATTEMPT_TIMEOUT_MS, 10) || 20000;
// Total väggklockebudget för HELA spegel-rundan. Måste hållas under appens
// OkHttp-timeouter (readTimeout 60 s / callTimeout 75 s i BackendHttp.kt),
// annars hinner appen ge upp först och backendens fortsatta försök blir
// bortkastade. 4 speglar × 20 s hade blivit 80 s — därför en gemensam budget.
const OVERPASS_TOTAL_BUDGET_MS = parseInt(process.env.OVERPASS_TOTAL_BUDGET_MS, 10) || 45000;
// Overpass usage policy ber om en beskrivande User-Agent med en riktig
// kontaktväg, så de kan höra av sig i stället för att blockera er IP.
const OVERPASS_CONTACT =
  process.env.OVERPASS_CONTACT || "https://github.com/steamguard-sudo/avgangsplaneraren";
const OVERPASS_USER_AGENT = `Avgangsplaneraren/1.0 (+${OVERPASS_CONTACT})`;
// Delad spegel-lista, provas i tur och ordning. overpass-api.de blockar ibland
// moln-IP (t.ex. Render); de andra tre är community-drivna alternativ med
// historiskt hyfsad uptime.
const OVERPASS_ENDPOINTS = [
  "https://overpass-api.de/api/interpreter",
  "https://overpass.kumi.systems/api/interpreter",
  "https://overpass.private.coffee/api/interpreter",
  "https://overpass.osm.ch/api/interpreter",
];

/** Avrundar koordinater till ~1 km precision, så närliggande förfrågningar delar cache-post. */
function roundCoord(value) {
  return Math.round(value * 100) / 100; // ~1.1 km precision vid svenska breddgrader
}

function cacheKey(fromLat, fromLon, toLat, toLon) {
  return [roundCoord(fromLat), roundCoord(fromLon), roundCoord(toLat), roundCoord(toLon)].join("|");
}

app.get("/route", async (req, res) => {
  const fromLat = parseFloat(req.query.fromLat);
  const fromLon = parseFloat(req.query.fromLon);
  const toLat = parseFloat(req.query.toLat);
  const toLon = parseFloat(req.query.toLon);

  if ([fromLat, fromLon, toLat, toLon].some((v) => Number.isNaN(v))) {
    return res.status(400).json({ error: "fromLat, fromLon, toLat, toLon krävs som tal" });
  }

  const key = cacheKey(fromLat, fromLon, toLat, toLon);
  const cached = cache.get(key, CACHE_TTL_MS);
  if (cached) {
    return res.json({ ...cached, cached: true });
  }

  try {
    const result = await fetchRouteFromGoogle(fromLat, fromLon, toLat, toLon);
    cache.set(key, result);
    res.json({ ...result, cached: false });
  } catch (err) {
    console.error("Fel vid anrop mot Google Routes API:", err.message);
    res.status(502).json({ error: "Kunde inte hämta rutt från Google" });
  }
});

/**
 * Fritextsökning av platser/adresser i Sverige, för autokompletteringsfälten
 * i appen. Cachas per sökterm — samma bokstäver någon skriver ("jönk...")
 * behöver bara slås upp mot Google en gång totalt, sen återanvänds den för
 * alla användare som skriver samma sak.
 */
app.get("/places/autocomplete", async (req, res) => {
  const query = (req.query.query || "").toString().trim();
  if (!query) {
    return res.json({ suggestions: [] });
  }

  const key = `places:autocomplete:${query.toLowerCase()}`;
  const cached = cache.get(key, PLACES_CACHE_TTL_MS);
  if (cached) {
    return res.json(cached);
  }

  try {
    const result = await fetchAutocompleteFromGoogle(query);
    cache.set(key, result);
    res.json(result);
  } catch (err) {
    console.error("Fel vid anrop mot Google Places Autocomplete:", err.message);
    res.status(502).json({ error: "Kunde inte söka platser just nu" });
  }
});

/** Slår upp koordinaten för ett tidigare valt förslag (placeId från autocomplete ovan). */
app.get("/places/details", async (req, res) => {
  const placeId = (req.query.placeId || "").toString().trim();
  if (!placeId) {
    return res.status(400).json({ error: "placeId krävs" });
  }

  const key = `places:details:${placeId}`;
  const cached = cache.get(key, PLACES_CACHE_TTL_MS);
  if (cached) {
    return res.json(cached);
  }

  try {
    const result = await fetchPlaceDetailsFromGoogle(placeId);
    cache.set(key, result);
    res.json(result);
  } catch (err) {
    console.error("Fel vid anrop mot Google Place Details:", err.message);
    res.status(502).json({ error: "Kunde inte hämta platsinformation" });
  }
});

/**
 * Övernattningsplatser (husbil/husvagn/tältplats) nära en punkt, från
 * OpenStreetMap via Overpass API — ett komplement till Trafikverkets
 * rastplatser, för resenärer som planerar att övernatta på vägen.
 * Data: © OpenStreetMap contributors, ODbL-licens.
 */
// Enda tillåtna OSM tourism-taggar för den här endpointen — eftersom
// `types` byggs in direkt i Overpass-frågesträngen nedan, och endpointen
// är offentlig (vem som helst kan anropa den, inte bara appen), whitelistas
// värdena här istället för att lita blint på vad som skickas in.
const ALLOWED_OVERNIGHT_TYPES = new Set(["caravan_site", "camp_site"]);

app.get("/overnight", async (req, res) => {
  const lat = parseFloat(req.query.lat);
  const lon = parseFloat(req.query.lon);
  const radiusKm = parseFloat(req.query.radiusKm) || 20;
  const types = (req.query.types || "caravan_site,camp_site")
    .toString()
    .split(",")
    .map((t) => t.trim())
    .filter((t) => ALLOWED_OVERNIGHT_TYPES.has(t));

  if (Number.isNaN(lat) || Number.isNaN(lon)) {
    return res.status(400).json({ error: "lat och lon krävs som tal" });
  }
  if (types.length === 0) {
    return res.json({ spots: [] });
  }

  const key = `overnight:${roundCoord(lat)},${roundCoord(lon)}:${radiusKm}:${[...types].sort().join(",")}`;
  const cached = cache.get(key, OVERNIGHT_CACHE_TTL_MS);
  if (cached) {
    return res.json({ ...cached, cached: true });
  }
  // Negativ-cache: om alla Overpass-speglar nyligen (inom NEGATIVE_CACHE_TTL_MS)
  // dog för det här området, svara 502 direkt utan att hamra speglarna igen.
  // Appen (findOvernightSpotsAlongRoute) tolkar 502 som hadFailure=true och
  // visar felstatus — samma utfall som ett riktigt misslyckat anrop, till
  // skillnad från ett tomt 200-svar som hade sett ut som "inga träffar".
  // Egen `:neg`-nyckel så en gammal negativ post aldrig blockerar en riktig
  // positiv träff (den läses ovan, före den här) och tvärtom.
  const negativeHit = cache.get(`${key}:neg`, NEGATIVE_CACHE_TTL_MS);
  if (negativeHit) {
    return res.status(502).json({ error: negativeHit.error, negativeCached: true });
  }

  try {
    const result = await fetchOvernightFromOverpass(lat, lon, radiusKm, types);
    cache.set(key, result);
    res.json({ ...result, cached: false });
  } catch (err) {
    const message = "Kunde inte hämta övernattningsplatser just nu";
    console.error(
      "Fel vid anrop mot Overpass API (alla speglar misslyckades):",
      err.message,
      err.cause ? `(orsak: ${err.cause})` : ""
    );
    cache.set(`${key}:neg`, { error: message });
    res.status(502).json({ error: message });
  }
});
app.get("/charging", async (req, res) => {
  const lat = parseFloat(req.query.lat);
  const lon = parseFloat(req.query.lon);
  const radiusKm = parseFloat(req.query.radiusKm) || 40;

  if (Number.isNaN(lat) || Number.isNaN(lon)) {
    return res.status(400).json({ error: "lat och lon krävs som tal" });
  }

  const key = `charging:${roundCoord(lat)},${roundCoord(lon)}:${radiusKm}`;
  const cached = cache.get(key, OVERNIGHT_CACHE_TTL_MS);
  if (cached) {
    return res.json({ ...cached, cached: true });
  }
  // Negativ-cache, se /overnight ovan för resonemanget.
  const negativeHit = cache.get(`${key}:neg`, NEGATIVE_CACHE_TTL_MS);
  if (negativeHit) {
    return res.status(502).json({ error: negativeHit.error, negativeCached: true });
  }

  try {
    const result = await fetchChargingStationsFromOverpass(lat, lon, radiusKm);
    cache.set(key, result);
    res.json({ ...result, cached: false });
  } catch (err) {
    const message = "Kunde inte hämta laddplatser just nu";
    console.error(
      "Fel vid anrop mot Overpass API (laddplatser, alla speglar misslyckades):",
      err.message,
      err.cause ? `(orsak: ${err.cause})` : ""
    );
    cache.set(`${key}:neg`, { error: message });
    res.status(502).json({ error: message });
  }
});

/**
 * Laddplatser via NOBIL (Norges/Sveriges officiella laddstationsregister,
 * drivs av Enova/Energimyndigheten) — ett alternativ till /charging
 * (OpenStreetMap/Overpass) ovan, samma svarsform så Android-appen kan
 * använda vilken som helst av dem bakom samma ChargingStationProvider-
 * gränssnitt.
 *
 * NOBILs användarvillkor kräver att anrop går via en mellanliggande server
 * som cachar — precis det den här backenden redan gör för alla andra
 * endpoints, så ingen extra arkitektur behövs.
 */
app.get("/charging-nobil", async (req, res) => {
  const lat = parseFloat(req.query.lat);
  const lon = parseFloat(req.query.lon);
  const radiusKm = parseFloat(req.query.radiusKm) || 40;

  if (Number.isNaN(lat) || Number.isNaN(lon)) {
    return res.status(400).json({ error: "lat och lon krävs som tal" });
  }

  if (!NOBIL_API_KEY) {
    console.error("Saknar NOBIL_API_KEY i miljövariablerna — kan inte söka laddplatser via NOBIL.");
    return res.status(500).json({ error: "NOBIL-integrationen är inte konfigurerad på servern (saknar API-nyckel)" });
  }

  const key = `charging-nobil:${roundCoord(lat)},${roundCoord(lon)}:${radiusKm}`;
  const cached = cache.get(key, NOBIL_CACHE_TTL_MS);
  if (cached) {
    return res.json({ ...cached, cached: true });
  }

  try {
    const result = await fetchChargingStationsFromNobil(lat, lon, radiusKm);
    cache.set(key, result);
    res.json({ ...result, cached: false });
  } catch (err) {
    console.error(
      "Fel vid anrop mot NOBIL API (laddplatser):",
      err.message,
      err.cause ? `(orsak: ${err.cause})` : ""
    );
    res.status(502).json({ error: "Kunde inte hämta laddplatser från NOBIL just nu" });
  }
});

async function fetchAutocompleteFromGoogle(query) {
  // Places API (New) — Autocomplete. Kräver att "Places API (New)" är
  // aktiverat i samma Google Cloud-projekt som Routes API.
  // https://developers.google.com/maps/documentation/places/web-service/place-autocomplete
  const response = await fetch("https://places.googleapis.com/v1/places:autocomplete", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Goog-Api-Key": GOOGLE_API_KEY,
    },
    body: JSON.stringify({
      input: query,
      includedRegionCodes: ["se"],
      languageCode: "sv",
    }),
  });

  if (!response.ok) {
    throw new Error(`Google svarade ${response.status}: ${await response.text()}`);
  }

  const json = await response.json();
  const suggestions = (json.suggestions || [])
    .map((s) => s.placePrediction)
    .filter(Boolean)
    .map((p) => ({
      placeId: p.placeId,
      description: p.text?.text || "",
    }));

  return { suggestions };
}

async function fetchPlaceDetailsFromGoogle(placeId) {
  // Places API (New) — Place Details, bara fälten vi faktiskt behöver.
  // https://developers.google.com/maps/documentation/places/web-service/place-details
  const response = await fetch(`https://places.googleapis.com/v1/places/${placeId}`, {
    method: "GET",
    headers: {
      "X-Goog-Api-Key": GOOGLE_API_KEY,
      "X-Goog-FieldMask": "location",
    },
  });

  if (!response.ok) {
    throw new Error(`Google svarade ${response.status}: ${await response.text()}`);
  }

  const json = await response.json();
  if (!json.location) {
    throw new Error("Google returnerade ingen plats för det ID:t");
  }

  return { lat: json.location.latitude, lon: json.location.longitude };
}

/**
 * Kör en Overpass-fråga mot speglarna (OVERPASS_ENDPOINTS) i tur och ordning
 * tills en svarar. Hela rundan delar en tidsbudget (OVERPASS_TOTAL_BUDGET_MS):
 * varje spegel-anrop får som mest OVERPASS_ATTEMPT_TIMEOUT_MS, eller mindre om
 * det är mindre än så kvar av budgeten. När budgeten är slut slutar vi försöka
 * och kastar det senaste felet — anroparen (/overnight, /charging) skriver då
 * en negativ-cache-post och svarar 502.
 *
 * Sekventiellt, inte parallellt: speglarna är gratis och delade, och
 * findOvernight/ChargingStationsAlongRoute i appen fläktar redan ut flera
 * ruttpunkter samtidigt. Nästa spegel provas bara när den förra faktiskt fallit.
 * Backend-cachen (14 dagars TTL) gör att samma område ändå bara frågas en gång
 * totalt. Vid stor trafik: överväg att självhosta en Overpass-instans.
 *
 * @param label kort etikett för loggraderna, t.ex. "övernattning".
 */
async function runOverpassQuery(query, label) {
  const deadline = Date.now() + OVERPASS_TOTAL_BUDGET_MS;
  let lastError;

  for (const endpoint of OVERPASS_ENDPOINTS) {
    if (deadline - Date.now() < 1500) {
      console.error(
        `Overpass (${label}): tidsbudgeten (${OVERPASS_TOTAL_BUDGET_MS} ms) slut, hoppar över återstående speglar`
      );
      break;
    }
    try {
      return await postOverpassQuery(endpoint, query, deadline);
    } catch (err) {
      console.error(
        `Overpass-anrop (${label}) mot ${endpoint} misslyckades:`,
        err.message,
        err.cause ? `(orsak: ${err.cause})` : ""
      );
      lastError = err;
    }
  }

  throw lastError || new Error(`Overpass (${label}): ingen spegel svarade inom budgeten`);
}

/**
 * Frågar Overpass API (OpenStreetMaps sökgränssnitt) efter husbils-/
 * husvagnsplatser och campingplatser inom en radie runt en punkt.
 */
async function fetchOvernightFromOverpass(lat, lon, radiusKm, types) {
  const radiusMeters = Math.round(radiusKm * 1000);
  const nodeFilters = types
    .map((type) => `node["tourism"="${type}"](around:${radiusMeters},${lat},${lon});`)
    .join("\n      ");
  const query = `
    [out:json][timeout:20];
    (
      ${nodeFilters}
    );
    out body;
  `;

  const json = await runOverpassQuery(query, "övernattning");
  const rawSpots = (json.elements || []).map((el) => ({
    id: String(el.id),
    name: el.tags?.name || null,
    lat: el.lat,
    lon: el.lon,
    type: el.tags?.tourism || "unknown",
    hasFee: el.tags?.fee ? el.tags.fee === "yes" : null,
    allowsCaravan: parseOsmYesNo(el.tags?.caravans),
    allowsMotorhome: parseOsmYesNo(el.tags?.motorhome),
    allowsTent: parseOsmYesNo(el.tags?.tents),
    // OSM använder både "phone" och "contact:phone" för samma sak.
    phone: el.tags?.phone || el.tags?.["contact:phone"] || null,
    // Fågelvägen från den sökta ruttpunkten till platsen — INTE körsträcka.
    // En sjö eller omväg kan göra den verkliga omvägen betydligt längre än
    // detta tal, men det ger ändå en fingervisning om vad som är en rimlig
    // avvikelse från rutten.
    distanceFromRouteKm: Math.round(haversineKm(lat, lon, el.lat, el.lon) * 10) / 10,
  }));
  return { spots: dedupeAndRankOvernightSpots(rawSpots) };
}

/**
 * Ett enskilt POST-anrop mot en Overpass-spegel, med hård timeout via
 * AbortController. Timeouten kortas ned om det är mindre än
 * OVERPASS_ATTEMPT_TIMEOUT_MS kvar av den totala budgeten (`deadline`), så att
 * spegel-rundan aldrig överskrider OVERPASS_TOTAL_BUDGET_MS. Inget eget
 * omförsök här — de fyra speglarna i OVERPASS_ENDPOINTS är retryn.
 * Kastar vid timeout, nätfel eller icke-2xx-svar.
 */
async function postOverpassQuery(endpoint, query, deadline) {
  const budgetLeftMs = deadline - Date.now();
  const timeoutMs = Math.min(OVERPASS_ATTEMPT_TIMEOUT_MS, Math.max(budgetLeftMs, 0));
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(endpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "User-Agent": OVERPASS_USER_AGENT,
      },
      body: "data=" + encodeURIComponent(query),
      signal: controller.signal,
    });

    if (!response.ok) {
      throw new Error(`Overpass svarade ${response.status}: ${await response.text()}`);
    }

    return await response.json();
  } catch (err) {
    if (err.name === "AbortError") {
      throw new Error(`Overpass-anrop mot ${endpoint} tog längre än ${timeoutMs} ms (timeout)`);
    }
    throw err;
  } finally {
    clearTimeout(timer);
  }
}

/**
 * OpenStreetMap kartlägger ibland en hel campingplats som MÅNGA separata
 * punkter (en per tomt/pitch) istället för en punkt för hela anläggningen —
 * det ger annars en lista med tiotals namnlösa, nästan identiska träffar.
 * Här slås punkter som ligger väldigt nära varandra ihop till en enda
 * representant, med namngivna platser prioriterade före namnlösa.
 */
/**
 * Tolkar OSM:s ja/nej-liknande värden (t.ex. för `caravans`, `motorhome`,
 * `tents`-taggarna, som anger om husvagn/husbil/tält är välkomna).
 * Returnerar null om värdet saknas eller är oklart — OSM-communityn
 * flaggar själva att den här informationen ofta saknas helt, så "vet
 * inte" måste kunna skiljas från "nej" i appen.
 */
function parseOsmYesNo(value) {
  if (value === undefined || value === null) return null;
  const v = String(value).toLowerCase();
  if (["yes", "designated", "only"].includes(v)) return true;
  if (v === "no") return false;
  return null;
}

function dedupeAndRankOvernightSpots(spots) {
  const MIN_DISTANCE_KM = 1.5;
  const MAX_RESULTS = 15;

  const sorted = [...spots].sort((a, b) => (b.name ? 1 : 0) - (a.name ? 1 : 0));
  const kept = [];

  for (const spot of sorted) {
    const tooCloseToExisting = kept.some(
      (k) => haversineKm(k.lat, k.lon, spot.lat, spot.lon) < MIN_DISTANCE_KM
    );
    if (!tooCloseToExisting) {
      kept.push(spot);
    }
  }

  // Sortera de kvarvarande platserna efter avstånd från ruttpunkten (inte
  // efter namn) innan avklippning till MAX_RESULTS — annars kan en
  // namngiven plats långt bort tränga undan en oftast mer relevant,
  // närmare men namnlös plats.
  const byDistance = [...kept].sort((a, b) => a.distanceFromRouteKm - b.distanceFromRouteKm);

  return byDistance.slice(0, MAX_RESULTS);
}

function haversineKm(lat1, lon1, lat2, lon2) {
  const R = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

  async function fetchChargingStationsFromOverpass(lat, lon, radiusKm) {
    const radiusMeters = Math.round(radiusKm * 1000);
    const query = `
      [out:json][timeout:20];
      (
        node["amenity"="charging_station"](around:${radiusMeters},${lat},${lon});
      );
      out body;
    `;

    const json = await runOverpassQuery(query, "laddplatser");
    const rawStations = (json.elements || []).map((el) => ({
      id: String(el.id),
      name: el.tags?.name || null,
      lat: el.lat,
      lon: el.lon,
      operator: el.tags?.operator || el.tags?.network || null,
      capacity: el.tags?.capacity ? parseInt(el.tags.capacity, 10) || null : null,
      hasFee: el.tags?.fee ? el.tags.fee === "yes" : null,
      distanceFromRouteKm: Math.round(haversineKm(lat, lon, el.lat, el.lon) * 10) / 10,
    }));
    return { stations: dedupeAndRankChargingStations(rawStations) };
  }

  function dedupeAndRankChargingStations(stations) {
    const MIN_DISTANCE_KM = 1.0;
    const MAX_RESULTS = 15;

    const sorted = [...stations].sort((a, b) => (b.name ? 1 : 0) - (a.name ? 1 : 0));
    const kept = [];

    for (const station of sorted) {
      const tooCloseToExisting = kept.some(
        (k) => haversineKm(k.lat, k.lon, station.lat, station.lon) < MIN_DISTANCE_KM
      );
      if (!tooCloseToExisting) {
        kept.push(station);
      }
    }

    const byDistance = [...kept].sort((a, b) => a.distanceFromRouteKm - b.distanceFromRouteKm);
    return byDistance.slice(0, MAX_RESULTS);
  }

/**
 * Frågar NOBIL:s sök-API efter laddstationer inom en radie runt en punkt.
 * https://nobil.no/api/server/search.php — se NOBIL:s API-dokumentation för
 * fullständig fältlista. Svaret är en array med ETT objekt som har fältet
 * "chargerstations".
 */
async function fetchChargingStationsFromNobil(lat, lon, radiusKm) {
  const distanceMeters = Math.round(radiusKm * 1000);

  const body = new URLSearchParams({
    apikey: NOBIL_API_KEY,
    apiversion: "3",
    action: "search",
    type: "near",
    lat: String(lat),
    long: String(lon),
    distance: String(distanceMeters),
    limit: "20",
    format: "json",
  });

  const response = await fetch("https://nobil.no/api/server/search.php", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });

  if (!response.ok) {
    throw new Error(`NOBIL svarade ${response.status}: ${await response.text()}`);
  }

  const json = await response.json();
  // NOBIL:s svar är ett RAKT OBJEKT med "chargerstations" direkt på
  // toppnivån (t.ex. {"Provider":"NOBIL.no",...,"chargerstations":[...]})
  // — inte en array med objektet som första element. json?.[0]?.chargerstations
  // letade alltså alltid i fel struktur och gav [] även när NOBIL hade
  // riktiga träffar. json?.chargerstations är rätt väg; behåller
  // json?.[0]?.chargerstations som extra fallback ifall formatet växlar.
  const chargerStations = json?.chargerstations || json?.[0]?.chargerstations || [];

  if (NOBIL_DEBUG && chargerStations.length === 0) {
    // Fick 0 stationer redan från NOBIL, INNAN vår egen 3 km-filtrering körs.
    // Logga hela råsvaret (trunkerat) så vi ser om chargerstations verkligen
    // är tomt, eller om vår json?.[0]?.chargerstations-sökväg missar datan
    // (t.ex. om NOBIL svarar med ett annat toppnivå-skal nu än när koden
    // skrevs).
    console.log(
      "NOBIL_DEBUG: 0 stationer — råsvarets nycklar:",
      Array.isArray(json) ? `array[${json.length}], json[0] keys: ${Object.keys(json?.[0] || {}).join(", ")}` : typeof json
    );
    console.log("NOBIL_DEBUG: råsvar (trunkerat 1500 tecken):", JSON.stringify(json).slice(0, 1500));
  }

  // Diagnostik, avstängd som standard — sätt NOBIL_DEBUG=true i miljön för
  // att logga exakt vad NOBIL svarar innan avståndsgränsen (task: 3 km
  // respekteras inte) och avgiftsfältet (task: "Pris okänt") åtgärdas på
  // riktigt. Ingen av de två buggarna är bekräftad mot verklig NOBIL-data
  // än — bara mot NOBIL:s (delvis inaktuella) API-dokumentation — så
  // parsningslogiken nedan är oförändrad tills loggarna bekräftar orsaken.
  if (NOBIL_DEBUG) {
    console.log(
      `NOBIL_DEBUG: begärde distance=${distanceMeters}m (radiusKm=${radiusKm}) runt (${lat},${lon}), fick ${chargerStations.length} stationer`
    );
    if (chargerStations[0]?.attr?.st) {
      console.log("NOBIL_DEBUG: attr.st för första stationen:", JSON.stringify(chargerStations[0].attr.st));
    }
  }

  // Position kommer som strängen "(lat,lon)" och måste parsas ut.
  const positionPattern = /\(\s*(-?[\d.]+)\s*,\s*(-?[\d.]+)\s*\)/;

  const rawStations = chargerStations
    .map((entry) => {
      const csmd = entry.csmd || {};
      if (csmd.id === undefined || csmd.id === null) return null;

      const match = positionPattern.exec(csmd.Position || "");
      if (!match) return null;

      const stationLat = parseFloat(match[1]);
      const stationLon = parseFloat(match[2]);
      if (Number.isNaN(stationLat) || Number.isNaN(stationLon)) return null;

      // Attribut "7" = "Parking fee". NOBIL har varierat lite mellan
      // .attrval och .trans för Ja/Nej-värdet beroende på version, så vi
      // kollar båda.
      const feeAttr = entry.attr?.st?.["7"];
      let hasFee = null;
      if (feeAttr) {
        const value = feeAttr.trans ?? feeAttr.attrval;
        if (value === "Yes") hasFee = true;
        else if (value === "No") hasFee = false;
      }

      const distanceFromRouteKm = Math.round(haversineKm(lat, lon, stationLat, stationLon) * 10) / 10;

      if (NOBIL_DEBUG) {
        console.log(
          `NOBIL_DEBUG: station ${csmd.id} "${csmd.name}" distanceFromRouteKm=${distanceFromRouteKm} (radiusKm=${radiusKm}) hasFee=${hasFee} feeAttr=${JSON.stringify(feeAttr)}`
        );
      }

      return {
        id: String(csmd.id),
        name: csmd.name || null,
        lat: stationLat,
        lon: stationLon,
        operator: csmd.Owned_by || null,
        capacity: csmd.Number_charging_points ? parseInt(csmd.Number_charging_points, 10) || null : null,
        hasFee,
        distanceFromRouteKm,
      };
    })
    .filter(Boolean);

  return { stations: dedupeAndRankChargingStations(rawStations) };
}

async function fetchRouteFromGoogle(fromLat, fromLon, toLat, toLon) {
  // Routes API (efterträdaren till Directions API) — se
  // https://developers.google.com/maps/documentation/routes
  const response = await fetch("https://routes.googleapis.com/directions/v2:computeRoutes", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Goog-Api-Key": GOOGLE_API_KEY,
      // Fältmask krävs av Routes API och håller nere kostnad/svarsstorlek
      // genom att bara be om de fält vi faktiskt använder.
      "X-Goog-FieldMask": "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline",
    },
    body: JSON.stringify({
      origin: { location: { latLng: { latitude: fromLat, longitude: fromLon } } },
      destination: { location: { latLng: { latitude: toLat, longitude: toLon } } },
      travelMode: "DRIVE",
      routingPreference: "TRAFFIC_AWARE",
      languageCode: "sv-SE",
      units: "METRIC",
    }),
  });

  if (!response.ok) {
    throw new Error(`Google svarade ${response.status}: ${await response.text()}`);
  }

  const json = await response.json();
  const route = json.routes && json.routes[0];
  if (!route) {
    throw new Error("Google returnerade ingen rutt");
  }

  // duration kommer som t.ex. "5040s"
  const durationSeconds = parseInt(route.duration.replace("s", ""), 10);

  return {
    distanceKm: Math.round(route.distanceMeters / 1000),
    driveMinutes: durationSeconds / 60,
    encodedPolyline: route.polyline.encodedPolyline,
  };
}

app.get("/health", (_req, res) => res.json({ status: "ok", cacheSize: cache.size(), dbPath: cache.DB_PATH }));

// Städa bort utgångna cache-poster en gång per dygn, så databasfilen inte
// växer obegränsat med rutter ingen längre frågar efter.
setInterval(() => cache.pruneExpired(CACHE_TTL_MS), 1000 * 60 * 60 * 24);

app.listen(PORT, () => {
  console.log(`Avgångsplaneraren-backend igång på port ${PORT} (cache: ${cache.DB_PATH})`);
});
