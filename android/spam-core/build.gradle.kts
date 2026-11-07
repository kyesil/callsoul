plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
