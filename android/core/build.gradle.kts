plugins {
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
    implementation(libs.adhan)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
