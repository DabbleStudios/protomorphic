package studio.dabble.protomorphic.rules

import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import java.io.File
import java.nio.file.Files

/**
 * Runs the real protoc on small schemas and builds the CodeGeneratorRequest protoc would pass a
 * plugin. Needs the `protoc` and `optionsProtos` system properties (set by the build).
 */
object Protoc {
    private val protoc = File(System.getProperty("protoc"))
    private val optionsProtos = File(System.getProperty("optionsProtos"))

    /** Compiles [files] (path to content) and requests generation of [toGenerate] (default: all of them). */
    fun request(files: Map<String, String>, toGenerate: List<String> = files.keys.toList()): CodeGeneratorRequest {
        val root = Files.createTempDirectory("protomorphic-schema").toFile()
        files.forEach { (path, content) -> root.resolve(path).apply { parentFile.mkdirs() }.writeText(content) }
        val wellKnown = root.resolve("_well_known").also { dir ->
            listOf("descriptor", "any").forEach { name ->
                val resource = "google/protobuf/$name.proto"
                dir.resolve(resource).apply { parentFile.mkdirs() }
                    .writeText(Protoc::class.java.classLoader.getResource(resource)!!.readText())
            }
        }
        val descriptors = root.resolve("descriptors.pb")
        val process = ProcessBuilder(
            listOf(
                protoc.absolutePath, "--include_imports", "--include_source_info",
                "--descriptor_set_out=$descriptors",
                "-I$root", "-I$optionsProtos", "-I$wellKnown",
            ) + toGenerate,
        ).directory(root).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "protoc failed:\n$output" }
        val set = FileDescriptorSet.parseFrom(descriptors.readBytes())
        return CodeGeneratorRequest.newBuilder()
            .addAllProtoFile(set.fileList)
            .addAllFileToGenerate(toGenerate)
            .build()
    }

    /** `option (protomorphic.library) = { ... };` */
    fun library(name: String, start: Int, end: Int, vararg deps: String): String =
        "option (protomorphic.library) = { name: \"$name\" extension_start: $start " +
            "extension_end: $end ${deps.joinToString(" ") { "dependencies: \"$it\"" }} };"

    /** An editions file in [pkg] importing protomorphic/options.proto and [imports]. */
    fun file(pkg: String, body: String, vararg imports: String) = buildString {
        appendLine("edition = \"2023\";")
        appendLine("package $pkg;")
        appendLine("import \"protomorphic/options.proto\";")
        imports.forEach { appendLine("import \"$it\";") }
        appendLine(body)
    }

    /** A "ui" library declaring the Shape interface, in ui/ui.proto. */
    val ui: Pair<String, String> = "ui/ui.proto" to file(
        "test.ui",
        """
        ${library("ui", 1, 999)}
        message Shape {
          option (protomorphic.interface) = true;
          extensions 1 to max;
        }
        """,
    )
}
