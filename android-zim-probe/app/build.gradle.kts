plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "be.johunn.survie.zimprobe"
    compileSdk = 35
    defaultConfig {
        applicationId = "be.johunn.survie.zimprobe"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        // Le prototype vise le Galaxy S21 FE (ARM64). Évite d'embarquer
        // trois architectures inutiles dans l'APK de test.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    // AAR Kiwix 2.6.0 avec libzim 9.7.0 recompilé pour conserver
    // le descripteur SAF autorisé au lieu de rouvrir /dev/fd/N.
    implementation(files("libs/libkiwix-patched.aar"))
}
