# Kom igång — Avgångsplaneraren

Den här filen är din startpunkt. Den förklarar vad som finns, hur du får igång
projektet, och vad som återstår — så att du inte behöver leta i chattens
historik för att komma ihåg vad som byggdes.

## 1. Packa upp och öppna

1. Packa upp `AvgangsplanerarenAndroid.zip` var du vill ha projektet lokalt.
2. Öppna mappen i **Android Studio** (senaste stabila versionen).
3. Låt Android Studio synka Gradle (`Sync Project with Gradle Files`) —
   den skapar `gradlew`-filerna automatiskt vid första synken.
4. Tryck **Run ▶** på en emulator eller fysisk enhet.

Appen ska starta direkt: ett formulär där du väljer avreseplats/mål bland
åtta exempelstäder, räknar ut en avgångstid, och visar rastplatsförslag
(just nu tre exempelrastplatser — se avsnitt 3).

**Krav:** Android Studio, JDK 17, Android SDK API 34 (min SDK 26).

## 2. Vad är faktiskt klart

| Del | Status | Fil |
|---|---|---|
| Beräkningslogik (ankomst → avgångstid, raster var 2h, buffert) | ✅ Klar, testad | `domain/CalculateDeparture.kt` |
| Filtrering på bord/bänk | ✅ Klar, testad | `domain/RestStopFilter.kt` |
| Compose-UI (formulär + resultat) | ✅ Klar, körbar, med riktig datum/tid-väljare | `ui/planner/PlannerScreen.kt`, `ui/planner/ArrivalDateTimePicker.kt`, `ui/board/DepartureBoard.kt` |
| Rastplatsdatabas (Room) + sökning inom radie | ✅ Klar, testad | `data/trafikverket/*` |
| Seedning av databasen från JSON vid första start | ✅ Klar | `data/trafikverket/TrafikverketDataSeeder.kt` |
| Konverteringsskript (Trafikverket GeoPackage → appens JSON) | ✅ Klar, men **du måste stämma av fältnamnen** mot er faktiska fil | `tools/convert_rastplatser.py` |
| Ruttberäkning (sträcka/körtid) | ✅ Riktig integration mot Google Routes API klar, via eget backend | `backend/server.js`, `data/directions/GoogleRoutesRepository.kt` |
| Riktig rastplatsdata från Trafikverket | ⚠️ Just nu bara 3 påhittade exempelrastplatser | `assets/rastplatser.json` |
| Karta | ❌ Inte påbörjat | — |
| Notiser/påminnelser | ❌ Inte påbörjat | — |
| Sparade resor | ❌ Inte påbörjat | — |
| Hilt (dependency injection) | ❌ Biblioteket är med i `build.gradle.kts` men används inte än — `PlannerScreen` skapar objekten direkt | — |

## 3b. Nästa steg: koppla in backend på riktigt

Just nu pratar appen med `http://10.0.2.2:3000` (Android-emulatorns adress för
din dators localhost). Så här kör du det skarpt:

1. **Skaffa en Google API-nyckel:** Google Cloud Console → aktivera **Routes API**
   → skapa en API-nyckel → begränsa den till er servers IP-adress (inte till
   appen — nyckeln ligger bara på servern).
2. **Starta backend lokalt:**
   ```
   cd backend
   npm install
   cp .env.example .env   # klistra in din nyckel i GOOGLE_API_KEY
   npm start
   ```
3. **Testa:** `curl "http://localhost:3000/route?fromLat=57.78&fromLon=14.16&toLat=59.33&toLon=18.07"`
   ska ge dig `distanceKm`, `driveMinutes` och `encodedPolyline`.
4. **Kör appen** i en emulator — den ska nu hämta riktiga rutter. Om anropet
   misslyckas (backend inte igång) faller appen automatiskt tillbaka på den
   gamla platshållar-uppskattningen, så du märker det genom att avstånden
   plötsligt blir "för runda" (multiplar av vägfaktorn 1.25).
