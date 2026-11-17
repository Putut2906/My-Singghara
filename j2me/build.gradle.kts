// Modul engine J2ME-Loader (di-vendor sebagai library, Java-only) agar game .jar/.jad
// berjalan NATIF di dalam APK My Singghara. Engine diluncurkan lewat MicroActivity (proses :midlet).
plugins {
    id("com.android.library")
}

android {
    namespace = "ru.playsoftware.j2meloader"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        // NDK upstream J2ME-Loader; bila belum terpasang, install via SDK Manager.
        ndkVersion = "29.0.14206865"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        externalNativeBuild {
            ndkBuild {
                arguments += "-j4"
            }
        }
        buildConfigField("boolean", "FULL_EMULATOR", "true")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        prefab = true
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(project(":dexlib"))

    val roomVersion = "2.8.5"
    val cameraxVersion = "1.6.2"
    val autoServiceVersion = "1.1.1"

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-rxjava2:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.preference:preference:1.2.1")

    implementation("com.android.volley:volley:1.2.1")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("com.google.guava:guava:33.7.2-android")

    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("net.lingala.zip4j:zip4j:2.11.6")

    implementation("com.github.nikita36078:pngj:2.2.3")
    implementation("io.github.nikita36078:ffmpeg-kit:6.0.LTS")
    implementation("com.google.oboe:oboe:1.11.0")

    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    implementation("com.github.nikita36078:filepicker:4.3.0")
    implementation("com.github.yukuku:ambilwarna:2.0.1")

    implementation("ch.acra:acra-dialog:5.14.2")

    annotationProcessor("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.21")
    compileOnly("com.google.auto.service:auto-service-annotations:$autoServiceVersion")
    annotationProcessor("com.google.auto.service:auto-service:$autoServiceVersion")
}
