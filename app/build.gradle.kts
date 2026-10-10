plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseSigningValues = mapOf(
    "storeFile" to providers.environmentVariable("KUIESVOX_KEYSTORE_FILE").orNull,
    "keyAlias" to providers.environmentVariable("KUIESVOX_KEY_ALIAS").orNull,
    "keyPassword" to providers.environmentVariable("KUIESVOX_KEY_PASSWORD").orNull,
    "storePassword" to providers.environmentVariable("KUIESVOX_STORE_PASSWORD").orNull
)
val hasAnyReleaseSigningValue = releaseSigningValues.values.any { !it.isNullOrBlank() }
val hasCompleteReleaseSigning = releaseSigningValues.values.all { !it.isNullOrBlank() }

if (hasAnyReleaseSigningValue && !hasCompleteReleaseSigning) {
    throw GradleException("Release signing requires all KUIESVOX_* signing environment variables.")
}

android {
    namespace = "tw.kuies.voiceime"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "tw.kuies.voiceime"
        minSdk = 26
        targetSdk = 37
        versionCode = 11
        versionName = "0.15.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasCompleteReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigningValues["storeFile"]))
                keyAlias = requireNotNull(releaseSigningValues["keyAlias"])
                keyPassword = requireNotNull(releaseSigningValues["keyPassword"])
                storePassword = requireNotNull(releaseSigningValues["storePassword"])
            }
        }
    }

    buildTypes {
        release {
            if (hasCompleteReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = true
                // packageScope turns on partial R8 shrinking. It produced a package-private
                // kotlin.text class that another package extends, so the release APK died at
                // startup with IllegalAccessError. Leave it unset (defaults to all packages).
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.okhttp)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20250517")
    testImplementation(libs.mockwebserver3)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
