plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.play.publisher)
}

android {
    namespace = "com.bettercallsoul"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.yesil.callsoul"
        // Android 17 is the target; 35 keeps the Galaxy S21 test phone usable.
        minSdk = 29
        targetSdk = 37
        versionCode = 20
        versionName = "0.3.8"
    }

    // The Play upload key lives outside the repo; its path and password are in ~/.gradle/gradle.properties.
    val uploadStore = providers.gradleProperty("bcs.uploadStore").orNull
    signingConfigs {
        if (uploadStore != null) {
            create("upload") {
                storeFile = file(uploadStore)
                storePassword = providers.gradleProperty("bcs.uploadPassword").get()
                keyAlias = "upload"
                keyPassword = providers.gradleProperty("bcs.uploadPassword").get()
            }
        }
    }

    buildTypes {
        // Every build talks to the public server; `bcs.apiUrl` points one at another, for example
        // `-Pbcs.apiUrl=http://localhost:3000` with `adb reverse tcp:3000 tcp:3000` for the dev server.
        val apiUrl = providers.gradleProperty("bcs.apiUrl").getOrElse("https://callsoul.yesil.dev")
        val backupUrls = providers.gradleProperty("bcs.apiBackupUrls").getOrElse("")
        debug {
            buildConfigField("String", "API_URL", "\"$apiUrl\"")
            buildConfigField("String", "API_BACKUP_URLS", "\"$backupUrls\"")
            buildConfigField("Boolean", "LOG_BY_DEFAULT", "true")
            // Plain http is allowed only here, for the local dev server.
            manifestPlaceholders["cleartext"] = "true"
        }
        release {
            buildConfigField("String", "API_URL", "\"$apiUrl\"")
            buildConfigField("String", "API_BACKUP_URLS", "\"$backupUrls\"")
            buildConfigField("Boolean", "LOG_BY_DEFAULT", "false")
            manifestPlaceholders["cleartext"] = "false"
            if (uploadStore != null) signingConfig = signingConfigs.getByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        // For handing out to testers (preview): shrunk like a release (a few MB instead of ~65), signed with
        // the debug key so it updates a debug install in place, and with the debug log on.
        create("preview") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            buildConfigField("Boolean", "LOG_BY_DEFAULT", "true")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":telecom"))
    implementation(project(":calllog"))
    implementation(project(":spam-core"))
    implementation(project(":island"))
    implementation(project(":diag"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    // QR codes through Google Play services' scanner screen: no camera permission of our own.
    implementation(libs.code.scanner)
    // Play services pull in an old Fragment, whose activity results the permission requests can't use.
    implementation(libs.androidx.fragment)
}

// Publishing from the command line needs a Play service account key, kept outside this repo; see store/README.md.
val playKey = providers.gradleProperty("bcs.playKey").orNull?.let(::file)?.takeIf { it.exists() }
play {
    enabled.set(playKey != null)
    playKey?.let { serviceAccountCredentials.set(it) }
    defaultToAppBundles.set(true)
    track.set("internal")
}
