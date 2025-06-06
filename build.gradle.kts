import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    kotlin("jvm") version "2.1.21"
    id("fabric-loom") version "1.10-SNAPSHOT"
    id("maven-publish")
    id("com.gradleup.shadow") version "8.3.6"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

val targetJavaVersion = 21
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
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

val mcVersion = project.findProperty("mc_version") as String? ?: "1_20"
val minecraftVersion = findProperty("minecraft_version_$mcVersion")?.toString()
    ?: throw GradleException("Minecraft version property for $mcVersion not found")
fun prop(ver: String, key: String) = project.property("${key}_$ver") as String

val loaderVersion = project.property("loader_version") as String
val kotlinLoaderVersion = project.property("kotlin_loader_version") as String

base {
    archivesName.set("${project.property("archives_base_name")}_${minecraftVersion}")
}
repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${prop(mcVersion, "minecraft_version")}")
    mappings("net.fabricmc:yarn:${prop(mcVersion, "yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc:fabric-language-kotlin:$kotlinLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop(mcVersion, "fabric_version")}")
    modImplementation("org.xerial:sqlite-jdbc:3.45.1.0")
    include("org.xerial:sqlite-jdbc:3.45.1.0")
}

tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName}" }
    }
}

tasks.processResources {
    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to prop(mcVersion, "minecraft_version"),
            "loader_version" to loaderVersion,
            "kotlin_loader_version" to kotlinLoaderVersion,
            "fabric_version" to prop(mcVersion, "fabric_version")
        )
    }
}

abstract class BuildAllWinTask : DefaultTask() {
    @get:Inject
    abstract val execOps: ExecOperations

    @TaskAction
    fun buildAll() {
        val supportedVersions: List<String> = (project.findProperty("supported_mc_versions") as String).split(",")
        supportedVersions.forEach { ver ->
            println("=== 正在编译 Minecraft $ver ===")
            execOps.exec {
                commandLine("gradlew.bat", "build", "-Pmc_version=$ver", "--stacktrace")
            }
        }
    }
}

abstract class BuildAllLinMacTask : DefaultTask() {
    @get:Inject
    abstract val execOps: ExecOperations

    @TaskAction
    fun buildAll() {
        val supportedVersions: List<String> = (project.findProperty("supported_mc_versions") as String).split(",")
        supportedVersions.forEach { ver ->
            println("=== 正在编译 Minecraft $ver ===")
            execOps.exec {
                commandLine("./gradlew", "build", "-Pmc_version=$ver", "--stacktrace")
            }
        }
    }
}

tasks.register<BuildAllWinTask>("buildAllWin") {
    group = "build"
    description = "为所有支持的Minecraft版本自动编译jar"
}

tasks.register<BuildAllLinMacTask>("buildAllLinMac") {
    group = "build"
    description = "为所有支持的Minecraft版本自动编译jar"
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}
