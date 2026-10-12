import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.taverndroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.taverndroid"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        ndk { abiFilters += listOf("arm64-v8a") }
        val stBundle = rootProject.file("app/src/main/assets/st-bundle.version")
        buildConfigField("String", "ST_BUNDLE", "\"${if (stBundle.exists()) stBundle.readText().trim() else "missing"}\"")
    }

    signingConfigs {
        // Release signing is read from keystore.properties (not committed); falls back to debug signing.
        val props = rootProject.file("keystore.properties")
        if (props.exists()) {
            val p = Properties().apply { props.inputStream().use { load(it) } }
            create("release") {
                storeFile = rootProject.file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        jniLibs {
            // Node.js and git are executables: they must be extracted to nativeLibraryDir
            // (the only app location Android allows exec() from) and left untouched.
            useLegacyPackaging = true
            keepDebugSymbols += "**/*.so"
        }
    }

    androidResources {
        // The bundle is already a zip; storing it uncompressed lets the installer stream it with a known length.
        noCompress += listOf("zip")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.webkit:webkit:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
