plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    // Matches the app's Java 21 target and the CI JDK (Zulu 21).
    jvmToolchain(21)

    androidTarget()
    jvm("desktop")

    compilerOptions {
        // Kept consistent with :app (firebase artifacts compiled with newer Kotlin metadata).
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                // KMP ViewModel (Phase 3 will move MainViewModel here).
                implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

android {
    namespace = "com.aistudio.singghara.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
