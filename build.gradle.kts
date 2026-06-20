import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import javax.inject.Inject

plugins {
    kotlin("jvm") version "2.3.0"
    id("fabric-loom") version "1.14.7"
    id("maven-publish")
    id("com.gradleup.shadow") version "8.3.6"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

data class VersionGroup(
    val id: String,
    val buildKey: String,
    val overlay: String,
    val javaVersion: Int,
    val minecraftRange: String,
)

fun parseVersionGroups(): List<VersionGroup> {
    val ids = (project.property("version_groups") as String).split(",").map { it.trim() }.filter { it.isNotEmpty() }
    return ids.map { id ->
        val parts = (project.property("group.$id") as String).split(",").map { it.trim() }
        require(parts.size == 4) { "group.$id must have 4 comma-separated values" }
        VersionGroup(id, parts[0], parts[1], parts[2].toInt(), parts[3])
    }
}

val versionGroups = parseVersionGroups()
val activeGroupId = (project.findProperty("mc_group") as String?)
    ?: versionGroups.last().id
val activeGroup = versionGroups.firstOrNull { it.id == activeGroupId }
    ?: throw GradleException("Unknown mc_group '$activeGroupId'. Valid: ${versionGroups.joinToString { it.id }}")

fun prop(buildKey: String, key: String): String {
    val specific = project.findProperty("${key}_$buildKey")?.toString()
    if (!specific.isNullOrBlank()) return specific
    return project.property(key) as String
}

fun optionalProp(buildKey: String, key: String): String? {
    val specific = project.findProperty("${key}_$buildKey")?.toString()
    if (specific != null) return specific.ifBlank { null }
    return project.findProperty(key)?.toString()?.ifBlank { null }
}

val buildKey = activeGroup.buildKey
val minecraftVersion = prop(buildKey, "minecraft_version")
val yarnMappings = prop(buildKey, "yarn_mappings")
val loaderVersion = prop(buildKey, "loader_version")
val kotlinLoaderVersion = prop(buildKey, "kotlin_loader_version")
val fabricVersion = prop(buildKey, "fabric_version")
val fpaVersion = prop(buildKey, "fpa_version")
val sguiVersion = optionalProp(buildKey, "sgui_version")
val placeholderApiVersion = optionalProp(buildKey, "placeholder_api_version")
val serverTranslationsVersion = optionalProp(buildKey, "server_translations_version")

val targetJavaVersion = activeGroup.javaVersion
val toolchainJavaVersion = maxOf(21, targetJavaVersion)

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(toolchainJavaVersion))
    withSourcesJar()
}

val commonMain = file("src/common/main")
val commonClient = file("src/common/client")
val versionMain = file("src/versions/${activeGroup.overlay}/main")
val versionClient = file("src/versions/${activeGroup.overlay}/client")

fun configureVersionSourceSets() {
    sourceSets.named("main") {
        kotlin.srcDir(commonMain.resolve("kotlin"))
        resources.srcDir(commonMain.resolve("resources"))
        if (versionMain.resolve("kotlin").exists()) kotlin.srcDir(versionMain.resolve("kotlin"))
        if (versionMain.resolve("resources").exists()) resources.srcDir(versionMain.resolve("resources"))
    }
    sourceSets.named("client") {
        kotlin.srcDir(commonClient.resolve("kotlin"))
        resources.srcDir(commonClient.resolve("resources"))
        if (versionClient.resolve("kotlin").exists()) kotlin.srcDir(versionClient.resolve("kotlin"))
        if (versionClient.resolve("resources").exists()) resources.srcDir(versionClient.resolve("resources"))
    }
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

configureVersionSourceSets()

fabricApi {
    configureDataGeneration {
        client = true
    }
}

base {
    archivesName.set("${project.property("archives_base_name")}_${activeGroup.id}")
}

repositories {
    mavenCentral()
    maven("https://repo.lucko.me/")
    maven("https://maven.nucleoid.xyz/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc:fabric-language-kotlin:$kotlinLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")

    modImplementation("me.lucko:fabric-permissions-api:$fpaVersion")

    if (sguiVersion != null) {
        modImplementation("eu.pb4:sgui:$sguiVersion")
        include("eu.pb4:sgui:$sguiVersion")
    }

    if (placeholderApiVersion != null) {
        modImplementation("eu.pb4:placeholder-api:$placeholderApiVersion")
        include("eu.pb4:placeholder-api:$placeholderApiVersion")
    }

    if (serverTranslationsVersion != null) {
        modImplementation("xyz.nucleoid:server-translations-api:$serverTranslationsVersion")
        include("xyz.nucleoid:server-translations-api:$serverTranslationsVersion")
    }

    modImplementation("eu.pb4:common-economy-api:2.0.0")
    include("eu.pb4:common-economy-api:2.0.0")

    modImplementation("org.xerial:sqlite-jdbc:3.45.1.0")
    include("org.xerial:sqlite-jdbc:3.45.1.0")

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
        attributes(
            "Implementation-Version" to project.version,
            "Implementation-Minecraft-Range" to activeGroup.minecraftRange,
        )
    }

    from("LICENSE.txt") {
        rename { "${it}_${project.base.archivesName}" }
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", minecraftVersion)
    inputs.property("minecraft_range", activeGroup.minecraftRange)
    inputs.property("loader_version", loaderVersion)
    inputs.property("kotlin_loader_version", kotlinLoaderVersion)
    inputs.property("fabric_version", fabricVersion)
    inputs.property("has_placeholder_api", placeholderApiVersion != null)

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to minecraftVersion,
            "minecraft_range" to activeGroup.minecraftRange,
            "loader_version" to loaderVersion,
            "kotlin_loader_version" to kotlinLoaderVersion,
            "fabric_version" to fabricVersion,
            "has_placeholder_api" to (placeholderApiVersion != null).toString(),
        )
    }
}

abstract class BuildAllTask : DefaultTask() {
    @get:Inject
    abstract val execOps: ExecOperations

    @TaskAction
    fun buildAll() {
        val groups = (project.findProperty("version_groups") as String).split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val gradleCommand = if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "./gradlew"

        groups.forEach { groupId ->
            logger.lifecycle("=== Building version group: $groupId ===")
            execOps.exec {
                commandLine(gradleCommand, "build", "-Pmc_group=$groupId", "--stacktrace")
            }
        }

        execOps.exec {
            commandLine(gradleCommand, "deleteSourcesJar")
        }
    }
}

tasks.register<BuildAllTask>("buildAll") {
    group = "build"
    description = "Build JARs for all supported Minecraft version groups"
}

tasks.register<Delete>("deleteSourcesJar") {
    group = "build"
    description = "Remove generated *-sources.jar files from build/libs"
    delete(fileTree("build/libs") { include("**/*-sources.jar") })
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }
}
