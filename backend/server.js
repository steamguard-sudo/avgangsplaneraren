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
const PORT = process.env.PORT || 3000;

if (!GOOGLE_API_KEY) {
  console.error("Saknar GOOGLE_API_KEY i miljövariablerna. Se .env.example.");
  process.exit(1);
}

const CACHE_TTL_MS = 1000 * 60 * 60 * 24 * 30; // 30 dagar — vägar ändras sällan
const PLACES_CACHE_TTL_MS = 1000 * 60 * 60 * 24 * 90; // 90 dagar — adresser/orter ändras nästan aldrig
const OVERNIGHT_CACHE_TTL_MS = 1000 * 60 * 60 * 24 * 14; // 14 dagar — OSM-data uppdateras oftare av communityn

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
app.get("/overnight", async (req, res) => {
  const lat = parseFloat(req.query.lat);
  const lon = parseFloat(req.query.lon);
  const radiusKm = parseFloat(req.query.radiusKm) || 20;

  if (Number.isNaN(lat) || Number.isNaN(lon)) {
    return res.status(400).json({ error: "lat och lon krävs som tal" });
  }

  const key = `overnight:${roundCoord(lat)},${roundCoord(lon)}:${radiusKm}`;
  const cached = cache.get(key, OVERNIGHT_CACHE_TTL_MS);
  if (cached) {
    return res.json({ ...cached, cached: true });
  }

  try {
    const result = await fetchOvernightFromOverpass(lat, lon, radiusKm);
    cache.set(key, result);
    res.json({ ...result, cached: false });
  } catch (err) {
    console.error("Fel vid anrop mot Overpass API:", err.message);
    res.status(502).json({ error: "Kunde inte hämta övernattningsplatser just nu" });
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
 * Frågar Overpass API (OpenStreetMaps sökgränssnitt) efter husbils-/
 * husvagnsplatser och campingplatser inom en radie runt en punkt.
 *
 * OBS om artighet mot Overpass: den publika instansen (overpass-api.de)
 * är gratis men delad av alla som använder den. Backend-cachen ovan
 * (14 dagars TTL) gör att samma område bara frågas en gång totalt, oavsett
 * hur många av era användare som råkar passera samma sträcka — det är
 * precis den sortens artighet Overpass ber om. Om trafiken blir stor,
 * överväg att självhosta en Overpass-instans eller byta till en betald
 * leverantör (t.ex. Geoapify) istället för den delade publika instansen.
 */
async function fetchOvernightFromOverpass(lat, lon, radiusKm) {
  const radiusMeters = Math.round(radiusKm * 1000);
  const query = `
    [out:json][timeout:25];
    (
      node["tourism"="caravan_site"](around:${radiusMeters},${lat},${lon});
      node["tourism"="camp_site"](around:${radiusMeters},${lat},${lon});
    );
    out body;
  `;

  const response = await fetch("https://overpass-api.de/api/interpreter", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      // Overpass usage policy ber om en beskrivande User-Agent så de kan
      // kontakta er vid problem, istället för att bara blockera er IP.
      "User-Agent": "Avgangsplaneraren/1.0 (kontakt: fyll-i-din-epost-har)",
    },
    body: "data=" + encodeURIComponent(query),
  });

  if (!response.ok) {
    throw new Error(`Overpass svarade ${response.status}: ${await response.text()}`);
  }

  const json = await response.json();
  const rawSpots = (json.elements || []).map((el) => ({
    id: String(el.id),
    name: el.tags?.name || null,
    lat: el.lat,
    lon: el.lon,
    type: el.tags?.tourism || "unknown",
    hasFee: el.tags?.fee ? el.tags.fee === "yes" : null,
  }));

  return { spots: dedupeAndRankOvernightSpots(rawSpots) };
}

/**
 * OpenStreetMap kartlägger ibland en hel campingplats som MÅNGA separata
 * punkter (en per tomt/pitch) istället för en punkt för hela anläggningen —
 * det ger annars en lista med tiotals namnlösa, nästan identiska träffar.
 * Här slås punkter som ligger väldigt nära varandra ihop till en enda
 * representant, med namngivna platser prioriterade före namnlösa.
 */
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

  return kept.slice(0, MAX_RESULTS);
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