5. **När ni går skarpt:** deploya `backend/` till valfri Node-värd (Fly.io,
   Render, Cloud Run m.fl.), byt `AppConfig.BACKEND_BASE_URL` till er riktiga
   HTTPS-adress, och ta bort `usesCleartextTraffic="true"` ur
   `AndroidManifest.xml`.
6. **Cache-lagret** i `backend/cache.js` ligger nu i SQLite (`backend/data/cache.db`), inte i
   minnet — det överlever alltså att Node-processen startar om. Om ni kör på en PaaS gratisnivå
   (t.ex. Render Free) kan filsystemet ändå nollställas vid omstart/ny driftsättning om ni inte har
   en beständig disk kopplad — se kommentaren högst upp i `cache.js` för detaljer och när det
   spelar roll. En städrutin (`pruneExpired`) körs automatiskt en gång per dygn så databasfilen
   inte växer obegränsat.
7. **Deploy till Render:** `render.yaml` i projektets rot är en färdig "Blueprint" för det här.
   Pusha repot till GitHub/GitLab, gå till Render → New → Blueprint, peka på repot, fyll i
   `GOOGLE_API_KEY` när Render ber om det. Filen konfigurerar även en beständig disk för
   SQLite-cachen (kräver Render Starter-plan, ~$7/mån — se kommentarerna i `render.yaml` för hur
   ni kör på Free istället, med förbehållet att cachen då kan nollställas vid omstart).

## 3. Nästa steg: hämta riktig rastplatsdata från Trafikverket

Detta är sannolikt det första ni vill göra skarpt, eftersom appen just nu
bara visar tre påhittade exempelrastplatser.

