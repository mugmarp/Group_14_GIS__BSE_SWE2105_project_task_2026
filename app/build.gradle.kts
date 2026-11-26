import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.geotrace"
    compileSdk = 36

    val versionCodeOverride = System.getenv("ORG_GRADLE_PROJECT_versionCode")?.toIntOrNull()
    val versionNameOverride = System.getenv("ORG_GRADLE_PROJECT_versionName")

    defaultConfig {
        applicationId = "com.geotrace"
        minSdk = 26
        targetSdk = 36
        versionCode = versionCodeOverride ?: 1
        versionName = versionNameOverride ?: "1.0.0"

        val envFile = rootProject.file(".env")
        val envProps = Properties()
        if (envFile.exists()) {
            envFile.inputStream().use { stream -> envProps.load(stream) }
        }
        val rawEnvFileKey = envProps.getProperty("OPENCELLID_API_KEY")
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("'")
        val rawSysEnvKey = System.getenv("OPENCELLID_API_KEY")
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("'")
        val openCellIdKey = (rawEnvFileKey?.takeIf { it.isNotBlank() && it != "OPENCELLID_API_KEY_DEFAULT_VALUE" }
            ?: rawSysEnvKey?.takeIf { it.isNotBlank() && it != "OPENCELLID_API_KEY_DEFAULT_VALUE" }
            ?: "")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "OPENCELLID_API_KEY", "\"$openCellIdKey\"")

        val rawOrsFileKey = envProps.getProperty("ORS_API_KEY")
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("'")
        val rawOrsSysKey = System.getenv("ORS_API_KEY")
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("'")
        val orsApiKey = (rawOrsFileKey?.takeIf { it.isNotBlank() && it != "ORS_API_KEY_DEFAULT_VALUE" }
            ?: rawOrsSysKey?.takeIf { it.isNotBlank() && it != "ORS_API_KEY_DEFAULT_VALUE" }
            ?: "")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "ORS_API_KEY", "\"$orsApiKey\"")
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("releaseConfig") {
            val storeFilePath = System.getenv("ORG_GRADLE_PROJECT_signingStoreFile") ?: ""
            val storePassword = System.getenv("ORG_GRADLE_PROJECT_signingStorePassword") ?: ""
            val keyAlias = System.getenv("ORG_GRADLE_PROJECT_signingKeyAlias") ?: ""
            val keyPassword = System.getenv("ORG_GRADLE_PROJECT_signingKeyPassword") ?: ""

            if (storeFilePath.isNotBlank() && file(storeFilePath).exists() && storePassword.isNotBlank() && keyAlias.isNotBlank() && keyPassword.isNotBlank()) {
                this.storeFile = file(storeFilePath)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            } else {
                // Fallback to debug keystore so release builds succeed reliably on CI and local
                this.storeFile = file("${rootDir}/debug.keystore")
                this.storePassword = "android"
                this.keyAlias = "androiddebugkey"
                this.keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debugConfig")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.play.services.location)
    implementation(libs.coil.compose)
    implementation(libs.osmdroid.android)

    // Firebase & Authentication
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // Phase 2: Nearby Connections P2P Mesh
    implementation(libs.play.services.nearby)

    // Room Offline Persistence Cache
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Local JVM Unit Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
