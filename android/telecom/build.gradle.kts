plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.bettercallsoul.telecom"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    api(project(":calllog"))
    api(project(":spam-core"))
    implementation(project(":island"))
    implementation(project(":diag"))
    implementation(libs.androidx.core.ktx)
}
