# Uppgraderingsplan: AGP 8.9 + Gradle 8.11 (mål: compileSdk 36 för Play)

Status: **förslag, inget genomfört.** Skrivet 2026-09-06. Körs som en egen
session (beslut i chatten).

## 1. Mål och nuläge

Google Play kräver API 36 (Android 16) för nya uppdateringar sedan
2026-08-31. Appen ligger på 34. compileSdk 36 kräver AGP ≥ 8.9, som i sin
tur kräver Gradle ≥ 8.11.1.

| Komponent | Nu | Mål |
|---|---|---|
| AGP (`com.android.application`) | 8.5.0 | 8.9.1 |
| Gradle (wrapper) | 8.7 | 8.11.1 |
| Kotlin | 1.9.24 | **1.9.24 (oförändrat)** |
| Compose compiler ext | 1.5.14 | **1.5.14 (oförändrat)** |
| Compose BOM | 2024.06.00 | ev. 2024.09/12 (frivilligt) |
| compileSdk / targetSdk | 34 / 34 | 36 / 36 (**egen fas 3**) |
| minSdk | 26 | 26 |
| Bytekod / toolchain | 17 / JDK 21 | 17 / JDK 21 |
| Daemon-JVM | JDK 21 via `org.gradle.java.home` | oförändrat, se §6 |

Grön baslinje att bevara: `testDebugUnitTest` 11/11, `assembleDebug`,
`bundleRelease` (signerad AAB), CI `build` + `backend`.

## 2. Kompatibilitet

- **AGP 8.9.x ↔ Gradle:** minst **8.11.1**, testad upp till 8.13. Gradle
  8.11 duger.
- **AGP 8.9 ↔ JDK att köra Gradle:** minst JDK 17. JDK 21 stöds. JDK 24–26
  stöds inte officiellt för att *köra* Gradle 8.11 (Gradle 8.11 stödjer upp
  till JDK 23) — behåll JDK 21-pinnen, se §6.
- **AGP 8.9 ↔ Kotlin:** tvingar **inte** Kotlin 2. Kotlin 1.9.24 fungerar.
- **Compose:** med Kotlin 1.9.24 används fortfarande den fristående Compose-
  compilern via `composeOptions.kotlinCompilerExtensionVersion = "1.5.14"`.
  Det fungerar på AGP 8.9. **Bumpa inte Kotlin i samma steg** — Kotlin 2.0+
  flyttar Compose-compilern till `org.jetbrains.kotlin.plugin.compose` och
  tar bort `kotlinCompilerExtensionVersion`; det är en separat migrering.
- **kapt / Room:** kapt stöds på Kotlin 1.9.24 + AGP 8.9. Room 2.6.1
  oförändrad. (KSP-migrering är frivillig och separat.)
- **Övriga beroenden** (Retrofit 2.11, billing 7.1.1, play-services-maps
  19.0.0, kotlinx-serialization 1.6.3, coroutines 1.8.1): inga kända
  AGP 8.9-problem, rör dem inte.
- **Backend (`backend/`):** helt opåverkat.

## 3. SDK-plattform 36 — läge (verifierat 2026-09-06)

Maskinens SDK (`C:\Users\Z97X\AppData\Local\Android\Sdk`) har **installerat**:

    platforms/android-34      (rev 3.0.0, ok)
    platforms/android-36.1    (rev 1.0.0) — DELVIS TRASIG, saknar source.properties
    platforms/android-37.1    (rev 1.0.0)
    build-tools/34.0.0, 36.0.0

`cmdline-tools` är rev **23.0** (nya `android`-CLI:t; `sdkmanager` deprecerat
men fungerar). `sdkmanager --list` → "Available packages" visar att **plain
`platforms;android-36` rev 2.0.0 går att hämta**:

    platforms/android-36        2.0.0   ← plain, nedladdningsbar
    platforms/android-36.1      1.0.0   ← minor (den trasiga installerade)
    platforms/android-35        2.0.0
    build-tools/36.0.0                  ← redan installerad

**Slutsats:** minor-SDK-problemet är inte en blockerare — plain `android-36`
finns att hämta, så AGP 8.9 (som letar efter `platforms/android-36`) fungerar.
Kvarstående punkt: den installerade `android-36.1` saknar `source.properties`
(android.jar finns) — den är delvis trasig. Vi behöver den inte för väg A;
installera bara plain `android-36`.

## 4. Rekommendation

**Väg A** — exakt som efterfrågat: **AGP 8.9.1 + Gradle 8.11.1**, compileSdk
mot **plain `android-36`** (hämtas i fas 0, §3 visar att den är tillgänglig).
Kotlin 1.9.24 och Compose-compiler 1.5.14 orörda.

