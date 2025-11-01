import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    kotlin("jvm") version "2.1.21"
    id("fabric-loom") version "1.11.7"
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

// Prefer the latest supported MC version by default to avoid dependency mismatches
val defaultMcVersion: String = (project.findProperty("supported_mc_versions") as String?)
    ?.split(",")
    ?.last()
    ?.trim()
    ?: "1_21_6"
val mcVersion = project.findProperty("mc_version") as String? ?: defaultMcVersion
val minecraftVersion = findProperty("minecraft_version_$mcVersion")?.toString()
    ?: throw GradleException("Minecraft version property for $mcVersion not found")
fun prop(ver: String, key: String) = project.property("${key}_$ver") as String

val loaderVersion = project.property("loader_version") as String
val kotlinLoaderVersion = project.property("kotlin_loader_version") as String

base {
    archivesName.set("${project.property("archives_base_name")}_${minecraftVersion}")
}
repositories {
    // 默认仓库由 loom 添加，这里保持空即可，必要时可添加自定义仓库
    mavenCentral()
    // Lucko 仓库（发布 fabric-permissions-api）
    maven("https://repo.lucko.me/")
    // Nucleoid 仓库（发布 sgui）
    maven("https://maven.nucleoid.xyz/")
    // Sonatype snapshots（如果使用快照版本时需要）
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${prop(mcVersion, "minecraft_version")}")
    mappings("net.fabricmc:yarn:${prop(mcVersion, "yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc:fabric-language-kotlin:$kotlinLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop(mcVersion, "fabric_version")}")

    // 引入并打包 fabric-permissions-api 到本模组内（无需服务器单独安装）
    val fpaVersion = prop(mcVersion, "fpa_version")
    modImplementation("me.lucko:fabric-permissions-api:$fpaVersion")
    include("me.lucko:fabric-permissions-api:$fpaVersion")

    // SGUI 库 - 服务端 GUI 库（1.21.3 不支持）
    if (mcVersion != "1_21_3") {
        val sguiVersion = prop(mcVersion, "sgui_version")
        modImplementation("eu.pb4:sgui:$sguiVersion")
        include("eu.pb4:sgui:$sguiVersion")
    }

    // SQLite 驱动
    modImplementation("org.xerial:sqlite-jdbc:3.45.1.0")
    include("org.xerial:sqlite-jdbc:3.45.1.0")

    // MySQL 驱动（可选，根据配置是否使用）。打包进 jar 以便服务器无需额外放置
    implementation("com.mysql:mysql-connector-j:9.4.0")
    include("com.mysql:mysql-connector-j:9.4.0")
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
    inputs.property("version", project.version)
    inputs.property("minecraft_version", prop(mcVersion, "minecraft_version"))
    inputs.property("loader_version", loaderVersion)
    inputs.property("kotlin_loader_version", kotlinLoaderVersion)
    inputs.property("fabric_version", prop(mcVersion, "fabric_version"))

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

abstract class BuildAllTask : DefaultTask() {
    @get:Inject
    abstract val execOps: ExecOperations

    @TaskAction
    fun buildAll() {
        val supportedVersions: List<String> = (project.findProperty("supported_mc_versions") as String).split(",")
        val gradleCommand = getGradleCommand()

        // 构建所有支持的Minecraft版本
        supportedVersions.forEach { ver ->
            println("=== 正在编译 Minecraft $ver ===")
            execOps.exec {
                commandLine(gradleCommand, "build", "-Pmc_version=$ver", "--stacktrace")
            }
        }

        // 删除源文件
        execOps.exec {
            commandLine(gradleCommand, "deleteSourcesJar", "--stacktrace")
        }
    }

    private fun getGradleCommand(): String {
        // 根据操作系统选择正确的命令
        return if (System.getProperty("os.name").lowercase().contains("win")) {
            "gradlew.bat"
        } else {
            "./gradlew"
        }
    }
}

tasks.register<BuildAllTask>("buildAll") {
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

tasks.register<Delete>("deleteSourcesJar") {
    group = "build"
    description = "删除所有版本的 source.jar 文件"
    val sourceJarFiles = fileTree("build/libs") {
        include("**/*-sources.jar")
    }
    delete(sourceJarFiles)
}