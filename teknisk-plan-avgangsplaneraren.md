# Teknisk plan: Avgångsplaneraren

En app (iOS + Android) som räknar ut avgångstid utifrån avreseplats, slutmål och önskad ankomsttid, samt föreslår rastplatser — gärna med bord och bänkar.

---

## 1. Kärnfunktioner (MVP)

1. Ange avreseplats, slutmål och önskad ankomsttid.
2. Beräkna körsträcka och körtid för rutten.
3. Räkna ut nödvändig avgångstid (ankomsttid − körtid − rasttid − buffert).
4. Föreslå rastplatser längs rutten, med filter för bord/bänk/toalett.
5. Notis/påminnelse en tid innan beräknad avgång.

Senare version: sparade favoritresor, kalendersynk (hämta ankomsttid automatiskt från ett möte), realtidstrafik, väderhänsyn.

---

## 2. Rekommenderad teknikstack — nativt, Android först

Beslut: bygg nativt för bäst prestanda och plattformskänsla, med Android som första plattform.

- **Språk:** Kotlin
- **UI-ramverk:** Jetpack Compose (Googles moderna, deklarativa UI-toolkit — ersätter äldre XML-layouter, passar bra för en animerad "avgångstavla")
- **Arkitektur:** MVVM (Model–View–ViewModel) enligt Googles rekommenderade apparkitektur, med `ViewModel` + `StateFlow` för UI-tillstånd
- **iOS senare:** Swift/SwiftUI, med samma backend/API-lager återanvänt (se avsnitt 3) så bara UI-lagret behöver byggas om

### Kärnbibliotek (Android)

| Behov | Bibliotek |
|---|---|
| Nätverksanrop (Directions API, ev. eget backend-API för rastplatser) | Retrofit + OkHttp, `kotlinx.serialization` för JSON |
| Läsa geodata (GeoPackage från NVDB) vid databygge | GeoTools/GDAL (`ogr2ogr`) i ett fristående konverteringssteg, inte i appen |
| Asynkront/reaktivt flöde | Kotlin Coroutines + Flow |
| Karta | Google Maps SDK for Android (`com.google.android.gms:play-services-maps`) |
| Lokal lagring (sparade resor) | Room (SQLite-wrapper) eller DataStore för enklare preferenser |
| Notiser/påminnelser | `AlarmManager` (exakt tidsstyrd väckning) + `NotificationCompat`, ev. `WorkManager` för bakgrundsuppdatering av trafikläge |
| Dependency injection | Hilt |
| Datum/tid-beräkning | `kotlinx-datetime` eller `java.time` (redan i Android sedan API 26, annars via desugaring) |

### Projektstruktur (förslag)

```
app/
 ├─ ui/
 │   ├─ planner/        (formulär: avresa, mål, ankomsttid)
 │   ├─ board/           (avgångstavlan, animerad Compose-komponent)
 │   └─ route/           (karta + tidslinje med rastplatser)
 ├─ domain/
 │   ├─ CalculateDeparture.kt   (kärnlogiken: ankomst − körtid − rast − buffert)
 │   └─ RestStopFilter.kt
 ├─ data/
 │   ├─ directions/      (Retrofit-klient mot Google Directions API)
 │   ├─ trafikverket/    (läser rastplatsdata från Trafikverkets NVDB, se avsnitt 3.2)
 │   └─ local/           (Room-databas för sparade resor + bundlad rastplatsdata)
 └─ di/                  (Hilt-moduler)
```

Att lägga beräkningslogiken (`domain/`) helt fri från Android-specifika klasser gör den lätt att återanvända rakt av i den framtida iOS-versionen, bara omskriven till Swift.

---

## 3. Nyckelkomponenter

### 3.1 Ruttberäkning (avstånd + körtid)
Behövs för steg 2–3 ovan. Alternativ:

- **Google Directions API** — enkel, pålitlig, ger körtid inklusive trafikprognos. Kostar per anrop efter fri kvot.
- **Mapbox Directions API** — liknande, ofta billigare i stor skala.
- **OSRM (Open Source Routing Machine)**, självhostad — gratis, men kräver att ni driftar en egen server med kartdata.

Rekommendation för MVP: Google Directions API (snabbast att komma igång, bra dokumentation).

### 3.2 Rastplatser med bord/bänk — Trafikverkets NVDB-data

Istället för Google Places (som saknar bord/bänk-detaljnivå) används **Trafikverkets öppna vägdata**, som är gjord för precis det här syftet:

- **Källa:** Nationella Vägdatabasen (NVDB), dataprodukten **"Rastplats"**. Den visar var rastplatser finns längs det statliga vägnätet samt exakt vilken utrustning som finns — bord, bänkar, toaletter, soptunnor, handikappanpassning m.m., enligt Trafikverkets riktlinjer (VGU) för vad en rastplats ska hålla för minimistandard.
- **Åtkomst:** Data hämtas via **Trafikverkets Datautbytesportal / Öppet API**, eller som färdiga paket via **Lastkajen** (kräver ett gratis användarkonto för nedladdning). Filerna levereras i **GeoPackage**-format.
- **Licens:** Trafikverket publicerar sin öppna data under **Creative Commons CC0** — fri att använda, återanvända och distribuera, med källangivelse.

**Hur det används i appen:** NVDB-datan uppdateras sällan (rastplatser byggs/rivs inte ofta), så det är varken nödvändigt eller lämpligt att appen frågar Trafikverkets API live vid varje sökning. Rekommenderat flöde:

