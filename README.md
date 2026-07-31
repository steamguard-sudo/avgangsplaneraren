# Avgångsplaneraren — Android (Kotlin + Jetpack Compose)

> **Ny här?** Läs `KOM_IGANG.md` först — den ger en fullständig statusöversikt
> och en rekommenderad ordning för nästa steg.

## Vad som finns på plats

- **Domänlogik** (`domain/CalculateDeparture.kt`, `RestStopFilter.kt`, `Models.kt`) — den centrala
  beräkningen: ankomsttid → avgångstid, inklusive raster var 2:a timme och filtrering på
  bord/bänk. Helt fri från Android-specifika klasser, med tillhörande enhetstester.
- **UI** (`ui/planner/PlannerScreen.kt`, `ui/board/DepartureBoard.kt`) — ett formulär och en
  resultatvy i Jetpack Compose, kopplade till domänlogiken.
- **Rastplatsdata från Trafikverket** (`data/trafikverket/`) — en Room-databas som seedas från
  `assets/rastplatser.json` vid första appstart, plus ett repository som söker fram rastplatser
  nära en given punkt på rutten (grovfilter via bounding box, exakt avstånd via haversine).
  Just nu innehåller `rastplatser.json` bara tre **påhittade exempelrastplatser** — se
  `KOM_IGANG.md` avsnitt 3 för hur du hämtar och konverterar riktig data från Trafikverkets NVDB.
- **Konverteringsskript** (`tools/convert_rastplatser.py`) — gör om Trafikverkets GeoPackage-export
  till `rastplatser.json`. Kräver att du stämmer av kolumnnamnen mot er faktiska fil (se skriptets
  docstring).
- **Ruttberäkning via Google Routes API** (`data/directions/`) — `GoogleRoutesRepository` anropar
  ert eget backend (`backend/server.js`), som i sin tur anropar Google, döljer API-nyckeln och
  **cachar svaren mellan alla användare**. `RouteEstimator` finns kvar som automatisk fallback om
  backend inte svarar. Polylinen från Google avkodas med `PolylineDecoder` (verifierad mot Googles
  egen exempeldata i ett enhetstest).
- **Backend** (`backend/server.js`, `backend/cache.js`) — ett minimalt Node/Express-API. Cachen
  ligger i SQLite (via `better-sqlite3`), inte i minnet, så den överlever att processen startar om.
  Se avsnitt "Kom igång med backend" nedan.
- **Enhetstester** (`app/src/test/`) — verifierar beräkningslogiken och rastplatssökningen.

## Vad som INTE är byggt än

- Karta (Google Maps SDK).
- Notiser (`AlarmManager`) som påminner innan avgångstid.
- Room-databas för sparade resor (separat från rastplatsdatabasen).
- Riktig datum/tid-väljare i UI:t (just nu hårdkodad till "6 timmar från nu").
- Backend körs bara lokalt/utveckling just nu — se KOM_IGANG.md avsnitt 3b för deploy.

## Kom igång med backend

```
cd backend
npm install
cp .env.example .env   # fyll i GOOGLE_API_KEY
npm start
```

Appen pratar med `http://10.0.2.2:3000/` (Android-emulatorns adress för din dators localhost) —
se `data/directions/AppConfig.kt`. Fungerar direkt om du kör backend lokalt och appen i emulatorn.

**Om cachen i produktion:** SQLite-filen (`backend/data/cache.db`) överlever att Node-processen
startar om, men om ni kör på en PaaS gratisnivå kan hela filsystemet nollställas vid omstart om ni
inte har en beständig disk kopplad. Se kommentaren högst upp i `backend/cache.js` för detaljer.

## Så här öppnar du projektet

1. Ladda ner och packa upp zip-filen.
2. Öppna mappen i **Android Studio** (senaste stabila versionen, t.ex. Koala/Ladybug eller senare).
3. Android Studio skapar automatiskt `gradlew`/Gradle-wrapper-filerna åt dig vid första
   synkroniseringen (`Sync Project with Gradle Files`) — de är medvetet inte inkluderade i zippen.
4. Kör appen på en emulator eller fysisk enhet (`Run ▶`).

## Krav

- Android Studio (senaste stabila)
- JDK 17
- Android SDK, API 34 (min SDK 26)
- Python 3 + `pip install geopandas fiona` (endast för `tools/convert_rastplatser.py`, inte för att köra appen)

## Struktur

```
app/src/main/java/com/avgangsplaneraren/app/
 ├─ MainActivity.kt
 ├─ domain/
 │   ├─ Models.kt
 │   ├─ Coordinates.kt
 │   ├─ GeoMath.kt
 │   ├─ CalculateDeparture.kt
 │   └─ RestStopFilter.kt
 ├─ data/
 │   ├─ directions/RouteEstimator.kt   (platshållare — byt mot Directions API)
 │   └─ trafikverket/                   (Room-databas + seeding + repository för rastplatser)
 └─ ui/
     ├─ planner/PlannerScreen.kt
     ├─ board/DepartureBoard.kt
     └─ theme/                          (tom — lägg gärna in Material 3-tema här)
app/src/main/assets/rastplatser.json     (rastplatsdata — just nu exempeldata)
tools/convert_rastplatser.py             (Trafikverket GeoPackage → rastplatser.json)
```

Se `teknisk-plan-avgangsplaneraren.md` för den fullständiga planen, inklusive rekommenderade
API:er, publiceringssteg och tidsplan.
