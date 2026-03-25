plugins {
    val kotlinVersion = "2.3.20"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    id("com.gradleup.shadow") version "9.4.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.25.3")
    implementation("com.github.ajalt.mordant:mordant:3.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("com.google.guava:guava:33.5.0-jre")

    val adventureVersion = "4.26.1"
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
                "Main-Class" to "com.github.onlaait.mcserverpingerimpl.MainKt",
                "Multi-Release" to true
            )
        }
    }
}

kotlin {
    jvmToolchain(25)
}