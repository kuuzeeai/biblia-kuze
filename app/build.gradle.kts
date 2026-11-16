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
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("biblia") {
            storeFile = file("biblia.jks")
            storePassword = "biblia1234"
            keyAlias = "biblia"
            keyPassword = "biblia1234"
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
