# Fix Build Error: :app:checkDebugAarMetadata

The error `:app:checkDebugAarMetadata` indicates that your project is using dependencies that require a higher **Android SDK version (compileSdk)** or a newer **Android Gradle Plugin (AGP)** than what is currently configured.

## Analysis
Based on the build logs, there are 36 version mismatches. The primary issues are:
1.  **`com.google.maps.android:maps-compose:8.4.0`** requires `compileSdk 37` or higher.
2.  **`androidx.core:core-ktx:1.19.0`** requires `compileSdk 37` and **AGP 9.1.0** or higher.
3.  **Compose libraries (v1.11.4)** require `compileSdk 35` and **AGP 8.6.0** or higher.

Your current configuration:
- `compileSdk = 34`
- `Android Gradle Plugin = 8.5.0`

## Proposed Changes

I recommend upgrading your project configuration to support the libraries you've added.

### 1. Update Android Gradle Plugin (AGP)
Upgrade the AGP version in the root `build.gradle.kts`.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/Z97X/Documents/avgangsplaneraren/AvgangsplanerarenAndroid/build.gradle.kts)
- Update `com.android.application` version from `8.5.0` to `9.1.0` (or `9.3.1`).
- Update `org.jetbrains.kotlin.android` version to a compatible version (e.g., `2.0.20` or higher).

### 2. Update Android SDK Versions
Update the SDK versions in the app-level `build.gradle.kts`.

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/Z97X/Documents/avgangsplaneraren/AvgangsplanerarenAndroid/app/build.gradle.kts)
- Set `compileSdk = 37`.
- Set `targetSdk = 37`.
- If upgrading Kotlin to 2.0+, remove `kotlinCompilerExtensionVersion` and apply the Compose Compiler plugin.

### 3. Update Gradle Wrapper
Newer AGP versions require newer Gradle versions.

#### [MODIFY] [gradle-wrapper.properties](file:///C:/Users/Z97X/Documents/avgangsplaneraren/AvgangsplanerarenAndroid/gradle/wrapper/gradle-wrapper.properties)
- Update `distributionUrl` to use Gradle `8.10` or higher (depending on the chosen AGP).

---

## User Review Required

> [!IMPORTANT]
> **Major Version Upgrades**: Upgrading AGP from 8.5 to 9.1+ and Kotlin to 2.x is a significant change. It may require additional adjustments in your code, especially regarding the Compose Compiler and any deprecated APIs in newer SDKs.

> [!WARNING]
> **Kotlin 2.0 Migration**: If we upgrade Kotlin to 2.0 or higher, the way the Compose Compiler is configured changes. We will need to add the `org.jetbrains.kotlin.plugin.compose` plugin.

## Open Questions
1. Do you want to proceed with the upgrade to **SDK 37** and **AGP 9.1+**, or would you prefer to **downgrade** the libraries (e.g., Maps Compose) to versions that work with your current SDK 34?
2. If we upgrade, should I aim for the latest stable versions (AGP 9.3.1, Kotlin 2.4.10) or specifically what the error requested (AGP 9.1.0)?

## Verification Plan
1. Run `./gradlew :app:checkDebugAarMetadata` to verify the metadata check passes.
2. Run a full build `./gradlew assembleDebug` to ensure compilation is successful.
3. Deploy to a device/emulator to verify runtime behavior.
