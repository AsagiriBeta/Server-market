import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.0"
    id("fabric-loom") version "1.14.7"
    id("maven-publish")
    id("com.gradleup.shadow") version "8.3.6"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

val targetJavaVersion = 21
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    withSourcesJar()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("server-market") {
            sourceSet("main")
            sourceSet("client")
        }
    }
}

fabricApi {
    configureDataGeneration {
        client = true
    }
}

val minecraftVersion = project.property("minecraft_version") as String
val yarnMappings = project.property("yarn_mappings") as String
val loaderVersion = project.property("loader_version") as String
val kotlinLoaderVersion = project.property("kotlin_loader_version") as String
val fabricVersion = project.property("fabric_version") as String
val fpaVersion = project.property("fpa_version") as String
val sguiVersion = project.property("sgui_version") as String
val placeholderApiVersion = project.property("placeholder_api_version") as String

base {
    archivesName.set("${project.property("archives_base_name")}_${minecraftVersion}")
}

repositories {
    mavenCentral()
    // Lucko (fabric-permissions-api)
    maven("https://repo.lucko.me/")
    // Nucleoid (sgui)
    maven("https://maven.nucleoid.xyz/")
    // Sonatype snapshots (only needed when using snapshot deps)
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc:fabric-language-kotlin:$kotlinLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")

    // fabric-permissions-api (do NOT include; server should install it separately)
    modImplementation("me.lucko:fabric-permissions-api:$fpaVersion")

    // SGUI (included)
    modImplementation("eu.pb4:sgui:$sguiVersion")
    include("eu.pb4:sgui:$sguiVersion")

    // Placeholder API (included)
    modImplementation("eu.pb4:placeholder-api:$placeholderApiVersion")
    include("eu.pb4:placeholder-api:$placeholderApiVersion")

    // SQLite
    modImplementation("org.xerial:sqlite-jdbc:3.45.1.0")
    include("org.xerial:sqlite-jdbc:3.45.1.0")

    // MySQL (included)
    implementation("com.mysql:mysql-connector-j:9.4.0")
    include("com.mysql:mysql-connector-j:9.4.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    manifest {
        attributes("Implementation-Version" to project.version)
    }

    from("LICENSE") {
        rename { "${it}_${project.base.archivesName}" }
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", minecraftVersion)
    inputs.property("loader_version", loaderVersion)
    inputs.property("kotlin_loader_version", kotlinLoaderVersion)
    inputs.property("fabric_version", fabricVersion)

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to minecraftVersion,
            "loader_version" to loaderVersion,
            "kotlin_loader_version" to kotlinLoaderVersion,
            "fabric_version" to fabricVersion
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    repositories {
        // Configure publish repositories here.
    }
}
