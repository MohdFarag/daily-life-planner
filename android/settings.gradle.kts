pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
            }
        }
        gradlePluginPortal() // mirrors Maven Central; listed first because Central rate-limits shared CI IPs
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
            }
        }
        maven("https://plugins.gradle.org/m2/")
        mavenCentral()
    }
}

rootProject.name = "daily-life-planner"
include(":app")

// Platform-independent domain logic; also buildable alone with `./gradlew -p core test` (no Android SDK needed).
includeBuild("core")
