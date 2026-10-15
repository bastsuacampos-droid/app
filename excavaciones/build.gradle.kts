plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "cl.sacyr.rutaitata.excavaciones"
    compileSdk = 35

    defaultConfig {
        applicationId = "cl.sacyr.rutaitata.excavaciones"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.room:room-testing:$room")
}
