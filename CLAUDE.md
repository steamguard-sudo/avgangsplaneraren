# Bygga projektet (assembleDebug m.fl.)

`gradlew` / `gradlew.bat` finns INTE i repot (de är gitignorade och skapas
normalt av Android Studio vid "Sync Project with Gradle Files" — se
KOM_IGANG.md). I den här utvecklingsmiljön finns ingen Android Studio-synk
körd, så wrapper-scripten saknas på disk. Använd istället den redan cachade
Gradle-distributionen direkt, via Git Bash (PowerShell kan inte exekvera det
extensionless launcher-scriptet).

## Fungerande kommando (kört och verifierat 2026-08-02)

Kör via Bash-verktyget (Git Bash), inte PowerShell:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio1/jbr"
cd "/c/Users/Z97X/Documents/avgangsplaneraren/AvgangsplanerarenAndroid"
"/c/Users/Z97X/.gradle/wrapper/dists/gradle-8.7-bin/bhs2wmbdwecv87pi65oeuq5iu/gradle-8.7/bin/gradle" assembleDebug --console=plain
```

Motsvarande i PowerShell (för Java-anrop, t.ex. `java -version`):

```powershell
& "C:\Program Files\Android\Android Studio1\jbr\bin\java.exe" -version
```

### Viktiga detaljer

- **JBR (bundlad JDK) ligger under `Android Studio1`, inte `Android Studio`.**
  Det finns två installationer på maskinen
  (`C:\Program Files\Android\Android Studio` och `...\Android Studio1`);
  bara `Android Studio1\jbr\bin\java.exe` existerar/fungerar. Verifiera vid
  behov med `Test-Path` i PowerShell innan du antar sökvägen.
- Java-version i JBR: OpenJDK 21.0.10.
- Den cachade Gradle 8.7-distributionen ligger under
  `C:\Users\Z97X\.gradle\wrapper\dists\gradle-8.7-bin\<hash>\gradle-8.7\bin\gradle`.
  Hash-katalogen (`bhs2wmbdwecv87pi65oeuq5iu`) kan ändras om cachen rensas
  eller byggs om — om sökvägen ovan inte finns, sök fram den nya med:
  ```bash
  find "/c/Users/Z97X/.gradle/wrapper/dists/gradle-8.7-bin" -maxdepth 2 -name gradle -type f
  ```
- Gradle-version bestäms av `gradle/wrapper/gradle-wrapper.properties`
  (för närvarande `gradle-8.7-bin.zip`). Om den filen uppdateras till en ny
  version måste motsvarande distribution finnas cachad (eller laddas ner) på
  nytt.
- Kör builden via Bash-verktyget (Git Bash), inte PowerShell — PowerShell
  kan inte köra det extensionless unix-launcher-scriptet `bin/gradle`
  (`& "...\bin\gradle"` misslyckas tyst utan felmeddelande).
