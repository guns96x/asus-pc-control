plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hermes.pccontrol"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hermes.pccontrol"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8 drops unused Compose/icon classes: the APK shrinks from ~16 MB to a few MB.
            isMinifyEnabled = true
            isShrinkResources = true
            // Installed builds are signed with the Android Studio debug key; keep it so OTA updates install in place.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

// Shown in the app's OTA dialog via /api/app/version.
val releaseNotes = "Менший APK (R8), опитування ПК лише коли застосунок відкритий, захищеніший агент"

// Copies the release APK next to the PC agent and records its version, so OTA never
// advertises a build that differs from the APK actually served.
val publishApk by tasks.registering {
    dependsOn("assembleRelease")
    val code = android.defaultConfig.versionCode
    val name = android.defaultConfig.versionName
    val apkDir = layout.buildDirectory.dir("outputs/apk/release")
    val repoRoot = rootProject.layout.projectDirectory.dir("..")
    doLast {
        val apk = apkDir.get().asFile.listFiles { f -> f.extension == "apk" }.orEmpty().single()
        for (target in listOf("AsusControl.apk", "pc-agent/AsusControl.apk")) {
            apk.copyTo(repoRoot.file(target).asFile, overwrite = true)
        }
        val json = groovy.json.JsonOutput.toJson(
            mapOf("version_code" to code, "version_name" to name, "download_url" to "/app.apk", "release_notes" to releaseNotes)
        )
        repoRoot.file("pc-agent/app-version.json").asFile.writeText(groovy.json.JsonOutput.prettyPrint(json) + "\n")
    }
}
