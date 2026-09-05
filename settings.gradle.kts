pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    // Låter Gradle auto-nedladda en matchande JDK för Java-toolchainen
    // (se kotlin { jvmToolchain(21) } i app/build.gradle.kts) när ingen
    // lokal JDK 21 hittas – t.ex. på CI.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Avgangsplaneraren"
include(":app")
