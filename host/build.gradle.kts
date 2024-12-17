plugins {
    kotlin("jvm")
}

group = "io.amplicode"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":script-definition"))
    implementation("org.jetbrains.kotlin:kotlin-scripting-common")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host")
    testImplementation(kotlin("test"))
}

tasks.register<Copy>("buildDistributionDir") {
    into("${layout.buildDirectory.get()}/distribution")

    from(tasks.jar.get().archiveFile)

    from(configurations.runtimeClasspath) {
        include("*.jar")
    }
}

tasks.create<Zip>("zipDist") {
    from("${layout.buildDirectory.get()}/distribution")
    into("${layout.buildDirectory.get()}/dist.zip")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
