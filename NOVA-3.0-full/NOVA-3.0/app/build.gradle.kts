plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android { namespace = "hu.novamobile"; compileSdk = 35
    defaultConfig { applicationId = "hu.novamobile"; minSdk = 26; targetSdk = 35; versionCode = 30; versionName = "3.0" }
}
