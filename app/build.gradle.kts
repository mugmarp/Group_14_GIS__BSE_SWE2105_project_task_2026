plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
        versionName = versionNameOverride ?: "1.0"

        val openCellIdKey = System.getenv("OPENCELLID_API_KEY") ?: ""
        buildConfigField("String", "OPENCELLID_API_KEY", "\"$openCellIdKey\"")
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
}
