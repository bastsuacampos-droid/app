plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firma de las versiones publicadas. La clave nunca va en el repositorio: se entrega por
// variables de entorno (en GitHub, desde los secrets del workflow publicar-app.yml).
val firmaArchivo: String? = System.getenv("FIRMA_KEYSTORE")
val firmaClave: String? = System.getenv("FIRMA_CLAVE")

android {
    namespace = "cl.sacyr.rutaitata.charlas"
    compileSdk = 35

    defaultConfig {
        applicationId = "cl.sacyr.rutaitata.charlas"
        minSdk = 26
        targetSdk = 35
        // Para publicar una versión nueva: subir ambos números, escribir app/novedades-version.txt
        // y llevar el cambio a main (ver .github/workflows/publicar-app.yml).
        versionCode = 3
        versionName = "1.2"

        // Banco de charlas publicado: la app lo revisa al abrirse y aplica las versiones nuevas.
        buildConfigField(
            "String",
            "URL_CONTENIDO",
            "\"https://raw.githubusercontent.com/bastsuacampos-droid/app/main/app/src/main/assets/charlas.json\"",
        )
        // Versiones publicadas (GitHub Releases), para actualizar la app desde la misma app. El repositorio
        // también publica otras apps: solo cuentan las releases con tag "charlas-v…" (ver Versiones).
        buildConfigField(
            "String",
            "URL_VERSIONES",
            "\"https://api.github.com/repos/bastsuacampos-droid/app/releases?per_page=100\"",
        )
    }

    signingConfigs {
        create("publicacion") {
            if (firmaArchivo != null && firmaClave != null) {
                storeFile = file(firmaArchivo)
                storePassword = firmaClave
                keyAlias = System.getenv("FIRMA_ALIAS") ?: "androiddebugkey"
                keyPassword = firmaClave
            }
        }
    }

    buildTypes {
        release {
            if (firmaArchivo != null && firmaClave != null) {
                signingConfig = signingConfigs.getByName("publicacion")
            }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
