import java.util.Properties

plugins {
    // Depuis AGP 9, le support Kotlin est intégré : le plugin org.jetbrains.kotlin.android
    // ne doit plus être appliqué. Seul le plugin du compilateur Compose reste nécessaire.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Clé de signature de distribution.
 *
 * Deux provenances : le fichier keystore.properties sur un poste de développement, et
 * l'environnement dans la CI, qui n'a pas de fichier mais reçoit la clé par les secrets
 * du dépôt. L'environnement l'emporte quand les deux sont présents.
 *
 * En l'absence des deux — sur un poste qui vient de cloner — la variante release retombe
 * sur la clé de debug : le build ne casse pas, mais l'APK produit ne peut pas mettre à
 * jour une installation signée avec la vraie clé.
 */
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties")
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use(::load)

    System.getenv("SIGNING_STORE_FILE")?.let { setProperty("storeFile", it) }
    System.getenv("SIGNING_STORE_PASSWORD")?.let { setProperty("storePassword", it) }
    System.getenv("SIGNING_KEY_ALIAS")?.let { setProperty("keyAlias", it) }
    System.getenv("SIGNING_KEY_PASSWORD")?.let { setProperty("keyPassword", it) }
}
val hasReleaseKey = keystoreProperties.getProperty("storeFile") != null

/**
 * Version de l'application.
 *
 * La CI de publication dérive ces valeurs de l'étiquette Git, de sorte que l'APK annonce
 * la version sous laquelle il est distribué. Le code de version doit croître à chaque
 * publication, sans quoi Android refuse la mise à jour.
 */
val appVersionName: String = System.getenv("APP_VERSION_NAME") ?: "1.0"
val appVersionCode: Int = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 1

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
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("distribution") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("distribution")
                ?: signingConfigs.getByName("debug")

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
