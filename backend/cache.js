/**
 * backend/cache.js
 *
 * Beständig cache för ruttsvar, baserad på SQLite (via better-sqlite3)
 * istället för en in-memory Map. Fördelen: cachen överlever att processen
 * startar om (t.ex. en deploy, eller en krasch) — en `Map` i minnet gjorde
 * inte det.
 *
 * VIKTIGT att känna till om ni kör på en PaaS gratisnivå (Render, Railway
 * m.fl.): filsystemet är ofta EPHEMERALT där också — dvs. filen
 * `data/cache.db` kan fortfarande försvinna vid omstart/ny driftsättning
 * om ni inte har en beständig disk kopplad (det brukar kräva en betald
 * plan, t.ex. Render "Persistent Disk" eller en Fly.io "Volume"). Utan en
 * sådan disk ger SQLite-varianten er ändå två saker på köpet jämfört med
 * `Map`:
 *   1. Cachen överlever normala omstarter inom samma körande instans
 *      (t.ex. om er Node-process kraschar och startas om av samma host
 *      utan att disken nollställs, vilket är det vanliga fallet).
 *   2. Den överlever helt säkert mellan lokala `npm start`-körningar under
 *      utveckling, vilket gör det mycket smidigare att testa.
 * Se KOM_IGANG.md för hur ni lägger till en riktig beständig disk när ni
 * går skarpt.
 */

const path = require("path");
const fs = require("fs");
const Database = require("better-sqlite3");

const DB_PATH = process.env.CACHE_DB_PATH || path.join(__dirname, "data", "cache.db");

// Se till att katalogen finns innan SQLite försöker skapa filen där.
fs.mkdirSync(path.dirname(DB_PATH), { recursive: true });

const db = new Database(DB_PATH);
db.pragma("journal_mode = WAL"); // bättre för samtidiga läsningar/skrivningar

db.exec(`
  CREATE TABLE IF NOT EXISTS route_cache (
    key TEXT PRIMARY KEY,
    data TEXT NOT NULL,
    timestamp INTEGER NOT NULL
  )
`);

const selectStmt = db.prepare("SELECT data, timestamp FROM route_cache WHERE key = ?");
const upsertStmt = db.prepare(`
  INSERT INTO route_cache (key, data, timestamp) VALUES (?, ?, ?)
  ON CONFLICT(key) DO UPDATE SET data = excluded.data, timestamp = excluded.timestamp
`);
const countStmt = db.prepare("SELECT COUNT(*) AS count FROM route_cache");
const deleteExpiredStmt = db.prepare("DELETE FROM route_cache WHERE timestamp < ?");

/**
 * @param key cache-nyckel, t.ex. från `cacheKey(...)` i server.js.
 * @param ttlMs hur gammal en cacheträff får vara innan den räknas som utgången.
 * @returns det cachade objektet, eller null om det saknas/gått ut.
 */
function get(key, ttlMs) {
  const row = selectStmt.get(key);
  if (!row) return null;
  if (Date.now() - row.timestamp >= ttlMs) return null;
  return JSON.parse(row.data);
}

function set(key, data) {
  upsertStmt.run(key, JSON.stringify(data), Date.now());
}

function size() {
  return countStmt.get().count;
}

/** Städar bort gamla poster. Kör t.ex. en gång per dygn, se server.js. */
function pruneExpired(ttlMs) {
  const cutoff = Date.now() - ttlMs;
  deleteExpiredStmt.run(cutoff);
}

module.exports = { get, set, size, pruneExpired, DB_PATH };
