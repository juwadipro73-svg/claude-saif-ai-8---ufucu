pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // The worker checks this exact compiler JAR against JetBrains' SHA-256
        // before exposing its temporary Maven repository to the build.
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
        google()
        mavenCentral()
    }
}
rootProject.name = "SaifCloudArtifact"
include(":app")
