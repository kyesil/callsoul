plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.bettercallsoul.calllog"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.coroutines.android)
    api(project(":spam-core"))
    implementation(libs.androidx.core.ktx)
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(kotlin("test-junit"))
}
