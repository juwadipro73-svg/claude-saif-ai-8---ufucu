import groovy.json.JsonSlurper
import org.gradle.api.tasks.testing.Test

val versionFile = rootProject.file("version.json")
val parsedVersion = if (versionFile.exists()) {
    @Suppress("UNCHECKED_CAST")
    JsonSlurper().parseText(versionFile.readText()) as Map<String, Any>
} else {
    mapOf("version_code" to 1, "version_name" to "1.0.0")
}

val baseVersionCode = (parsedVersion["version_code"] as? Number)?.toInt() ?: 1
val baseVersionName = (parsedVersion["version_name"] as? String) ?: "1.0.0"

// CI stamp: every GitHub Actions run gets a higher versionCode and a visible "build #N (sha)" label, so an
// old APK can never be mistaken for the new one and Android always treats the new APK as an update.
val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val ciSha = (System.getenv("GITHUB_SHA") ?: "").take(7)
val appVersionCode = if (ciRunNumber != null) maxOf(baseVersionCode, 1000 + ciRunNumber) else baseVersionCode
val appVersionName = if (ciRunNumber != null) "$baseVersionName-b$ciRunNumber" else baseVersionName
val buildStamp = if (ciRunNumber != null) "build #$ciRunNumber ($ciSha)" else "local build"

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.wire)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.gemini"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.saifai.app"
        minSdk = 26
        targetSdk = 28
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "BUILD_STAMP", "\"$buildStamp\"")

        // Optional platform keys (read from env / .env; empty when not provided)
        buildConfigField("String", "GEMINI_API_KEY", "\"${System.getenv("GEMINI_API_KEY") ?: ""}\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"${System.getenv("OPENROUTER_API_KEY") ?: ""}\"")
        // Public endpoint URLs only. Never embed a GitHub token or Drive credentials in the APK.
        listOf("SAIF_CLOUD_API_BASE_URL", "SAIF_DOWNLOAD_PORTAL_URL", "SAIF_FIREBASE_DATABASE_URL")
            .forEach { key ->
                val value = System.getenv(key).orEmpty().trim().trimEnd('/')
                require(value.isEmpty() || (value.startsWith("https://") && '"' !in value && '\\' !in value)) {
                    "$key must be an HTTPS URL"
                }
                buildConfigField("String", key, "\"$value\"")
            }

        // Telegram asset library (set as GitHub Actions secrets; empty when not provided)
        buildConfigField("String", "TG_BOT_TOKEN", "\"${System.getenv("TG_BOT_TOKEN") ?: ""}\"")
        buildConfigField("String", "TG_CHAT_ID", "\"${System.getenv("TG_CHAT_ID") ?: ""}\"")

        // GPL v3 compliance: the complete corresponding source for THIS build must be published at
        // this address (see GPL_SHIP_CHECKLIST.md). Point it at the exact commit being shipped.
        val sourceUrl = System.getenv("SAIF_SOURCE_URL").orEmpty().trim().trimEnd('/')
        require(sourceUrl.isEmpty() || (sourceUrl.startsWith("https://") && '"' !in sourceUrl && '\\' !in sourceUrl)) {
            "SAIF_SOURCE_URL must be an HTTPS URL"
        }
        buildConfigField("String", "SAIF_SOURCE_URL", "\"$sourceUrl\"")
    }

    signingConfigs {
        // Fixed debug key kept in the repo: GitHub runners otherwise generate a NEW random debug key on every
        // build, so Android refuses to install a new APK over the old one ("App not installed") and the phone
        // silently keeps showing the old version.
        getByName("debug") {
            storeFile = file("saif-debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = file("release.jks")
            storePassword = "123456"
            keyAlias = "antigem"
            keyPassword = "123456"
        }
    }

    flavorDimensions += "mode"
    productFlavors {
        // Local ports differ per app so com.antigem and com.termux can be installed and run side by side
        // (com.antigem: +2, so its bridge port never equals com.termux's hub port).
        create("standard") {
            dimension = "mode"
            applicationId = "com.saifai.app"
            buildConfigField("int", "BRIDGE_PORT", "1236")
            buildConfigField("int", "HUB_PORT", "1237")
            buildConfigField("int", "BROWSER_MCP_PORT", "8767")
        }
    }

    buildTypes {
        debug {
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    base {
        archivesName.set("app")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
        disable += "ExpiredTargetSdkVersion"
    }

    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    androidResources {
        noCompress += listOf("gz", "tgz", "tar.gz", "tar", "apk")
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
        excludes += "*.xsd"
        excludes += "**/*.xsd"
        excludes += "META-INF/INDEX.LIST"
        excludes += "META-INF/io.netty.versions.properties"
        excludes += "META-INF/DEPENDENCIES"
        excludes += "META-INF/LICENSE*"
        excludes += "META-INF/NOTICE*"
      }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true // Robolectric tests read app labels and manifest resources.
    }
}

kotlin {
    jvmToolchain(21)
}

// Robolectric accesses JDK FileDescriptor internals when loading Android 16. Without this
// export every Robolectric test fails before its first assertion on current JDKs.
tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2/")
    systemProperty("robolectric.dependency.repo.id", "mavenCentralMirror")
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED"
    )
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
  implementation("androidx.work:work-runtime-ktx:2.10.0")

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Networking and Serialization
  implementation(libs.okhttp)
  implementation(libs.okhttp.sse)
  implementation(libs.androidx.webkit)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.datastore.preferences)

  // Image Loading (Coil for Markdown Images and IDE Asset Viewer)
  implementation("io.coil-kt:coil-compose:2.7.0")
  implementation("io.coil-kt:coil-svg:2.7.0")

  // Native Android JLaTeXMath / LaTeX Renderer (Pure Android Canvas, Zero WebViews)
  implementation("io.noties.markwon:ext-latex:4.6.2")
  implementation("ru.noties:jlatexmath-android:0.2.0")

  // Pure Java/Kotlin SSH Client (Persistent Termux SSH connection pool)
  implementation("com.github.mwiede:jsch:0.2.20")

  // HTML Parser & Web Content Scraper (Free Web Search & Webpage Reader)
  implementation("org.jsoup:jsoup:1.18.3")

  // Sora Editor (Industry standard Android Code Editor)
  implementation("io.github.Rosemoe.sora-editor:editor:0.23.6")
  implementation("io.github.Rosemoe.sora-editor:language-textmate:0.23.6")
  implementation("io.github.Rosemoe.sora-editor:language-java:0.23.6")

  // Archive and Compression Utilities (Supports tar, xz, ar, deb extraction)
  implementation("org.apache.commons:commons-compress:1.26.1")
  implementation("org.tukaani:xz:1.9")

  // Termux Terminal Emulator with Native PTY Support (Terminal View is compiled from source)
  implementation("com.github.termux.termux-app:terminal-emulator:v0.118.1")
  implementation("com.google.guava:listenablefuture:1.0")

  // Local tests: JVM, Robolectric, Compose screenshot tests and coroutines.
  testImplementation(libs.junit)
  // Plain JVM tests also exercise org.json parsers; Android's stub JSON classes return defaults.
  testImplementation("org.json:json:20240303")
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation("org.robolectric:robolectric:4.17")
  testImplementation("io.github.takahirom.roborazzi:roborazzi:1.77.0")
  testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.77.0")

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Wire Protobuf & gRPC runtime
  implementation(libs.wire.runtime)
  implementation(libs.wire.grpc.client)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // ---- SAIF AI core (Room, Firebase, Media3, Automation module) ----
  implementation(project(":automation"))
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation("com.google.firebase:firebase-database")
  implementation("com.google.firebase:firebase-appcheck-playintegrity")
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.ui)
  implementation(libs.media3.common)
  implementation(libs.apksig) // on-device APK signing (compiler/AppCompilerEngine.kt)
  implementation(libs.kotlinx.coroutines.android)
  implementation("androidx.security:security-crypto:1.1.0-alpha06")
}

