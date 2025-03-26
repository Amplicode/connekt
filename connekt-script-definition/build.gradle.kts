import org.jetbrains.kotlin.gradle.dsl.JvmTarget

@Suppress("PropertyName")
val ktor_version: String by project

plugins {
    kotlin("jvm") version "2.0.21"
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-scripting-common")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm")
    implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies")
    implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies-maven")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    implementation("com.squareup.okhttp3:okhttp:4.9.2")
    implementation("com.jayway.jsonpath:json-path:2.9.0")
    implementation("org.assertj:assertj-core:3.24.2")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.1")
    api("org.mapdb:mapdb:3.1.0")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    testImplementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    testImplementation("io.ktor:ktor-network-tls-certificates:$ktor_version")
    testImplementation("org.bouncycastle:bcpkix-jdk15on:1.70")
    testImplementation("org.junit.platform:junit-platform-launcher")
}

// Copy sources into the jar to attach them in IDE
tasks.jar {
    from(sourceSets["main"].allSource)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}