package com.dabble.protomorphic.rules

import kotlin.test.Test
import kotlin.test.assertTrue

class SchemaRulesTest {

    private fun assertError(expected: String, files: Map<String, String>) {
        val errors = SchemaRules(Schema(Protoc.request(files))).check()
        assertTrue(errors.any { expected in it }, "expected an error containing \"$expected\", got: $errors")
    }

    @Test
    fun extensionOutsideItsLibrarysRange() = assertError(
        "is outside library \"foundation\"'s extension range 1000..1999",
        mapOf(
            Protoc.ui,
            "foundation/shapes.proto" to Protoc.file(
                "test.foundation",
                "${Protoc.library("foundation", 1000, 1999, "ui")}\nmessage Circle {}\nextend test.ui.Shape { Circle circle = 5; }",
                "ui/ui.proto",
            ),
        ),
    )

    @Test
    fun extensionWithoutALibrary() = assertError(
        "needs a (protomorphic.library) declaration",
        mapOf(
            Protoc.ui,
            "x/x.proto" to "edition = \"2023\";\npackage test.x;\nimport \"ui/ui.proto\";\nmessage Circle {}\nextend test.ui.Shape { Circle circle = 1000; }\n",
        ),
    )

    @Test
    fun overlappingRanges() = assertError(
        "libraries \"ui\" (1..999) and \"other\" (500..1500) have overlapping extension ranges",
        mapOf(Protoc.ui, "other/other.proto" to Protoc.file("test.other", "${Protoc.library("other", 500, 1500)}\nmessage Empty {}")),
    )

    @Test
    fun importingALibraryThatIsNotADependency() = assertError(
        "library \"foundation\" imports ui/ui.proto of library \"ui\", which is not in its dependencies []",
        mapOf(
            Protoc.ui,
            "foundation/f.proto" to Protoc.file(
                "test.foundation",
                "${Protoc.library("foundation", 1000, 1999)}\nmessage Uses { test.ui.Shape shape = 1; }",
                "ui/ui.proto",
            ),
        ),
    )

    @Test
    fun inconsistentDeclarations() = assertError(
        "library \"ui\" is declared differently in",
        mapOf(Protoc.ui, "ui/more.proto" to Protoc.file("test.ui.more", "${Protoc.library("ui", 1, 500)}\nmessage More {}")),
    )

    @Test
    fun interfaceWithFields() = assertError(
        "interface test.bad.Shape must not declare fields (it has id)",
        mapOf(
            "bad/bad.proto" to Protoc.file(
                "test.bad",
                "${Protoc.library("bad", 1, 9)}\nmessage Shape {\n option (protomorphic.interface) = true;\n int32 id = 1;\n extensions 2 to max;\n}",
            ),
        ),
    )

    @Test
    fun interfaceWithoutAnExtensionRange() = assertError(
        "interface test.bad.Shape needs an extension range",
        mapOf(
            "bad/bad.proto" to Protoc.file(
                "test.bad",
                "${Protoc.library("bad", 1, 9)}\nmessage Shape { option (protomorphic.interface) = true; }",
            ),
        ),
    )

    @Test
    fun interfaceImplementationThatIsNotAMessage() = assertError(
        "implements interface test.ui.Shape, so it must be a singular message",
        mapOf(
            Protoc.ui,
            "foundation/f.proto" to Protoc.file(
                "test.foundation",
                "${Protoc.library("foundation", 1000, 1999, "ui")}\nextend test.ui.Shape { int32 radius = 1000; }",
                "ui/ui.proto",
            ),
        ),
    )
}
