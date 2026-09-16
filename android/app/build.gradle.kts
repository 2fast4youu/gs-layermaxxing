import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun normalizedUrl(value: String) = value.let { if (it.endsWith("/")) it else "$it/" }
val gregorApiBaseUrl = normalizedUrl(
    localProperties.getProperty("layermaxxing.gregorApiBaseUrl")
        ?: localProperties.getProperty("layermaxxing.apiBaseUrl")
        ?: System.getenv("LAYERMAXXING_GREGOR_URL")
        ?: "https://example.invalid/"
)
val gerfriedApiBaseUrl = normalizedUrl(
    localProperties.getProperty("layermaxxing.gerfriedApiBaseUrl")
        ?: System.getenv("LAYERMAXXING_GERFRIED_URL")
        ?: "https://headless.tail586ff8.ts.net/layermaxxing/"
)

android {
    namespace = "at.gregor.layermaxxing"
    compileSdk = 36

    defaultConfig {
        applicationId = "at.gregor.layermaxxing.gerfried"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GREGOR_API_BASE_URL", "\"$gregorApiBaseUrl\"")
        buildConfigField("String", "GERFRIED_API_BASE_URL", "\"$gerfriedApiBaseUrl\"")
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
