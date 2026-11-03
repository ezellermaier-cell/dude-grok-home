plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dude.grokhome"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dude.grokhome"
        minSdk = 29
        // Keep the app compatible with Android 10 and avoid newer runtime-only
        // notification/foreground-service requirements on the Tab S4.
        targetSdk = 29
        versionCode = 2
        versionName = "2.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}
