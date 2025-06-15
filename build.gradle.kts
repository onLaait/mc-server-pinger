plugins {
    val kotlinVersion = "2.1.21"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    id("com.gradleup.shadow") version "8.3.6"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.24.3")
    implementation("com.github.ajalt.mordant:mordant:3.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("com.google.guava:guava:33.4.8-jre")

    val adventureVersion = "4.21.0"
    implementation("net.kyori:adventure-api:$adventureVersion")
    implementation("net.kyori:adventure-text-serializer-gson:$adventureVersion")
    implementation("net.kyori:adventure-text-serializer-legacy:$adventureVersion")
    implementation("net.kyori:adventure-text-serializer-plain:$adventureVersion")
}

tasks {
    build {
        dependsOn(shadowJar)
    }

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
}

kotlin {
    jvmToolchain(21)
}