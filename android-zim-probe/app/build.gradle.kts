plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val pwaSourceDirectory = rootProject.projectDir.parentFile
val bundledGuideAssets = layout.buildDirectory.dir("generated/guideAssets")
val guideFichesJson = layout.buildDirectory.file("generated/guideData/guide-fiches.json")
val exportGuideFichesJson = tasks.register("exportGuideFichesJson") {
    val fichesSource = pwaSourceDirectory.resolve("fiches.js")
    inputs.file(fichesSource)
    outputs.file(guideFichesJson)
    doLast {
        val source = fichesSource.readText()
        val prefix = "window.FICHES="
        val start = source.indexOf(prefix)
        val end = Regex(";\\r?\\n\\r?\\nwindow\\.CHECKLIST")
            .find(source, (start + prefix.length).coerceAtLeast(0))
            ?.range?.first ?: -1
        check(start >= 0 && end > start) {
            "Could not extract the offline guide fiches from fiches.js."
        }
        val json = source.substring(start + prefix.length, end).trim()
        val output = guideFichesJson.get().asFile
        output.parentFile.mkdirs()
        output.writeText(json)
    }
}
val syncGuideAssets = tasks.register<Sync>("syncGuideAssets") {
    from(pwaSourceDirectory) {
        include(
            "index.html",
            "app.js",
            "fiches.js",
            "styles.css",
            "sw.js",
            "manifest.webmanifest",
            "SOURCES.md",
            "category-atlas.png",
            "procedure-atlas-*.png",
            "illustrations.svg",
            "icons/**"
        )
        into("guide")
    }
    from(guideFichesJson) {
        rename { "guide-fiches.json" }
        into("guide")
    }
    into(bundledGuideAssets)
    dependsOn(exportGuideFichesJson)
}

val debugBuildId = System.getenv("GITHUB_RUN_NUMBER")?.takeIf { it.isNotBlank() } ?: "local"

android {
    namespace = "be.johunn.survie.zimprobe"
    compileSdk = 35
    defaultConfig {
        applicationId = "be.johunn.survie.zimprobe"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "0.10.0"
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
    sourceSets.getByName("main").assets.srcDir(bundledGuideAssets)
}

tasks.named("preBuild").configure {
    dependsOn(syncGuideAssets)
}

dependencies {
    // AAR Kiwix 2.7.0 (libkiwix 14.2.1 / libzim 9.8.2) avec libzim
    // recompilé et patché pour conserver le descripteur SAF autorisé.
    implementation(files("libs/libkiwix-patched.aar"))
}
