plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "home.tv.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "home.tv.launcher"
        // Modifier.blur работает с API 31, на SK1 Android 14
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.okhttp)
    implementation(libs.okhttp.doh)
}
