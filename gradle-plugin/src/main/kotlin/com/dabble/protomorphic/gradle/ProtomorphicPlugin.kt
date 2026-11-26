package com.dabble.protomorphic.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.util.Properties

/**
 * `com.dabble.protomorphic`: generates Kotlin from a module's protos with protoc and
 * protoc-gen-protomorphic-kotlin, both resolved as dependencies, so nothing needs installing.
 *
 * For Kotlin Multiplatform modules the generated code goes into `commonMain`; for Kotlin/JVM modules
 * into `main`. Either way the module gets an `api` dependency on the protomorphic runtime.
 */
class ProtomorphicPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = with(project) {
        val versions = Properties().apply {
            ProtomorphicPlugin::class.java.getResourceAsStream("/protomorphic/versions.properties")!!.use(::load)
        }
        val version = versions.getProperty("version")
        val protobuf = versions.getProperty("protobuf")

        val extension = extensions.create("protomorphic", ProtomorphicExtension::class.java)
        extension.sourceDir.convention(layout.projectDirectory.dir("src/commonMain/proto"))
        extension.useLocalProjects.convention(false)

        fun artifact(name: String): Provider<Dependency> = extension.useLocalProjects.map { local ->
            if (local) dependencies.project(mapOf("path" to ":$name")) else dependencies.create("$GROUP:$name:$version")
        }

        val protoc = configurations.create("protomorphicProtoc") {
            isCanBeConsumed = false
            isTransitive = false
        }
        dependencies.add(protoc.name, "com.google.protobuf:protoc:$protobuf:${protocClassifier()}@exe")

        // protobuf-java ships the well-known protos (any.proto, descriptor.proto, ...).
        val wellKnown = configurations.create("protomorphicWellKnownProtos") {
            isCanBeConsumed = false
            isTransitive = false
        }
        dependencies.add(wellKnown.name, "com.google.protobuf:protobuf-java:$protobuf")

        val generator = configurations.create("protomorphicGenerator") {
            isCanBeConsumed = false
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, LibraryElements.JAR))
            }
            dependencies.addLater(artifact("protoc-gen-protomorphic-kotlin"))
        }

        val generate = tasks.register("generateProtomorphicKotlin", GenerateProtomorphicKotlin::class.java) {
            group = "build"
            description = "Generates Kotlin from this module's protos with protoc-gen-protomorphic-kotlin."
            this.protoc.from(protoc)
            wellKnownProtos.from(wellKnown)
            generatorClasspath.from(generator)
            sources.set(extension.sourceDir)
            includes.from(extension.includes)
            outputDir.set(layout.buildDirectory.dir("generated/protomorphic"))
        }

        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            extensions.getByType(KotlinMultiplatformExtension::class.java).sourceSets.getByName("commonMain").kotlin.srcDir(generate)
            configurations.named("commonMainApi") { dependencies.addLater(artifact("runtime")) }
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            extension.sourceDir.convention(layout.projectDirectory.dir("src/main/proto"))
            extensions.getByType(KotlinJvmProjectExtension::class.java).sourceSets.getByName("main").kotlin.srcDir(generate)
            configurations.named("api") { dependencies.addLater(artifact("runtime")) }
        }
    }

    private fun protocClassifier(): String {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val osPart = when {
            "mac" in os -> "osx"
            "linux" in os -> "linux"
            "windows" in os -> "windows"
            else -> error("protoc is not published for $os")
        }
        val archPart = if (arch == "aarch64" || arch == "arm64") "aarch_64" else "x86_64"
        return "$osPart-$archPart"
    }

    private companion object {
        const val GROUP = "com.dabble.protomorphic"
    }
}
