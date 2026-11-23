plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.gallerybridge.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.gallerybridge.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 82
        versionName = "0.82.0"
        manifestPlaceholders["appLabel"] = "Gallery Bridge"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // A persistent, user-owned keystore signs published .preview.signed APKs.
    // Older .preview.next/.preview.recovery packages were signed by rotating
    // CI debug keys. Their data must be migrated by explicit JSON backup/restore.
    val previewKeyFile = System.getenv("GB_PREVIEW_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (previewKeyFile != null) {
            create("previewPersistent") {
                storeFile = file(previewKeyFile)
                storePassword = System.getenv("GB_PREVIEW_STORE_PASSWORD")
                keyAlias = System.getenv("GB_PREVIEW_KEY_ALIAS")
                keyPassword = System.getenv("GB_PREVIEW_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }
    buildTypes {
        debug {
            // Debug APK is for CI instrumentation only and must never be published.
        }
        release {
            // Play Protect flagged the former distributable APK, built with assembleDebug.
            // Only a non-debuggable release variant can enter the signed preview channel.
            isDebuggable = false
            isMinifyEnabled = false
            if (providers.gradleProperty("galleryBridgePreview").orNull == "true") {
                applicationIdSuffix = ".preview.signed"
                manifestPlaceholders["appLabel"] = "Gallery Bridge Signed Preview 0.82"
                if (previewKeyFile != null) {
                    signingConfig = signingConfigs.getByName("previewPersistent")
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.21.2")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // Provides the test-only ComponentActivity used by createComposeRule().
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
