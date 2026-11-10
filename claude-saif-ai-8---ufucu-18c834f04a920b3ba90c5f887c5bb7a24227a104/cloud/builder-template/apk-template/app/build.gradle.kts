plugins { id("com.android.application") }
val appId = providers.gradleProperty("saif.generated.package").getOrElse("com.saifai.generated")
android {
    namespace = appId
    compileSdk = 35
    defaultConfig {
        applicationId = appId
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
}