configurations.all {
  resolutionStrategy {
    force("com.squareup.okhttp3:okhttp:4.12.0")
    force("com.squareup.okhttp3:okhttp-android:4.12.0")
  }
}

wire {
  sourcePath {
    srcDir("src/main/proto")
  }
  kotlin {
    rpcRole = "client"
    rpcCallStyle = "suspending"
  }
}

val buildAgyIdeBridge = tasks.register<Exec>("buildAgyIdeBridge") {
  val binDir = layout.projectDirectory.dir("src/main/assets/bin")
  val bridgeSourceDir = layout.projectDirectory.dir("../agy_ide_bridge")

  doFirst {
    binDir.asFile.mkdirs()
  }

  workingDir = bridgeSourceDir.asFile
  environment("GOOS", "android")
  environment("GOARCH", "arm64")
  environment("CGO_ENABLED", "0")
  commandLine("go", "build", "-ldflags=-s -w", "-o", binDir.file("agy_ide_bridge").asFile.absolutePath, "main.go")
}

val copyScriptsToAssets = tasks.register<Copy>("copyScriptsToAssets") {
  from(layout.projectDirectory.file("../scripts/backup_bootstrap.sh")) {
    rename { "backup_bootstrap" }
  }
  from(layout.projectDirectory.file("../scripts/backup_chats.sh")) {
    rename { "backup_chats" }
  }
  from(layout.projectDirectory.file("../scripts/restore_chats.sh")) {
    rename { "restore_chats" }
  }
  from(layout.projectDirectory.file("../scripts/backup_projects.sh")) {
    rename { "backup_projects" }
  }
  from(layout.projectDirectory.file("../scripts/restore_projects.sh")) {
    rename { "restore_projects" }
  }
  from(layout.projectDirectory.file("../scripts/backup_proot_ubuntu.sh")) {
    rename { "backup_proot_ubuntu" }
  }
  from(layout.projectDirectory.file("../scripts/git_clone.sh")) {
    rename { "git_clone" }
  }
  into(layout.projectDirectory.dir("src/main/assets/bin"))
}

