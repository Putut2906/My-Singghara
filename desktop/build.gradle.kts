import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    // Versi Kotlin & KSP disamakan dengan aplikasi Android (gradle/libs.versions.toml).
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.compose") version "2.2.10"
    id("org.jetbrains.compose") version "1.8.2"
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
}

// Versi aplikasi: di CI diisi dari -PappVersionName=1.0.<run_number>, sama seperti APK-nya.
val appVersion: String = providers.gradleProperty("appVersionName").orNull ?: "1.0.0"

// ---------------------------------------------------------------------------------------------
// Berkas yang DIPAKAI BERSAMA dengan aplikasi Android (tidak disalin manual, tidak diedit).
// ---------------------------------------------------------------------------------------------
val androidMain: File = rootDir.resolve("../app/src/main")

// 1) Berkas Kotlin Android yang tidak punya dependensi Android sama sekali:
//    - 21 objek *WebScript.kt (HTML/JS UI web, tanpa import apa pun)
//    - entity + DAO Room (hanya memakai androidx.room.* dan Flow, keduanya ada di JVM)
//    - PrayerTimesHelper (java.util.Calendar + kotlin.math)
val syncSharedSources = tasks.register<Sync>("syncSharedSources") {
    group = "my singghara"
    description = "Salin berkas Kotlin Android yang bebas dependensi Android ke build desktop."
    from(androidMain.resolve("java")) {
        include(
            "com/example/web/*WebScript.kt",
            "com/example/data/Models.kt",
            "com/example/data/Daos.kt",
            "com/example/data/QuranDao.kt",
            "com/example/data/QuranEntities.kt",
            "com/example/data/FcmNotification.kt",
            "com/example/ui/components/PrayerTimesHelper.kt",
        )
    }
    into(layout.buildDirectory.dir("generated/shared/kotlin"))
}

// 2) Halaman web utama. Di Android ia berupa fungsi private getWebUiHtml() di dalam
//    LocalHttpServer.kt (fungsi terakhir di berkas itu, murni string + 21 objek WebScript).
//    Tugas ini mengekstraknya apa adanya, jadi halaman desktop selalu sama dengan versi HP.
val generateWebUiPage = tasks.register("generateWebUiPage") {
    group = "my singghara"
    description = "Ekstrak LocalHttpServer.getWebUiHtml() menjadi WebUiPage.kt untuk desktop."
    val source = androidMain.resolve("java/com/example/web/LocalHttpServer.kt")
    val outDir = layout.buildDirectory.dir("generated/webui/kotlin")
    inputs.file(source)
    outputs.dir(outDir)
    doLast {
        val lines = source.readLines()
        val start = lines.indexOfFirst { it.trim() == "private fun getWebUiHtml(): String {" }
        val end = lines.indexOfLast { it == "    }" }
        val tailOk = lines.drop(end + 1).all { it.isBlank() || it == "}" }
        require(start >= 0 && end > start && tailOk) {
            "Struktur LocalHttpServer.kt berubah: getWebUiHtml() tidak lagi fungsi terakhir. " +
                "Pindahkan fungsi itu ke objek mandiri (lihat desktop/README.md, bagian 'Tahap 2') " +
                "atau sesuaikan penanda di desktop/build.gradle.kts."
        }
        val body = lines.subList(start, end + 1).joinToString("\n") {
            it.replaceFirst("private fun getWebUiHtml()", "fun html()")
        }
        val target = outDir.get().asFile.resolve("com/example/web/WebUiPage.kt")
        target.parentFile.mkdirs()
        target.writeText(
            "package com.example.web\n\n" +
                "// DIHASILKAN OTOMATIS dari LocalHttpServer.getWebUiHtml(). Jangan diedit di sini.\n" +
                "object WebUiPage {\n$body\n}\n"
        )
    }
}

// Perlu JDK 17 atau lebih baru (disarankan 21, sama dengan CI Android).
kotlin {
    sourceSets.main {
        kotlin.srcDir(syncSharedSources)
        kotlin.srcDir(generateWebUiPage)
    }
}

// 3) Aset web (CSS/JS/ikon/font) dipakai langsung dari folder assets Android.
//    Azan (mp3), installer PC lama, dan google-services.json tidak ikut.
sourceSets.main {
    resources.srcDir(androidMain.resolve("assets"))
    resources.exclude("mp3/**", "web/pc/**", "google-services.json", ".aistudio/**")
}

// KSP: Room harus menghasilkan kode Kotlin (bukan Java) untuk target non-Android.
ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // Room multiplatform (target JVM desktop) + SQLite bawaan; versi Room sama dengan aplikasi Android.
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.sqlite:sqlite-bundled:2.5.2")
    ksp("androidx.room:room-compiler:2.7.2")

    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

compose.desktop {
    application {
        mainClass = "desktop.MainKt"

        nativeDistributions {
            // Installer dibuat per sistem operasi: .exe dan .msi (Windows, butuh WiX Toolset 3.x),
            // .dmg (macOS), .deb (Linux). jpackage tidak bisa lintas-OS, jadi tiap format dibangun
            // di OS-nya (lihat .github/workflows/build-desktop.yml).
            // Tanpa WiX: `gradlew createDistributable` menghasilkan folder portabel berisi
            // "My Singghara.exe" (build/compose/binaries/main/app/).
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "My Singghara"
            packageVersion = appVersion
            description = "My Singghara untuk PC"
            vendor = "My Singghara"

            // Modul JDK yang tidak terdeteksi otomatis tetapi dipakai saat berjalan.
            modules("jdk.httpserver", "java.sql", "jdk.unsupported", "java.naming")

            windows {
                menuGroup = "My Singghara"
                shortcut = true
                perUserInstall = true
                iconFile.set(androidMain.resolve("assets/web/favicon.ico"))
                // Jangan diganti: ID ini yang membuat versi baru menimpa versi lama di Windows.
                upgradeUuid = "6f1c2f3e-5b7a-4c1e-9a53-2d8b7e0c4a11"
            }
            macOS {
                bundleID = "com.aistudio.mysingghara.desktop"
                // Untuk ikon Dock sendiri, tambahkan file .icns lalu aktifkan baris ini:
                // iconFile.set(project.file("packaging/icon.icns"))
            }
            linux {
                iconFile.set(androidMain.resolve("assets/web/icon-512.png"))
                shortcut = true
                menuGroup = "Office"
            }
        }
    }
}
