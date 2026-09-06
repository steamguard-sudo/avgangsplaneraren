# Bygga projektet (assembleDebug m.fl.)

`gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar` ligger numera
i repot (tidigare gitignorade). Vanligt bygge fungerar alltså direkt:

```bash
cd "/d/Dokument/avgangsplaneraren/AvgangsplanerarenAndroid"
./gradlew assembleDebug --console=plain
```

Kör via **Bash-verktyget (Git Bash)**, inte PowerShell — PowerShell kan inte
köra det extensionslösa unix-launcher-scriptet (`& "...gradlew"` respektive
`bin/gradle` misslyckas tyst utan felmeddelande). `gradlew.bat` fungerar i
PowerShell men Git Bash är standard här.

## ⚠️ JDK 26 kan inte köra Gradle-daemonen

Systemets standard-`java` (på PATH, `C:\Program Files\Common Files\Oracle\Java\javapath`)
är **JDK 26.0.2.1 (EA)**. Gradle 8.7 kan inte köra daemonen på den — den
buntade Kotlin-DSL-scriptkompilatorn (Kotlin 1.9.22 / IntelliJ `JavaVersion.parse`)
klarar inte den 4-delade versionssträngen och bygget dör direkt med ett
kryptiskt:

```
* What went wrong:
26.0.2.1
```
```
java.lang.IllegalArgumentException: 26.0.2.1
    at org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse(JavaVersion.java:305)
```

Det sker i daemon-JVM:en **innan** någon build-logik körs, så Java-toolchainen
i `app/build.gradle.kts` (`kotlin { jvmToolchain(21) }`) hjälper inte — den
styr bara kompilering/test av projektets källkod, inte daemon-JVM:en.

**Lösning (redan på plats på den här maskinen):** `~/.gradle/gradle.properties`
(dvs. `C:\Users\Z97X\.gradle\gradle.properties`, ej i repot) pekar
daemon-JVM:en till en JDK 21:

```properties
org.gradle.java.home=C:/Users/Z97X/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2
```

Med det fungerar `./gradlew` även om PATH-`java` är JDK 26. Alternativt: sätt
`export JAVA_HOME="/c/Program Files/Android/Android Studio1/jbr"` (JBR 21) före
anropet.

Android Studio påverkas inte av PATH-`java` — den använder sin buntade JBR
(21.0.10) och följer dessutom `org.gradle.java.home` ovan
(`gradleJvm = #GRADLE_LOCAL_JAVA_HOME` i `.idea/gradle.xml`).

## Viktiga detaljer

- **JBR (buntad JDK) ligger under `Android Studio1`, inte `Android Studio`.**
  Det finns två installationer på maskinen
  (`C:\Program Files\Android\Android Studio` och `...\Android Studio1`);
  bara `Android Studio1\jbr\bin\java.exe` existerar/fungerar. Verifiera vid
  behov med `Test-Path` i PowerShell innan du antar sökvägen.