Resten av planen (§5–§9) är skriven för väg A.

### Väg B (reserv) — AGP 8.11.1 + Gradle 8.13

Bara om något i väg A visar sig kräva minor-SDK-hantering (t.ex. om AGP 8.9
ändå inte accepterar `platforms/android-36` rev 2.0.0). Då: AGP 8.11.1,
Gradle 8.13, `compileSdk = 36` (+ ev. `compileSdkMinor = 1`) mot den
redan installerade `android-36.1` (efter reparation). Allt annat identiskt.

## 5. Steg för steg (branch `chore/agp-gradle-uppgradering`)

### Fas 0 — prep (ingen kodändring)

- [ ] Hämta plain SDK-plattform 36:
      `sdkmanager "platforms;android-36"` (rev 2.0.0, bekräftat tillgänglig).
      Verifiera att `platforms/android-36/source.properties` finns.
      Den trasiga `android-36.1` behöver inte röras för väg A.
- [ ] Kör och anteckna grön baslinje:
      `./gradlew clean testDebugUnitTest assembleDebug bundleRelease`.
- [ ] Skapa branchen.

### Fas 1 — Gradle wrapper 8.7 → 8.11.1 (commit 1)

    ./gradlew wrapper --gradle-version 8.11.1 --distribution-type bin
    ./gradlew wrapper --gradle-version 8.11.1 --distribution-type bin   # kör två ggr

Ändrade filer: `gradle/wrapper/gradle-wrapper.properties`,
`gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`.

- [ ] `./gradlew --version` → Gradle 8.11.1, JVM 21.
- [ ] `./gradlew assembleDebug` — går sannolikt med **varning** om att
      AGP 8.5.0 inte är testad mot Gradle 8.11.1. Grönt-med-varning är ok
      här; fas 2 tar bort varningen. (Om det blir hårt fel: slå ihop
      fas 1+2 till en commit.)
- [ ] Kontrollera att `gradlew` blev **LF** (`.gitattributes` styr det) och
      `gradlew.bat` **CRLF**.

### Fas 2 — AGP 8.5.0 → 8.9.1 (commit 2)

I `build.gradle.kts` (roten):

    id("com.android.application") version "8.9.1" apply false

Kotlin, serialization-plugin, Compose-compiler: **orörda.**

- [ ] `./gradlew clean testDebugUnitTest assembleDebug bundleRelease lintDebug`
- [ ] Bevaka:
      - kapt/Room-stub-generering (kör fortfarande på K1.9.24)
      - Compose-compiler-kompatibilitetsvarning (1.5.14 mot AGP 8.9)
      - nya Lint-checks (`lintDebug` kan flagga nytt; blockerar bara om
        `abortOnError` + faktiska errors — inget `lint {}`-block finns idag)
      - manifest-merger, R8/dex (minify av → låg risk)
      - `bundleRelease` signerar fortfarande med release-nyckeln
- [ ] **Manuell Compose-rök:** bygg och kör appen (`/run` eller Android
      Studio), klicka igenom planerarflödet — Compose runtime/compiler-
      samspel testas inte av enhetstesterna.
- [ ] Grön här = verktygskedjan uppe. compileSdk är fortfarande 34.

### Fas 3 — compileSdk / targetSdk 36 (commit 3, kan vara egen PR)

I `app/build.gradle.kts`:

    compileSdk = 36
    // ev. compileSdkMinor = 1   // om AGP kräver explicit minor
    defaultConfig { targetSdk = 36; versionCode = 5 }

- [ ] CI: lägg till SDK-plattform 36 på runnern (se fas 4).
- [ ] `./gradlew clean testDebugUnitTest assembleDebug bundleRelease lintRelease`
- [ ] **Android 16-beteendeändringar att gå igenom** (targetSdk 36):
      - **Edge-to-edge tvingas på** — `MainActivity` / Compose-scaffold måste
        hantera window insets korrekt (kontrollera `enableEdgeToEdge()` och
        att inga UI-element hamnar under status-/navbar).
      - Predictive back, `OnBackInvokedCallback`.
      - Eventuella nya runtime-permissions / foreground service-krav
        (appen har `DepartureAlarmReceiver` / `NotificationScheduler` — kolla
        notifikations-/alarm-behörigheter mot API 36).
      - `MediaStore`, storlek på `BroadcastReceiver`-ändringar m.m. — gå
        igenom Googles "Behavior changes: apps targeting Android 16".
- [ ] **Manuell rök på API 36-emulator** (skapa en om den saknas).
- [ ] Uppdatera `versionCode` → 5, `versionName` vid behov.

### Fas 4 — CI + docs (commit 4)

