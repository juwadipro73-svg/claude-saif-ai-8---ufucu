import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Config values gradle.properties se aati hain, local.properties unhe override kar sakta hai.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// Priority: gradle.properties -> local.properties -> environment (CI) -> fallback
fun cfg(key: String, fallback: String = ""): String =
    (project.findProperty(key) as String?)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: fallback

android {
    namespace = "com.saif.ai.builder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.saif.ai.builder"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "GITHUB_OWNER", "\"${cfg("GITHUB_OWNER", "shahudgg")}\"")
        buildConfigField("String", "GITHUB_REPO", "\"${cfg("GITHUB_REPO", "SaifAI-Builder")}\"")
        buildConfigField("String", "GITHUB_WORKFLOW", "\"${cfg("GITHUB_WORKFLOW", "build.yml")}\"")
        buildConfigField("String", "GITHUB_BRANCH", "\"${cfg("GITHUB_BRANCH", "main")}\"")
        buildConfigField("String", "FIREBASE_DB_URL", "\"${cfg("FIREBASE_DB_URL")}\"")
        buildConfigField("String", "AD_GATE_URL", "\"${cfg("AD_GATE_URL")}\"")
        buildConfigField("String", "SAIF_UPLOAD_URL", "\"${cfg("SAIF_UPLOAD_URL")}\"")
        buildConfigField("String", "SAIF_API_KEY", "\"${cfg("SAIF_API_KEY")}\"")
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

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
