plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hyaeve.lumir"
    compileSdk = 35

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("LUMIR_KEYSTORE_FILE")
            val keystorePassword = System.getenv("LUMIR_KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("LUMIR_KEY_ALIAS")
            val keyPassword = System.getenv("LUMIR_KEY_PASSWORD")

            if (!keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank() &&
                !keyAlias.isNullOrBlank() && !keyPassword.isNullOrBlank()
            ) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.hyaeve.lumir"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { buildConfig = true }
}

kotlin { jvmToolchain(17) }

// Keep the first client independent from the Lumic server source tree.
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("com.google.android.material:material:1.12.0")
}
