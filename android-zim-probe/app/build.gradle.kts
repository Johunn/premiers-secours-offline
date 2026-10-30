plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val debugBuildId = System.getenv("GITHUB_RUN_NUMBER")?.takeIf { it.isNotBlank() } ?: "local"

android {
    namespace = "be.johunn.survie.zimprobe"
    compileSdk = 35
    defaultConfig {
        applicationId = "be.johunn.survie.zimprobe"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.7.0"
        // Le prototype vise le Galaxy S21 FE (ARM64). Évite d'embarquer
        // trois architectures inutiles dans l'APK de test.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
    buildTypes {
        getByName("debug") {
            // GitHub Actions generates a new debug signing key for each run.
            // A unique package lets each debug APK install alongside older ones.
            applicationIdSuffix = ".debug.run$debugBuildId"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    // AAR Kiwix 2.7.0 (libkiwix 14.2.1 / libzim 9.8.2) avec libzim
    // recompilé et patché pour conserver le descripteur SAF autorisé.
    implementation(files("libs/libkiwix-patched.aar"))
}
