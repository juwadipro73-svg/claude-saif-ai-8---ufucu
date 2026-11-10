plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.saif.apprunner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.saif.apprunner"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            isCrunchPngs = false
        }
        release {
            isMinifyEnabled = false
            isCrunchPngs = false
        }
    }

    androidResources {
        noCompress += listOf("png", "html", "json")
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
