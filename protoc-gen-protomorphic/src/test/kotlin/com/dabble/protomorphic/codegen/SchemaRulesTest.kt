package com.dabble.protomorphic.codegen

import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs the real protoc on small schemas and passes its descriptors to [generate], as protoc does
 * when it runs the plugin.
 */
class SchemaRulesTest {

    private val protoc = File(System.getProperty("protoc"))
    private val optionsProtos = File(System.getProperty("optionsProtos"))

    /** Compiles [files] (path to content) and generates [toGenerate] (default: all of them). */
    private fun run(files: Map<String, String>, toGenerate: List<String> = files.keys.toList()) =
        generate(request(files, toGenerate))

    private fun request(files: Map<String, String>, toGenerate: List<String>): CodeGeneratorRequest {
        val root = Files.createTempDirectory("protomorphic-rules").toFile()
        files.forEach { (path, content) -> root.resolve(path).apply { parentFile.mkdirs() }.writeText(content) }
        val wellKnown = root.resolve("_well_known").also { dir ->
            listOf("descriptor", "any").forEach { name ->
                val resource = "google/protobuf/$name.proto"
                dir.resolve(resource).apply { parentFile.mkdirs() }
                    .writeText(javaClass.classLoader.getResource(resource)!!.readText())
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
        if (process.waitFor() != 0) fail("protoc failed:\n$output")
        val set = FileDescriptorSet.parseFrom(descriptors.readBytes())
        return CodeGeneratorRequest.newBuilder()
            .addAllProtoFile(set.fileList)
            .addAllFileToGenerate(toGenerate)
            .build()
    }

    private fun library(name: String, start: Int, end: Int, vararg deps: String): String =
        "option (protomorphic.library) = { name: \"$name\" extension_start: $start " +
            "extension_end: $end ${deps.joinToString(" ") { "dependencies: \"$it\"" }} };"

    private fun file(pkg: String, body: String, vararg imports: String) = buildString {
        appendLine("edition = \"2023\";")
        appendLine("package $pkg;")
        appendLine("import \"protomorphic/options.proto\";")
        imports.forEach { appendLine("import \"$it\";") }
        appendLine(body)
    }

    private val ui = "ui/ui.proto" to file(
        "test.ui",
        """
        ${library("ui", 1, 999)}
        message Shape {
          option (protomorphic.interface) = true;
          extensions 1 to max;
        }
        """,
    )

    private fun assertError(expected: String, files: Map<String, String>, toGenerate: List<String> = files.keys.toList()) {
        val error = run(files, toGenerate).error
        assertTrue(expected in error, "expected an error containing \"$expected\", got: \"$error\"")
    }

    @Test
    fun validSchemaGenerates() {
        val foundation = "foundation/shapes.proto" to file(
            "test.foundation",
            """
            ${library("foundation", 1000, 1999, "ui")}
            message Circle {}
            extend test.ui.Shape { Circle circle = 1000; }
            """,
            "ui/ui.proto",
        )
        val response = run(mapOf(ui, foundation))
        assertEquals("", response.error)
        val shapes = response.fileList.single { it.name == "test/foundation/Shapes.kt" }.content
        assertTrue("public data class Circle(" in shapes)
        assertTrue(": test.ui.Shape.Implementation" in shapes)
        assertTrue("public fun test.ui.Shape.Companion.circle(" in shapes)
    }

    @Test
    fun extensionOutsideItsLibrarysRange() = assertError(
        "is outside library \"foundation\"'s extension range 1000..1999",
        mapOf(
            ui,
            "foundation/shapes.proto" to file(
                "test.foundation",
                "${library("foundation", 1000, 1999, "ui")}\nmessage Circle {}\nextend test.ui.Shape { Circle circle = 5; }",
                "ui/ui.proto",
            ),
        ),
    )

    @Test
    fun extensionWithoutALibrary() = assertError(
        "needs a (protomorphic.library) declaration",
        mapOf(
            ui,
            "x/x.proto" to "edition = \"2023\";\npackage test.x;\nimport \"ui/ui.proto\";\nmessage Circle {}\nextend test.ui.Shape { Circle circle = 1000; }\n",
        ),
    )

    @Test
    fun overlappingRanges() = assertError(
        "libraries \"ui\" (1..999) and \"other\" (500..1500) have overlapping extension ranges",
        mapOf(ui, "other/other.proto" to file("test.other", "${library("other", 500, 1500)}\nmessage Empty {}")),
    )

    @Test
    fun importingALibraryThatIsNotADependency() = assertError(
        "library \"foundation\" imports ui/ui.proto of library \"ui\", which is not in its dependencies []",
        mapOf(
            ui,
            "foundation/f.proto" to file(
                "test.foundation",
                "${library("foundation", 1000, 1999)}\nmessage Uses { test.ui.Shape shape = 1; }",
                "ui/ui.proto",
            ),
        ),
    )

    @Test
    fun inconsistentDeclarations() = assertError(
        "library \"ui\" is declared differently in",
        mapOf(ui, "ui/more.proto" to file("test.ui.more", "${library("ui", 1, 500)}\nmessage More {}")),
    )

    @Test
    fun interfaceWithFields() = assertError(
        "interface test.bad.Shape must not declare fields (it has id)",
        mapOf(
            "bad/bad.proto" to file(
                "test.bad",
                "${library("bad", 1, 9)}\nmessage Shape {\n option (protomorphic.interface) = true;\n int32 id = 1;\n extensions 2 to max;\n}",
            ),
        ),
    )

    @Test
    fun interfaceWithoutAnExtensionRange() = assertError(
        "interface test.bad.Shape needs an extension range",
        mapOf(
            "bad/bad.proto" to file(
                "test.bad",
                "${library("bad", 1, 9)}\nmessage Shape { option (protomorphic.interface) = true; }",
            ),
        ),
    )

    @Test
    fun interfaceImplementationThatIsNotAMessage() = assertError(
        "implements interface test.ui.Shape, so it must be a singular message",
        mapOf(
            ui,
            "foundation/f.proto" to file(
                "test.foundation",
                "${library("foundation", 1000, 1999, "ui")}\nextend test.ui.Shape { int32 radius = 1000; }",
                "ui/ui.proto",
            ),
        ),
    )

    @Test
    fun onlyRequestedFilesAreGenerated() {
        val foundation = "foundation/shapes.proto" to file(
            "test.foundation",
            "${library("foundation", 1000, 1999, "ui")}\nmessage Circle {}\nextend test.ui.Shape { Circle circle = 1000; }",
            "ui/ui.proto",
        )
        val response = run(mapOf(ui, foundation), toGenerate = listOf("foundation/shapes.proto"))
        assertEquals("", response.error)
        assertEquals(listOf("test/foundation/Shapes.kt"), response.fileList.map { it.name })
        assertFalse(response.fileList.single().content.contains("data class Shape("))
    }
}