tasks.named("preBuild") {
  dependsOn(buildAgyIdeBridge, copyScriptsToAssets)
}




// Bundle built-in extensions. The app-owned runtime lives in src/main/assets/browser-runtime.
val bundleBrowserExtensions by tasks.registering(Sync::class) {
    from(rootProject.file("extensions")) {
        exclude("**/test/**", "**/node_modules/**", "**/.storage/**", "**/.antigem-state/**")
    }
    into(layout.buildDirectory.dir("generated/browserExtensionAssets/browser-extensions"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/browserExtensionAssets").get().asFile)
tasks.named("preBuild").configure { dependsOn(bundleBrowserExtensions) }

// Documentation is served by the developer MCP; models do not need the app source tree.
val bundleExtensionDocs by tasks.registering(Sync::class) {
    from(rootProject.file("extension_docs"))
    into(layout.buildDirectory.dir("generated/extensionDocAssets/extension-docs"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/extensionDocAssets").get().asFile)
tasks.named("preBuild").configure { dependsOn(bundleExtensionDocs) }

// Real Android WebView APK container template used by AppCompilerEngine for on-device APK compilation.
val bundleBaseRunnerApk by tasks.registering(Sync::class) {
    dependsOn(":apprunner:assembleDebug")
    from(project(":apprunner").layout.buildDirectory.dir("outputs/apk/debug")) {
        include("*.apk")
        exclude("*unaligned*")
        rename { "base_runner.apk" }
    }
    into(layout.buildDirectory.dir("generated/baseRunnerAssets"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/baseRunnerAssets").get().asFile)
tasks.named("preBuild").configure { dependsOn(bundleBaseRunnerApk) }
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") || it.name.startsWith("generate") && it.name.endsWith("Assets") }
    .configureEach { dependsOn(bundleBaseRunnerApk) }

