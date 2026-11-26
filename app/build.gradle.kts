plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kuzeai.biblia"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kuzeai.biblia"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        create("biblia") {
            // Si hay una llave en secretos (KEYSTORE_PATH y KEYSTORE_PASS) se usa esa;
            // si no, se usa app/biblia.jks como hasta ahora (la misma firma, para poder actualizar).
            val rutaLlave = System.getenv("KEYSTORE_PATH")
            val claveLlave = System.getenv("KEYSTORE_PASS")
            if (!rutaLlave.isNullOrEmpty() && !claveLlave.isNullOrEmpty()) {
                storeFile = file(rutaLlave)
                storePassword = claveLlave
                keyPassword = claveLlave
            } else {
                storeFile = file("biblia.jks")
                storePassword = "biblia1234"
                keyPassword = "biblia1234"
            }
            keyAlias = "biblia"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("biblia")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("biblia")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
