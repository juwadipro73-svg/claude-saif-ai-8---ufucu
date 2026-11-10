pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // CI verifies the official SHA-256 of this large Kotlin compiler JAR before
        // using a temporary local Maven repo. Maven Central redirected it to a GitHub
        // release asset returning HTTP 504 on Actions; normal developer builds use Central.
        System.getenv("SAIF_VERIFIED_MAVEN_DIR")?.takeIf { it.isNotBlank() }?.let { verifiedDir ->
            maven {
                url = uri(verifiedDir)
                content { includeModule("org.jetbrains.kotlin", "kotlin-compiler-embeddable") }
                metadataSources {
                    mavenPom()
                    ignoreGradleMetadataRedirection()
                }
            }
        }
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        maven("https://jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "SAIF AI"
include(":app")
include(":automation")
include(":apprunner")
