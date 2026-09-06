import java.util.Properties
import java.io.FileInputStream

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        load(FileInputStream(file))
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("kotlin-kapt")
}

android {
    namespace = "com.avgangsplaneraren.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.avgangsplaneraren.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.1.0"

        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY", "")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Release-signering läses från local.properties (gitignorad, aldrig
    // committad — samma mönster som MAPS_API_KEY högst upp i filen). Saknas
    // RELEASE_STORE_FILE (t.ex. på CI eller en annan maskin) skapas ingen
    // signingConfig, och release-bygget blir osignerat i stället för att
    // krascha konfigurationsfasen. Debug-flödet och CI:s assembleDebug rörs
    // inte.
    signingConfigs {
        val releaseStoreFile = localProperties.getProperty("RELEASE_STORE_FILE")
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    testOptions {
        // Låter JVM-enhetstester anropa android.util.Log m.fl. utan att kasta
        // "not mocked" — de returnerar bara defaultvärden. Behövs sedan
        // TrafikverketRestStopRepository loggar när en sökning ger 0 träffar.
        unitTests.isReturnDefaultValues = true
    }
}

// Pinnar den JDK som kompilerar och kör tester till 21 (senaste LTS som
// Gradle 8.7 stödjer). Utan detta används den JDK som råkar köra Gradle,
// vilket går sönder på nyare JDK:er. Bytekodnivån är fortfarande 17 (se
// compileOptions/kotlinOptions ovan). JDK 21 auto-nedladdas via Foojay-
// resolvern i settings.gradle.kts om den saknas lokalt.
//
// OBS: detta styr INTE JVM:en som kör själva Gradle-daemonen – den måste
// startas med JDK <= 21 (via JAVA_HOME eller org.gradle.java.home i din
// personliga ~/.gradle/gradle.properties).
kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.core:core-ktx:1.13.1")

    implementation("com.google.android.gms:play-services-maps:19.0.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
}
