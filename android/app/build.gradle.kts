import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
val apiBaseUrl = (
    localProperties.getProperty("layermaxxing.apiBaseUrl")
        ?: System.getenv("LAYERMAXXING_API_BASE_URL")
        ?: "https://example.invalid/"
).let { if (it.endsWith("/")) it else "$it/" }

android {
    namespace = "at.gregor.layermaxxing"
    compileSdk = 36

    defaultConfig {
        applicationId = "at.gregor.layermaxxing"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "3.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.okhttp)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.biometric)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
