plugins {
    kotlin("jvm") version "2.3.0"
    application
}

group = "com.kotlin"
version = "1.0-SNAPSHOT"

application {
    mainClass = "com.kotlin.coroutine.MainKt"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}