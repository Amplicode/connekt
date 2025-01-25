plugins {
    kotlin("jvm")
    distribution
    `maven-publish`
}

group = "com.amplicode"
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


distributions {
    main {
        distributionBaseName = "host"
        contents {
            into("/") {
                from(tasks.jar.get().archiveFile)
                from(configurations.runtimeClasspath) {
                    include("*.jar")
                }
            }
        }
    }
}

//tasks.register<Copy>("buildDistributionDir") {
//    into("${layout.buildDirectory.get()}/distribution")
//
//    from(tasks.jar.get().archiveFile)
//
//    from(configurations.runtimeClasspath) {
//        include("*.jar")
//    }
//}
//
//tasks.create<Zip>("zipDist") {
//    from("${layout.buildDirectory.get()}/distribution")
//    into("${layout.buildDirectory.get()}/dist.zip")
//}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    jvmToolchain(8)
}

publishing {
    publications {
        create<MavenPublication>("distribution") {
            artifactId = "connekt-host"
            version = "1.0-SNAPSHOT"
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