1. **Skapa konto** på [Trafikverkets Datautbytesportal](https://data.trafikverket.se/)
   eller Lastkajen (gratis).
2. **Ladda ner** dataprodukten **"Rastplats"** ur NVDB, i GeoPackage-format.
3. **Installera verktyg:** `pip install geopandas fiona`
4. **Inspektera filen** för att se exakta kolumnnamn (de kan skilja sig från
   vad skriptet gissar):
   ```
   python tools/convert_rastplatser.py inspect rastplats.gpkg
   ```
5. **Justera** `FIELD_MAP` högst upp i `tools/convert_rastplatser.py` så den
   matchar kolumnnamnen ni ser i steg 4.
6. **Konvertera:**
   ```
   python tools/convert_rastplatser.py convert rastplats.gpkg rastplatser.json
   ```
7. **Ersätt** `app/src/main/assets/rastplatser.json` med den nya filen.
8. Avinstallera appen från emulatorn/enheten innan nästa körning (annars
   seedar den inte om, eftersom seedern bara fyller en tom databas) — eller
   höj `version` i `TrafikverketDatabase` och lägg till en migration.

Se `teknisk-plan-avgangsplaneraren.md`, avsnitt 3.2, för resonemanget bakom
varför Trafikverkets data valdes istället för Google Places/OpenStreetMap.

## 4. Ruttberäkning — nu klar (Google Routes API via eget backend)

`RouteEstimator.kt` finns kvar som fallback (ingen nätverksåtkomst, grov
uppskattning), men huvudvägen är nu `GoogleRoutesRepository`, som:
1. Anropar `backend/server.js` (ett litet Node/Express-API du kör själv).
2. Backend anropar i sin tur Googles **Routes API** (`computeRoutes`),
   håller API-nyckeln hemlig, och **cachar svaret** — samma sträcka (avrundad
   till ~1 km) hämtas bara från Google en gång, oavsett hur många användare
   som frågar efter den. Det här var den enskilt viktigaste kostnads-
   besparingen från kostnadsdiskussionen tidigare i chatten.
3. Appen avkodar den polyline Google skickar tillbaka (`PolylineDecoder`,
   verifierad mot Googles egen exempeldata i ett enhetstest) till punkter
   som resten av appen (rastplatssökningen) redan förstår.
4. Om backend inte svarar faller appen tillbaka på `RouteEstimator`
   automatiskt — appen kraschar aldrig bara för att nätet är nere.

Se avsnitt 3b ovan för hur du startar backend och kopplar in en riktig
API-nyckel.

## 5. Filkarta

```
AvgangsplanerarenAndroid/
 ├─ KOM_IGANG.md                     ← du är här
 ├─ README.md                        ← kortare teknisk README
 ├─ teknisk-plan-avgangsplaneraren.md (om den ligger bredvid zippen)
 ├─ tools/
 │   └─ convert_rastplatser.py       ← Trafikverket GeoPackage → appens JSON
 ├─ backend/
 │   ├─ server.js                    ← proxar Google Routes API
 │   ├─ cache.js                     ← SQLite-baserad, beständig ruttcache
 │   ├─ package.json
 │   └─ .env.example                 ← kopiera till .env, fyll i API-nyckel
 ├─ app/src/main/
 │   ├─ assets/rastplatser.json      ← rastplatsdata (just nu exempeldata)
 │   ├─ AndroidManifest.xml
 │   └─ java/com/avgangsplaneraren/app/
 │       ├─ MainActivity.kt
 │       ├─ domain/                  ← ren Kotlin, ingen Android-koppling
 │       │   ├─ Models.kt            (TripInput, RouteInfo, RestStop, DepartureResult)
 │       │   ├─ Coordinates.kt
 │       │   ├─ GeoMath.kt           (haversine-avstånd)
 │       │   ├─ RouteProvider.kt     (kontrakt för ruttdata)
 │       │   ├─ CalculateDeparture.kt (KÄRNAN i appen)
 │       │   └─ RestStopFilter.kt
 │       ├─ data/
 │       │   ├─ directions/
 │       │   │   ├─ AppConfig.kt             (backend-URL)
 │       │   │   ├─ RouteEstimator.kt        (fallback utan nätverk)
 │       │   │   ├─ BackendRoutesApi.kt      (Retrofit mot eget backend)
 │       │   │   ├─ GoogleRoutesRepository.kt (riktig implementation + fallback)
 │       │   │   └─ PolylineDecoder.kt
 │       │   └─ trafikverket/                  ← Room + seeding + repository
 │       │       ├─ RestAreaEntity.kt
 │       │       ├─ RestAreaDao.kt
 │       │       ├─ TrafikverketDatabase.kt
 │       │       ├─ TrafikverketDataSeeder.kt
 │       │       └─ TrafikverketRestStopRepository.kt
 │       └─ ui/
 │           ├─ planner/PlannerScreen.kt       ← formuläret, kopplar ihop allt
 │           └─ board/DepartureBoard.kt        ← resultatvyn
 └─ app/src/test/                    ← enhetstester för domain/ och data/
```

## 6. Rekommenderad ordning framåt

1. ~~Koppla in Google Directions/Routes API bakom ett litet backend~~ ✅ Klart.
2. ~~Deploykonfiguration för backend (render.yaml)~~ ✅ Klart — se avsnitt 3b.
3. Hämta och koppla in riktig Trafikverket-data (avsnitt 3 ovan) — störst
   återstående verklig nytta för minst jobb, eftersom hela pipelinen redan finns.
4. ~~Lägg till en riktig datum/tid-väljare i `PlannerScreen`~~ ✅ Klart —
   `ArrivalDateTimePicker.kt` (Material 3 Date-/TimePicker).
5. Karta (Google Maps SDK) som visar rutten och rastplatserna.
6. Notiser (`AlarmManager`) som påminner innan avgångstid.
7. Sparade resor (en till Room-tabell, separat från rastplatsdatabasen).
8. Städa upp med Hilt när antalet manuellt skapade objekt (`remember { ... }`
   i `PlannerScreen`) börjar kännas rörigt.

Säg till i chatten vilket steg du vill fortsätta med, så bygger jag vidare
på exakt den delen.
