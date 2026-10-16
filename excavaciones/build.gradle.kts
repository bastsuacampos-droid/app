plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

// Firma de las versiones publicadas: la misma clave y los mismos secrets que la app de charlas
// (FIRMA_KEYSTORE / FIRMA_CLAVE, ver .github/workflows/publicar-excavaciones.yml). Nunca va al repositorio.
val firmaArchivo: String? = System.getenv("FIRMA_KEYSTORE")
val firmaClave: String? = System.getenv("FIRMA_CLAVE")

android {
    namespace = "cl.sacyr.rutaitata.excavaciones"
    compileSdk = 35

    defaultConfig {
        applicationId = "cl.sacyr.rutaitata.excavaciones"
        minSdk = 26
        targetSdk = 35
        // Para publicar una versión nueva: subir ambos números, escribir novedades-version.txt
        // y llevar el cambio a main (ver .github/workflows/publicar-excavaciones.yml).
        versionCode = 2
        versionName = "1.0"

        // Versiones publicadas (GitHub Releases) para actualizar desde la misma app. Solo cuentan
        // las releases con tag "excavaciones-v…": el repositorio publica también otras apps.
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
            // Igual que la app de charlas: sin R8, un fallo de reflexión (Room) al iniciar no se perdona en terreno.
            isMinifyEnabled = false
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// El esquema de cada versión de la base queda en el repositorio: sirve para escribir y probar las
// migraciones. Nunca editar un JSON ya publicado; una versión nueva genera su propio archivo.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val room = "2.7.1"
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // Orientación de las fotos (EXIF) al importarlas desde la cámara o la galería.
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    testImplementation("junit:junit:4.13.2")
    // org.json real para las pruebas JVM (el de android.jar es un stub). Lo usa VersionesTest.
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.room:room-testing:$room")
    // Pruebas de pantallas con Robolectric.
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
