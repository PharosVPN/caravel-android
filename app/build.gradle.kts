// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Single source of truth for the version: the repo-root VERSION file (bare
// semver, e.g. "0.1.0"). Bump it with scripts/bump-version.sh. Read at
// configuration time so the APK's versionName always matches the release tag.
val appVersion = rootProject.file("VERSION").readText().trim()

android {
    namespace = "org.pharosvpn.caravel"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.pharosvpn.caravel"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = appVersion
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // The gomobile-built Go engine. Dropped into app/libs by build-bindings.sh.
    // Optional at configure time so the UI builds before the .aar lands; the
    // CoreBridge reflects into it at runtime and degrades gracefully if absent.
    compileOnly(files("libs/caravel.aar"))
    runtimeOnly(fileTree("libs") { include("*.aar") })

    debugImplementation(libs.androidx.ui.tooling)
}
