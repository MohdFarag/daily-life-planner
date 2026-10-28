pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.firebase.*")
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
                includeGroupByRegex("com\\.google\\.firebase.*")
            }
        }
        maven("https://plugins.gradle.org/m2/") // Maven Central mirror; Central rate-limits shared CI IPs
        mavenCentral()
    }
}

rootProject.name = "daily-life-planner"
include(":core")

// The Android app module needs the Android SDK. It is skipped where no SDK is installed
// (for example in CI containers that only run the core unit tests).
val sdkDir = System.getenv("ANDROID_HOME")
    ?: file("local.properties").takeIf { it.exists() }?.readLines()
        ?.firstOrNull { it.startsWith("sdk.dir=") }?.substringAfter("=")
if (sdkDir != null && file(sdkDir).exists()) include(":app")
