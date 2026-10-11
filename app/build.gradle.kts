plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.lumen.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lumen.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "GEMINI_API_KEY", "\"\"")
    }

    // Supply upload-key credentials in the build environment; never commit them.
    val uploadKeyPath = providers.environmentVariable("LUMEN_UPLOAD_KEYSTORE").orNull
    val uploadKeyAlias = providers.environmentVariable("LUMEN_UPLOAD_KEY_ALIAS").orNull
    val uploadStorePassword = providers.environmentVariable("LUMEN_UPLOAD_STORE_PASSWORD").orNull
    val uploadKeyPassword = providers.environmentVariable("LUMEN_UPLOAD_KEY_PASSWORD").orNull
    val uploadValues = listOf(uploadKeyPath, uploadKeyAlias, uploadStorePassword, uploadKeyPassword)
    require(uploadValues.all { it.isNullOrBlank() } || uploadValues.all { !it.isNullOrBlank() }) {
        "Provide all four LUMEN_UPLOAD_* signing variables, or leave all unset for an unsigned release."
    }
    if (!uploadKeyPath.isNullOrBlank()) {
        signingConfigs.create("upload") {
            storeFile = file(uploadKeyPath)
            storePassword = uploadStorePassword
            keyAlias = uploadKeyAlias
            keyPassword = uploadKeyPassword
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("upload")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")

    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("org.json:json:20240303")
}

// Run the phrase pack and existing unit tests before producing either APK variant.
tasks.matching { it.name in setOf("assembleDebug", "assembleRelease", "bundleDebug", "bundleRelease") }.configureEach {
    dependsOn("testDebugUnitTest")
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    testLogging.showStandardStreams = true
}
