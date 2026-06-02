plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.freekiosk.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.freekiosk.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // NanoHTTPD para servidor WiFi embebido
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    // Coroutines para slideshow timer
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
