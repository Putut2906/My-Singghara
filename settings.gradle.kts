pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io") {
            content {
                includeGroupByRegex("com\\.github\\..*")
                includeGroupByRegex("io\\.github\\..*")
            }
        }
    }
}

rootProject.name = "My Singghara"
include(":app")
// KMP migration (Fase 2): shared logic/UI lives in :shared (androidTarget + desktop jvm),
// and :desktopApp is the Compose Multiplatform Windows shell. :app stays untouched for now.
include(":shared")
include(":desktopApp")
