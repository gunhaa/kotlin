plugins {
    kotlin("jvm") version "2.3.0"
    application
}

group = "com.kotlin"
version = "1.0-SNAPSHOT"

val coroutinesVersion = "1.11.0"
val turbineVersion = "1.2.1"

application {
    mainClass = "com.kotlin.coroutine.MainKt"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("app.cash.turbine:turbine:$turbineVersion")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
    // 워크북 skeleton은 "원본 보관용"이라 실행하지 않는다. 푸는 곳은 answer 쪽이다.
    filter {
        excludeTestsMatching("com.kotlin.workbook.skeleton.*")
        isFailOnNoMatchingTests = false
    }
}

// 워크북 태스크(`./gradlew grade`, `./gradlew resetAnswer`)
apply(from = "gradle/workbook.gradle.kts")
