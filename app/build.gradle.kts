plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization") version "1.9.0"
}

android {
    namespace = "com.example.wearosapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.wearosapp"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

    }

    packaging {
        resources {
            pickFirsts += listOf(
                "META-INF/io.netty.versions.properties",
                "META-INF/INDEX.LIST",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/ASL2.0"
            )
        }
    }



    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.netty.codec.http)
    implementation(libs.hivemq.mqtt.client)
    implementation(libs.kotlinx.serialization.json)

    // Core Ktor client
    implementation(libs.ktor.client.core)

    // OkHttp engine
    implementation(libs.ktor.client.okhttp)

    // Content negotiation plugin
    implementation(libs.ktor.client.content.negotiation)

    // Kotlinx JSON serialization support
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.koin.android)

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")



    implementation(libs.play.services.wearable)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material)
    implementation(libs.compose.foundation)
    implementation(libs.wear.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    implementation(libs.material3.android)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}