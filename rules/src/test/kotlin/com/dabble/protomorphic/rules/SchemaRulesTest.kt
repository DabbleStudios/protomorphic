package com.dabble.protomorphic.rules

import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun interfaceWithAOneof() = assertError(
        "interface test.bad.Shape must not declare oneofs (it has kind)",
        mapOf(
            "bad/bad.proto" to Protoc.file(
                "test.bad",
                "${Protoc.library("bad", 1, 9)}\nmessage Shape {\n option (protomorphic.interface) = true;\n oneof kind { int32 id = 1; }\n extensions 2 to max;\n}",
            ),
        ),
    )

    /** ui/named.proto: a Named interface with an abstract `name` field. */
    private val named = "ui/named.proto" to Protoc.file(
        "test.ui.named",
        "${Protoc.library("ui", 1, 999)}\nmessage Named {\n option (protomorphic.interface) = true;\n string name = 1;\n extensions 100 to max;\n}",
    )

    private fun implementation(body: String) = "foundation/f.proto" to Protoc.file(
        "test.foundation",
        "${Protoc.library("foundation", 1000, 1999, "ui")}\n$body\nextend test.ui.named.Named { Person person = 1000; }",
        "ui/named.proto",
    )

    @Test
    fun implementationMissingAnInterfaceField() = assertError(
        "test.foundation.Person implements interface test.ui.named.Named (as test.foundation.person), so it must declare `string name [features.field_presence = EXPLICIT]`",
        mapOf(Protoc.ui, named, implementation("message Person { int32 age = 1; }")),
    )

    @Test
    fun implementationWithTheWrongFieldType() = assertError(
        "so it must declare `string name [features.field_presence = EXPLICIT]` (it declares `bytes name [features.field_presence = EXPLICIT]`)",
        mapOf(Protoc.ui, named, implementation("message Person { bytes name = 1; }")),
    )

    @Test
    fun implementationWithTheWrongPresence() = assertError(
        "so it must declare `string name [features.field_presence = EXPLICIT]` (it declares `string name`)",
        mapOf(Protoc.ui, named, implementation("message Person { string name = 1 [features.field_presence = IMPLICIT]; }")),
    )

    @Test
    fun implementationDeclaringTheFieldIsValid() {
        val errors = SchemaRules(
            Schema(Protoc.request(mapOf(Protoc.ui, named, implementation("message Person { int32 age = 1; string name = 7; }")))),
        ).check()
        assertEquals(emptyList(), errors)
    }

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

    /** ui/fn.proto: a Fn interface with abstract types Input and Output, and an `input` field of type Input. */
    private val fn = "ui/fn.proto" to Protoc.file(
        "test.ui.fn",
        "${Protoc.library("ui", 1, 999)}\nmessage Fn {\n option (protomorphic.interface) = true;\n message Input {}\n message Output {}\n Input input = 1;\n extensions 100 to max;\n}",
    )

    private fun fnImplementation(body: String) = "foundation/f.proto" to Protoc.file(
        "test.foundation",
        "${Protoc.library("foundation", 1000, 1999, "ui")}\n$body\nextend test.ui.fn.Fn { Square square = 1000; }",
        "ui/fn.proto",
    )

    @Test
    fun implementationMissingAnAbstractType() = assertError(
        "test.foundation.Square implements interface test.ui.fn.Fn (as test.foundation.square), so it must declare `message Output`",
        mapOf(Protoc.ui, fn, fnImplementation("message Square { message Input { int32 x = 1; } Input input = 1; }")),
    )

    @Test
    fun abstractTypedFieldMustUseTheImplementationsOwnType() = assertError(
        "so it must declare `Self.Input input` (it declares `test.ui.fn.Fn.Input input`)",
        mapOf(Protoc.ui, fn, fnImplementation("message Square { message Input {} message Output {} test.ui.fn.Fn.Input input = 1; }")),
    )

    @Test
    fun implementationBindingTheAbstractTypesIsValid() {
        val errors = SchemaRules(
            Schema(Protoc.request(mapOf(Protoc.ui, fn, fnImplementation("message Square { message Input { int32 x = 1; } message Output { int32 y = 1; } Input input = 1; }")))),
        ).check()
        assertEquals(emptyList(), errors)
    }

    @Test
    fun interfaceWithAnEnum() = assertError(
        "interface test.bad.Shape must not declare enums (it has Kind)",
        mapOf(
            "bad/bad.proto" to Protoc.file(
                "test.bad",
                "${Protoc.library("bad", 1, 9)}\nmessage Shape {\n option (protomorphic.interface) = true;\n enum Kind { KIND_UNSPECIFIED = 0; }\n extensions 1 to max;\n}",
            ),
        ),
    )
}