1. **Konverteringssteg (körs av er, inte i appen):** Ladda ner GeoPackage-filen för "Rastplats" via Datautbytesportalen/Lastkajen, filtrera ev. till relevant geografiskt område, och konvertera med `ogr2ogr` (del av GDAL) till antingen GeoJSON eller direkt till en SQLite-databas.
2. **Distribution:** Antingen (a) bunta den konverterade databasen som en tillgång i appen (Room kan öppna en förifylld SQLite-databas), vilket funkar offline och kräver ingen server, eller (b) lägg den på ert eget backend-API och servera "rastplatser nära denna rutt" som ett litet REST-anrop.
3. **Uppdatering:** Kör konverteringssteget t.ex. en gång i kvartalet (eller vid ny appversion) för att fånga upp nya/ändrade rastplatser — ingen realtidssynk behövs.
4. **Filtrering längs rutten:** När körtiden ger var på vägen en rast bör läggas (var 2:a timme, se avsnitt 4), sök i den lokala databasen efter rastplatser inom en buffert (t.ex. 10–15 km) från ruttens linje vid den punkten, och filtrera på `bord = true` och `bänk = true` om användaren valt det.

Detta ger exaktare och mer tillförlitlig bord/bänk-information än vad OpenStreetMap eller Google Places kan erbjuda för svenska statliga rastplatser, helt gratis, och utan beroende av externa API-kvoter i produktion.

*Begränsning att känna till:* NVDB:s "Rastplats"-produkt täcker det **statliga** vägnätet. Rastplatser vid kommunala eller enskilda vägar kan saknas — om det blir ett problem i praktiken går det att komplettera med OpenStreetMap/Overpass API som sekundär källa för de luckorna.

### 3.3 Backend
Ett litet backend-lager behövs för att:
- Dölja API-nycklar (aldrig lägg Google/Mapbox-nycklar direkt i appen)
- Cacha ruttsvar och rastplatsdata (snabbare + billigare)
- Köra beräkningslogiken för avgångstid centralt, så den blir lika på iOS och Android

Förslag: **Firebase Cloud Functions** eller **Supabase Edge Functions** — låg driftskostnad, skalar automatiskt, bra för en app i den här storleksklassen. Node.js/Express på t.ex. Fly.io/Render fungerar också om ni vill ha mer kontroll.

### 3.4 Databas (för sparade resor/preferenser)
- **Firebase Firestore** eller **Supabase (Postgres)** — båda har färdiga SDK:er för Flutter och React Native, inkl. inloggning.

### 3.5 Notiser
- Flutter: `flutter_local_notifications`
- React Native: `notifee` eller `react-native-push-notification`

Lokala notiser (schemalagda på enheten) räcker för påminnelsen om avgångstid — ingen serverpush behövs för det.

### 3.6 Karta & UI
- Flutter: `google_maps_flutter` eller `flutter_map` (OSM-baserad, gratis)
- React Native: `react-native-maps`

---

## 4. Beräkningslogik (kärnan i appen)

```
körtid          = rutt.duration (från Directions API)
antal_raster    = floor(körtid / 2h)          // en rast ca var 2:a timme
rasttid         = antal_raster × 20 min        // justerbart av användaren
buffert         = användarens marginal (t.ex. 10 min)

avgångstid = ankomsttid − körtid − rasttid − buffert
```

Rastplatser placeras ut vid ungefär var 2:e körtimme längs rutten (koordinat interpoleras längs ruttlinjen), filtrerat på bord/bänk om användaren valt det.

---

## 5. Publicering

| Plattform | Krav | Ungefärlig kostnad |
|---|---|---|
| **Android (Google Play)** — först ut | Google Play Console-konto, byggs i Android Studio | ~250 kr (engångsavgift) |
| **iOS (App Store)** — senare | Apple Developer Program-konto, Xcode för byggen, granskning via App Store Connect | ~1 190 kr/år |

Testning innan lansering: **TestFlight** (iOS) och **Play Console interna/slutna tester** (Android) — låt några personer testa ruttberäkningen och rastplatsförslagen i verkligheten innan publik release.

---

## 6. Föreslagen utvecklingsplan (Android)

1. **Vecka 1–2:** Skapa Android Studio-projekt (Kotlin + Compose), grundläggande UI (formulär + resultatvy), koppla in Google Directions API via Retrofit.
2. **Vecka 3–4:** Bygg beräkningslogiken för avgångstid i `domain/`. Ladda ner NVDB:s "Rastplats"-data från Trafikverket, konvertera till SQLite och koppla in som lokal källa för rastplatser med bord/bänk-filter.
3. **Vecka 5:** Karta (Google Maps SDK) med ruttlinje och rastplatsmarkörer, lokala notiser via `AlarmManager`.
4. **Vecka 6:** Sparade resor (Room), polish av UI och animationer på avgångstavlan.
5. **Vecka 7:** Intern testning i Play Console (sluten testning).
6. **Vecka 8:** Lansering på Google Play.
7. **Efter lansering:** Skriv om UI-lagret i SwiftUI för iOS, återanvänd samma backend/API-kontrakt.

Detta är ett rimligt tempo för 1 utvecklare på deltid, eller snabbare med ett litet team.

---

## 7. Ungefärliga löpande kostnader

- Google Directions API: gratis kvot, sedan ca 5 USD/1000 anrop (cacha aggressivt för att hålla nere kostnad)
- Trafikverkets NVDB-data: helt gratis (CC0), inga löpande API-kostnader eftersom datan bundlas/hostas av er själva och bara uppdateras periodiskt
- Firebase/Supabase: gratisnivå räcker långt för en app med måttlig användarbas
- Apple + Google utvecklarkonton: se tabell ovan

---

## 8. Nästa steg

Om ni vill gå vidare kan jag hjälpa till att:
- Skriva konverteringsskriptet som gör om Trafikverkets GeoPackage-data till en SQLite-databas att bunta med appen
- Implementera sökningen som hittar rastplatser inom en buffert längs ruttlinjen
- Rita en mer detaljerad datamodell för backend
