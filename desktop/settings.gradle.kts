// Build Gradle MANDIRI untuk versi desktop. Sengaja tidak di-include ke settings.gradle.kts
// milik aplikasi Android, supaya build/CI Android (AGP 9.1.1) tidak terpengaruh sama sekali.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "my-singghara-desktop"