- Java-version i JBR: OpenJDK 21.0.10.
- Gradle-version bestäms av `gradle/wrapper/gradle-wrapper.properties`
  (för närvarande `gradle-8.7-bin.zip`). Den cachade distributionen ligger
  under `C:\Users\Z97X\.gradle\wrapper\dists\gradle-8.7-bin\<hash>\gradle-8.7\`.
- Fallback om `./gradlew` strular: kör den cachade distributionens launcher
  direkt (leta upp `<hash>` om cachen byggts om):
  ```bash
  export JAVA_HOME="/c/Program Files/Android/Android Studio1/jbr"
  GRADLE=$(find "/c/Users/Z97X/.gradle/wrapper/dists/gradle-8.7-bin" -maxdepth 2 -name gradle -type f | head -1)
  "$GRADLE" assembleDebug --console=plain
  ```
- Java-toolchain: `kotlin { jvmToolchain(21) }` i `app/build.gradle.kts`, med
  Foojay-resolvern i `settings.gradle.kts` som skyddsnät för auto-nedladdning
  av JDK 21 om ingen hittas (på CI förses den av `setup-java`, se nedan).
  Bytekodnivån är fortfarande 17 (`compileOptions` / `kotlinOptions`).
- **Radslut:** `.gitattributes` har `* text=auto eol=lf`, så all text checkas
  ut med LF oberoende av lokal `core.autocrlf` (`*.bat` behåller crlf). Editor-
  verktyg som skriver LF ska alltså inte längre ge "LF will be replaced by
  CRLF"-varningar vid commit.

## Backend (`backend/`)

Litet Node/Express-backend (Render, `render.yaml`, `rootDir: backend`,
`autoDeploy: true`) som proxar/cachar Google Routes + Places, samt slår mot
Overpass (OSM) för `/overnight` och `/charging`. Cache i SQLite via
`better-sqlite3` (`cache.js`).

- **Ingen testharness** — `package.json` har bara `start`. Verifiera
  ändringar med `node --check backend/server.js` (CI:s `backend`-jobb kör
  samma sak, se nedan). `better-sqlite3` är en native modul; `npm install`
  i `backend/` kräver byggkedja (särskilt på ny Node-major), så den körs
  sällan lokalt här.
- **Overpass-robusthet (fix E):** `runOverpassQuery()` kör
  `OVERPASS_ENDPOINTS` (4 speglar: overpass-api.de, kumi.systems,
  private.coffee, osm.ch) sekventiellt under en **delad väggklockebudget**
  `OVERPASS_TOTAL_BUDGET_MS` (default 45000). Varje spegel får
  `OVERPASS_ATTEMPT_TIMEOUT_MS` (default 20000) eller resten av budgeten,
  via `AbortController`. Budgeten **måste** hållas under appens OkHttp-
  timeouter i `BackendHttp.kt` (`readTimeout` 60 s / `callTimeout` 75 s),
  annars ger appen upp först. Inget eget omförsök per spegel — de fyra
  speglarna *är* retryn. User-Agent byggs från `OVERPASS_CONTACT`
  (default repo-URL; sätts i `render.yaml` / `.env.example`).
- **Negativ-cache (fix F):** när alla speglar dör skriver `/overnight` och
  `/charging` en `:neg`-post (`NEGATIVE_CACHE_TTL_MS`, 10 min). Nästa anrop
  för samma område inom fönstret → snabbt **502** utan upstream-anrop.
  Medvetet 502 och inte tom 200: appen tolkar 502 som `hadFailure` och
  visar `*_failed_all/partial`; ett tomt 200-svar hade sett ut som "inga
  träffar". `:neg`-nyckeln är skild från den positiva cache-nyckeln, som
  läses först.

## CI (GitHub Actions)

`.github/workflows/android.yml` kör på **push och PR mot `main`**, med
`paths-ignore: ['**.md']` — rena dokändringar kör inte workflowen alls (en
push som rör *både* `.md` och kod kör den ändå). `paths-ignore` är på
workflow-nivå, så en backend-only-push kör fortfarande `build`-jobbet.
Två jobb:

**`build`** (Android, `ubuntu-latest`):

1. `actions/setup-java@v4` med Temurin **JDK 21** (kör Gradle-daemonen — se
   JDK 26-fällan ovan; matchar även `jvmToolchain(21)`).
2. `gradle/actions/setup-gradle@v4` för dependency-/build-cache mellan körningar.
3. `./gradlew testDebugUnitTest --console=plain --stacktrace`
4. `./gradlew assembleDebug --console=plain --stacktrace`
5. Laddar upp `app-debug.apk` som artefakt (`app-debug`), och — bara vid fel —
   testrapporten under `app/build/reports/tests/testDebugUnitTest`.

**`backend`** (`ubuntu-latest`): `actions/setup-node@v4` med **Node 22**
(matchar prod: `render.yaml` / `backend/.node-version`) →
`node --check backend/server.js` + `cache.js`. Fångar syntaxfel; `backend/`
har ingen riktig testharness.

`build`-runnern är `ubuntu-latest`, så `org.gradle.java.home` / JBR-sökvägarna
ovan gäller bara lokalt; på CI räcker `setup-java`. Bygget behöver ingen
`MAPS_API_KEY` — `manifestPlaceholders` defaultar till tom sträng när
`local.properties` saknas.

Status/loggar: `https://github.com/steamguard-sudo/avgangsplaneraren/actions`.

## Verifierat

- `./gradlew clean :app:assembleDebug :app:testDebugUnitTest` med JBR 21 →
  BUILD SUCCESSFUL, `app-debug.apk` byggd, enhetstester gröna (2026-09-05).
- `./gradlew testDebugUnitTest` med PATH-`java` = JDK 26 och utan `JAVA_HOME`
  (dvs. enbart via `org.gradle.java.home` i `~/.gradle/gradle.properties`) →
  BUILD SUCCESSFUL (2026-09-05).
- GitHub Actions-workflowen (`android.yml`) grön på `main` — första körningen
  samt fix G (`testDebugUnitTest` + `assembleDebug`), 2026-09-06.
- Fix J (rastbrytpunkter efter kumulativ båglängd) — `./gradlew
  testDebugUnitTest` 11/11 grönt (nytt test `brytpunkt placeras efter faktisk
  baglangd, inte efter punktindex`), `assembleDebug` OK, CI grön på `main`
  (commit `97781cd`), 2026-09-06.
- Fix E + F (backend Overpass-robusthet + negativ-cache, commits `0090d82`
  / `14fd592`) — `node --check backend/server.js` OK. Ingen körande
  backend-test (ingen harness, native deps ej installerade); logiken
  granskad mot `cache.js`-kontraktet. Skarp verifiering sker vid nästa
  Render-deploy. 2026-09-06.
- Städ 1–3 (commits `0dc365a` / `65fbdc6` / `1056d25`): `.gitattributes`
  `eol=lf` (`git add --renormalize .` gav noll filändringar — blobbarna var
  redan LF); raderat döda `res/values/values-{en,de}/strings.xml`
  (felnästlade, ignorerade av aapt); CI fick `paths-ignore: ['**.md']` +
  nytt `backend`-jobb. Båda CI-jobben (`build` + `backend`) gröna på `main`
  (commit `1056d25`), 2026-09-06.
