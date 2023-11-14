plugins {
    kotlin("jvm") version "1.9.10"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.github.onlaait"
version = ""

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("libs/mcserverping-1.0.7.jar"))
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.21.1")
    implementation("com.github.ajalt.mordant:mordant:2.2.0")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
        manifest {
            attributes (
                "Main-Class" to "com.github.onlaait.mcserverpinger.MainKt",
                "Multi-Release" to true
            )
        }
    }

    build {
        dependsOn(shadowJar)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}