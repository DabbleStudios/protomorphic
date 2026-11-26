package studio.dabble.protomorphic.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

/**
 * Runs protoc with protoc-gen-protomorphic-kotlin on every .proto file under [sources], resolving imports
 * from [includes], the well-known protos and protomorphic/options.proto, and writes the Kotlin to
 * [outputDir]. Schema rule violations reported by the plugin fail the task with protoc's message.
 */
@CacheableTask
abstract class GenerateProtomorphicKotlin @Inject constructor(
    private val exec: ExecOperations,
    private val fileSystem: FileSystemOperations,
    private val archives: ArchiveOperations,
) : DefaultTask() {

    /** The protoc executable (a single file, from Maven). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val protoc: ConfigurableFileCollection

    /** The protobuf-java jar, whose google/protobuf `.proto` files are put on the proto path. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val wellKnownProtos: ConfigurableFileCollection

    /** protoc-gen-protomorphic-kotlin and its dependencies. */
    @get:Classpath
    abstract val generatorClasspath: ConfigurableFileCollection

    /** This module's proto root. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: DirectoryProperty

    /** Proto roots of dependencies; read for imports, not generated. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val includes: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        fileSystem.delete { delete(out) }
        out.mkdirs()

        val root = sources.get().asFile
        val files = root.walkTopDown().filter { it.isFile && it.extension == "proto" }
            .map { it.relativeTo(root).invariantSeparatorsPath }.sorted().toList()
        if (files.isEmpty()) return

        val work = temporaryDir
        val protoPath = work.resolve("proto-path").also { dir -> fileSystem.delete { delete(dir) } }
        fileSystem.copy {
            from(archives.zipTree(wellKnownProtos.singleFile)) { include("google/protobuf/*.proto") }
            into(protoPath)
        }
        protoPath.resolve(OPTIONS).apply { parentFile.mkdirs() }
            .writeText(javaClass.getResource("/$OPTIONS")!!.readText())

        val protocExe = work.resolve("protoc").also {
            protoc.singleFile.copyTo(it, overwrite = true)
            it.setExecutable(true)
        }
        val plugin = work.resolve("protoc-gen-protomorphic-kotlin").also { script ->
            val java = File(System.getProperty("java.home"), "bin/java").absolutePath
            val classpath = generatorClasspath.files.joinToString(File.pathSeparator) { it.absolutePath }
            script.writeText("#!/bin/sh\nexec \"$java\" -cp \"$classpath\" $MAIN_CLASS \"\$@\"\n")
            script.setExecutable(true)
        }

        exec.exec {
            commandLine(
                buildList {
                    add(protocExe.absolutePath)
                    add("--plugin=protoc-gen-protomorphic-kotlin=${plugin.absolutePath}")
                    add("--protomorphic-kotlin_out=${out.absolutePath}")
                    add("-I${root.absolutePath}")
                    includes.files.forEach { add("-I${it.absolutePath}") }
                    add("-I${protoPath.absolutePath}")
                    addAll(files)
                },
            )
            workingDir = root
        }
    }

    private companion object {
        const val OPTIONS = "protomorphic/options.proto"
        const val MAIN_CLASS = "studio.dabble.protomorphic.kotlin.MainKt"
    }
}
