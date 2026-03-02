import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val ktorVersion: String by project

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "io.amplicode"

dependencies {
    testImplementation(project(":connekt-scripting-host"))
    testImplementation(project(":connekt-script-definition"))
    testImplementation("org.jetbrains.kotlin:kotlin-scripting-common")
    testImplementation("org.jetbrains.kotlin:kotlin-scripting-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host")

    testImplementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    testImplementation("io.ktor:ktor-network-tls-certificates:$ktorVersion")
    testImplementation("io.ktor:ktor-server-resources:$ktorVersion")
    testImplementation("org.bouncycastle:bcpkix-jdk15on:1.70")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.platform:junit-platform-launcher")
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
