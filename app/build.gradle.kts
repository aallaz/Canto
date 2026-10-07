plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.canto"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.canto"
        minSdk = 24
        targetSdk = 34
        // Sur GitHub Actions, chaque build a un numéro plus grand : Android accepte la mise à jour.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(1) ?: 1
        versionName = "0.1.$versionCode"
    }

    // Clé de signature fixe fournie par les secrets GitHub (voir README) : indispensable pour
    // que les mises à jour s'installent par-dessus l'app sans la désinstaller.
    val releaseKeystore = System.getenv("CANTO_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    if (releaseKeystore != null) {
        signingConfigs {
            getByName("debug") {
                storeFile = releaseKeystore
                storePassword = System.getenv("CANTO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CANTO_KEY_ALIAS") ?: "canto"
                keyPassword = System.getenv("CANTO_KEY_PASSWORD") ?: System.getenv("CANTO_KEYSTORE_PASSWORD")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.7.0")
    implementation("androidx.compose.foundation:foundation:1.5.0")
    implementation("androidx.compose.ui:ui:1.5.0")
    implementation("androidx.compose.ui:ui-graphics:1.5.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.0")
    implementation("androidx.compose.material3:material3:1.1.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.5.0")
}
