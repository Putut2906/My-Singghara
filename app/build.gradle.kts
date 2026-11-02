plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // Declared only to put the plugin on this project's classpath; it is applied below
    // when google-services.json actually registers the package used by this app.
    alias(libs.plugins.google.services) apply false
}

val appPackageId = "com.aistudio.mysingghara.q8wpa1"

// The google-services plugin aborts the build when google-services.json has no client for
// applicationId ("No matching client found for package name ..."), so it is enabled only
// once the file registers this package.
val googleServicesFile = file("google-services.json")
val googleServicesHasThisApp = googleServicesFile.exists() &&
    Regex("\"package_name\"\\s*:\\s*\"${Regex.escape(appPackageId)}\"")
        .containsMatchIn(googleServicesFile.readText())
if (googleServicesHasThisApp) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.example"
    compileSdk = 36

    defaultConfig {
        applicationId = appPackageId
        minSdk = 24
        targetSdk = 36
        // CI passes 1.0.<run_number> so BuildConfig matches the release tag.
        versionCode = (providers.gradleProperty("appVersionCode").orNull ?: "1").toIntOrNull() ?: 1
        versionName = providers.gradleProperty("appVersionName").orNull ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GEMINI_API_KEY", "\"\"")
    }

    signingConfigs {
        // debug.keystore is committed on purpose: every CI run must sign with the
        // same key, otherwise Android rejects the update as "app not installed"
        // (INSTALL_FAILED_UPDATE_INCOMPATIBLE) because the signatures differ.
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debugConfig")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        // firebase-auth 25.0.0 (BoM 35.1.0) is compiled with Kotlin 2.4 while this project
        // uses 2.2; its metadata is otherwise rejected as "incompatible version".
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.print:print:1.0.0")
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation("com.squareup.moshi:moshi:1.15.2")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    // Pemutar video: ExoPlayer + UI PlayerView, termasuk extractor MKV/AVI/FLV/3GP.
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    // Task.await() lives here; the Firebase artifacts no longer pull it in.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
