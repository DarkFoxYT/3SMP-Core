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
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("org.apache.logging.log4j:log4j-api:2.24.1")
    compileOnly("org.apache.logging.log4j:log4j-core:2.24.1")
    compileOnly(files("C:/Users/darkf/Desktop/server stuff/.paper-remapped/MythicMobs-5.11.2.jar"))
    compileOnly(files("C:/Users/darkf/Desktop/server stuff/.paper-remapped/ItemsAdder_4.0.16.jar"))
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.13")
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
