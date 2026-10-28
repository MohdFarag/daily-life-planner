plugins {
    kotlin("jvm") version "2.4.0"
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
    implementation("com.batoulapps.adhan:adhan2:0.0.7")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
