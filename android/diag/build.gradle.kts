plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.bettercallsoul.diag"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }
}

kotlin {
    jvmToolchain(21)
}
