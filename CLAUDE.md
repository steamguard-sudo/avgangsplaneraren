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

## CI (GitHub Actions)

`.github/workflows/android.yml` kör på **push och PR mot `main`**:

1. `actions/setup-java@v4` med Temurin **JDK 21** (kör Gradle-daemonen — se
   JDK 26-fällan ovan; matchar även `jvmToolchain(21)`).
2. `gradle/actions/setup-gradle@v4` för dependency-/build-cache mellan körningar.
3. `./gradlew testDebugUnitTest --console=plain --stacktrace`
4. `./gradlew assembleDebug --console=plain --stacktrace`
5. Laddar upp `app-debug.apk` som artefakt (`app-debug`), och — bara vid fel —
   testrapporten under `app/build/reports/tests/testDebugUnitTest`.

Runnern är `ubuntu-latest`, så `org.gradle.java.home` / JBR-sökvägarna ovan
gäller bara lokalt; på CI räcker `setup-java`. Bygget behöver ingen
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
