/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun versionCodeOf(version: String): Int {
    val match = requireNotNull(Regex("""(\d{1,2})\.(\d{1,3})\.(\d{1,3})""").matchEntire(version)) {
        "JAUGE_VERSION doit être vX.Y.Z, reçu : $version"
    }
    val (major, minor, patch) = match.destructured
    return major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt()
}

android {
    namespace = "fr.sygix.jauge"
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.sygix.jauge"
        minSdk = 31
        targetSdk = 37
        val ciVersion = System.getenv("JAUGE_VERSION")?.removePrefix("v")
        versionCode = ciVersion?.let(::versionCodeOf) ?: 1
        versionName = ciVersion ?: "0.1.0"
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("JAUGE_STORE_FILE")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = System.getenv("JAUGE_STORE_PASSWORD")
                keyAlias = System.getenv("JAUGE_KEY_ALIAS")
                keyPassword = System.getenv("JAUGE_KEY_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.datastore.preferences)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.work.runtime)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "1536m"
    systemProperty("jauge.previews", layout.buildDirectory.dir("widget-previews").get().asFile.absolutePath)
    jvmArgs(
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
    )
}
