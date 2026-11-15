import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(21)
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.aistudio.singghara.desktop.MainKt"

        // Fase 5 refines iconFile/modules; this scaffold makes packageExe/packageMsi resolvable.
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "MySingghara"
            packageVersion = "1.0.0"
            vendor = "My Singghara"
            description = "My Singghara desktop"

            windows {
                menuGroup = "My Singghara"
                upgradeUuid = "5C06CABA-8A9B-48AB-B456-A91C8A0541AE"
                // iconFile.set(project.file("src/desktopMain/resources/app.ico")) // Fase 5: add .ico
            }
        }
    }
}