- [ ] `.github/workflows/android.yml`:
      - `gradle/actions/setup-gradle@v4` läser wrappern → får Gradle 8.11.1
        automatiskt, ingen ändring behövs.
      - Lägg till SDK-plattform 36 om `ubuntu-latest`-imagen saknar den:
        antingen `android-actions/setup-android@v3` + `sdkmanager
        "platforms;android-36"`, eller ett explicit steg. Verifiera först om
        runnern redan har den (`ANDROID_HOME`).
      - `backend`-jobbet: oförändrat.
- [ ] `CLAUDE.md`:
      - Gradle 8.7 → 8.11.1, AGP 8.5.0 → 8.9.1 i "Viktiga detaljer" + cachead
        distributionssökväg.
      - JDK 26-avsnittet: **testa om pinnen fortfarande behövs** med
        Gradle 8.11.1 (nyare buntad Kotlin-DSL-kompilator kan ha fixat
        `JavaVersion.parse`-buggen). Uppdatera avsnittet med utfallet —
        behåll pinnen om osäkert.
      - Release-signering-sektionen: ta bort "riktar sig mot API 34"-noten.
- [ ] Uppdatera den här filens status till "genomförd, se commits X–Y".

## 6. JDK 26-daemonen

CLAUDE.md dokumenterar att Gradle 8.7:s buntade Kotlin-DSL-kompilator
kraschar på PATH-`java` = JDK 26 (`JavaVersion.parse("26.0.2.1")`).
Gradle 8.11.1 buntar en nyare kompilator där den 4-delade versionssträngen
troligen hanteras — men JDK 26 EA stöds ändå inte officiellt för att *köra*
Gradle 8.11.1 (Gradle 8.11 stödjer upp till JDK 23).

**Plan:** behåll `org.gradle.java.home` → JDK 21 i
`~/.gradle/gradle.properties` genom hela uppgraderingen. I fas 4, testa
uttryckligen `./gradlew testDebugUnitTest` **utan** pinnen (PATH-`java` =
JDK 26) och dokumentera om den fortfarande behövs. Pinnen är ofarlig att
behålla oavsett.

## 7. Riskregister

| Risk | Sannolikhet | Åtgärd |
|---|---|---|
| AGP 8.9 accepterar inte `platforms/android-36` rev 2.0.0 | Låg | Väg B (AGP 8.11.1 + Gradle 8.13) |
| Installerade `android-36.1` (trasig source.properties) stör AGP:s SDK-scan | Låg–medel | Fas 0: rör den inte, eller ominstallera/ta bort den |
| Compose 1.5.14 inkompatibel med AGP 8.9 | Låg | Bumpa till 1.5.15 (K1.9.25) eller Compose BOM-uppdatering; separat commit |
| Nya Lint-errors bryter `lintRelease` i release-bygget | Medel | `lint { abortOnError = false }` tillfälligt, åtgärda separat |
| targetSdk 36 edge-to-edge bryter layout | Medel | Fas 3 manuell rök, insets-fix i Compose |
| Gradle 8.11.1 + JDK 26-daemon | Låg–medel | JDK 21-pinnen kvar (§6) |
| CI-runner saknar `platforms;android-36` | Medel | Fas 4: explicit sdkmanager-steg |
| Wrapper-jar-diff + `.gitattributes` eol | Låg | Verifiera `gradlew`=LF, `.bat`=CRLF efter fas 1 |

## 8. Rollback

Allt ligger i git. Per fas:

- Fas 1: `git revert` wrapper-commiten + `./gradlew wrapper --gradle-version 8.7`.
- Fas 2: `git revert` AGP-commiten.
- Fas 3: `git revert` compileSdk-commiten (separat, så den kan rullas
  tillbaka utan att röra verktygskedjan).

Håll faserna som separata commits så `git bisect` fungerar.

## 9. Öppna beslut

1. ~~Väg A eller väg B?~~ **Avgjort 2026-09-06: väg A.** Plain
   `platforms;android-36` rev 2.0.0 bekräftad tillgänglig (§3), så AGP 8.9.1
   + Gradle 8.11.1 räcker. Väg B kvar som reserv.
2. Bumpa Compose BOM 2024.06.00 → nyare i samma svep, eller separat?
   Rekommendation: separat, efter att verktygskedjan är grön.
3. Fas 3 (compileSdk 36 + Android 16-beteende) i samma PR som fas 1–2 eller
   egen PR? Rekommendation: egen PR — verktygskedja och beteendeändringar
   är olika sorters risk.
4. Höja `minSdk` samtidigt? (Ligger på 26 = Android 8.0.) Ingen anledning
   just nu; låt vara.
