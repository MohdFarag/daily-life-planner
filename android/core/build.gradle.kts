plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

group = "app.dailylife"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    // Exposed in the public API (PrayerSettings uses CalculationMethod and Madhab).
    api(libs.adhan)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
