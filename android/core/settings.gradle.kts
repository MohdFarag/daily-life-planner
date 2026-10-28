pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://plugins.gradle.org/m2/") // Maven Central mirror; Central rate-limits shared CI IPs
        mavenCentral()
    }
}

rootProject.name = "core"
