plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "net.dark.3smp"
version = "1.0.0"

description = "3SMPCore"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    compileOnly("me.clip:placeholderapi:2.11.6")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    runServer {
        minecraftVersion("1.21.11")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("plugin.yml") { expand(props) }
    }
}
