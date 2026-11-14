plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.bettercallsoul.island"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(project(":diag"))
}
