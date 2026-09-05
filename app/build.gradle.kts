plugins {
    // Depuis AGP 9, le support Kotlin est intégré : le plugin org.jetbrains.kotlin.android
    // ne doit plus être appliqué. Seul le plugin du compilateur Compose reste nécessaire.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "fr.gaddiction.skellobadge"

    // Compose 1.12 impose de compiler contre l'API 37 ou plus récente.
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.gaddiction.skellobadge"
        // API 26 : java.time est disponible nativement, pas de desugaring à prévoir.
        minSdk = 26
        // Volontairement en retrait du compileSdk : cibler l'API 37 activerait des
        // changements de comportement système non vérifiés sur cette application.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Volontairement désactivé : l'APK est distribué en direct, sa taille n'est
            // pas un enjeu, et on évite toute règle de conservation à maintenir.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
