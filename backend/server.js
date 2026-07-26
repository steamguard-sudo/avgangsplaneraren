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
