/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    application
    distribution
    `maven-publish`
}

group = "io.amplicode"
version = project.property("version_name")!!

dependencies {
    implementation(project(":connekt-script-definition"))

    implementation("org.jetbrains.kotlin:kotlin-scripting-common")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host")
    implementation("com.github.ajalt.clikt:clikt:5.0.1")

    // Implementation for slf4j to disable warning
    implementation("org.slf4j:slf4j-nop:2.0.7")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-core-jvm:3.0.2")
    testImplementation("io.ktor:ktor-server-netty-jvm:3.0.2")
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
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_1_8
}

application {
    mainClass = "io.amplicode.connekt.EvaluatorKt"
}

publishing {
    publications {
        create<MavenPublication>("distribution") {
            artifactId = "connekt-scripting-host"
            version = buildString {
                append(project.version)
                // Nexus accepts SNAPSHOT versions only
                if (!endsWith("-SNAPSHOT")) {
                    append("-SNAPSHOT")
                }
            }
            artifact(tasks.distZip)
        }
        val uploadUrl = project.findProperty("uploadUrl") as String?
        val uploadUser = project.findProperty("uploadUser") as String?
        val uploadPassword = project.findProperty("uploadPassword") as String?
        if (uploadUrl != null) {
            repositories {
                maven {
                    name = "Amplicode"
                    setUrl(uploadUrl)
                    credentials {
                        username = uploadUser
                        password = uploadPassword
                    }
                    isAllowInsecureProtocol = true
                }
            }
        }
    }
}
