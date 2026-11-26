package com.dabble.protomorphic.kotlin

import com.dabble.protomorphic.rules.Protoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratorTest {

    @Test
    fun validSchemaGenerates() {
        val foundation = "foundation/shapes.proto" to Protoc.file(
            "test.foundation",
            """
            ${Protoc.library("foundation", 1000, 1999, "ui")}
            message Circle {}
            extend test.ui.Shape { Circle circle = 1000; }
            """,
            "ui/ui.proto",
        )
        val response = generate(Protoc.request(mapOf(Protoc.ui, foundation)))
        assertEquals("", response.error)
        val shapes = response.fileList.single { it.name == "test/foundation/Shapes.kt" }.content
        assertTrue("public data class Circle(" in shapes)
        assertTrue(": test.ui.Shape.Implementation" in shapes)
        assertTrue("public fun test.ui.Shape.Companion.circle(" in shapes)
    }

    @Test
    fun onlyRequestedFilesAreGenerated() {
        val foundation = "foundation/shapes.proto" to Protoc.file(
            "test.foundation",
            "${Protoc.library("foundation", 1000, 1999, "ui")}\nmessage Circle {}\nextend test.ui.Shape { Circle circle = 1000; }",
            "ui/ui.proto",
        )
        val response = generate(Protoc.request(mapOf(Protoc.ui, foundation), toGenerate = listOf("foundation/shapes.proto")))
        assertEquals("", response.error)
        assertEquals(listOf("test/foundation/Shapes.kt"), response.fileList.map { it.name })
        assertFalse(response.fileList.single().content.contains("data class Shape("))
    }

    @Test
    fun interfaceFieldsBecomeImplementationProperties() {
        val ui = "ui/ui.proto" to Protoc.file(
            "test.ui",
            """
            ${Protoc.library("ui", 1, 999)}
            message Named {
              option (protomorphic.interface) = true;
              string name = 1;
              extensions 100 to max;
            }
            """,
        )
        val foundation = "foundation/people.proto" to Protoc.file(
            "test.foundation",
            "${Protoc.library("foundation", 1000, 1999, "ui")}\nmessage Person { int32 age = 1; string name = 2; }\nextend test.ui.Named { Person person = 1000; }",
            "ui/ui.proto",
        )
        val response = generate(Protoc.request(mapOf(ui, foundation)))
        assertEquals("", response.error)
        val named = response.fileList.single { it.name == "test/ui/Ui.kt" }.content
        assertTrue("public val name: kotlin.String?\n" in named, named)
        assertFalse("public val name: kotlin.String? = null," in named, "the interface itself has no name property")
        assertTrue("firstNumber(unknownFields, kotlin.collections.setOf(1))" in named)
        val people = response.fileList.single { it.name == "test/foundation/People.kt" }.content
        assertTrue("override val name: kotlin.String? = null," in people, people)
    }

    @Test
    fun abstractTypesBecomeTypeParameters() {
        val ui = "ui/ui.proto" to Protoc.file(
            "test.ui",
            """
            ${Protoc.library("ui", 1, 999)}
            message Fn {
              option (protomorphic.interface) = true;
              message Input {}
              message Output {}
              Input input = 1;
              extensions 100 to max;
            }
            """,
        )
        val foundation = "foundation/square.proto" to Protoc.file(
            "test.foundation",
            "${Protoc.library("foundation", 1000, 1999, "ui")}\nmessage Square { message Input { int32 x = 1; } message Output { int32 y = 1; } Input input = 1; }\nextend test.ui.Fn { Square square = 1000; }",
            "ui/ui.proto",
        )
        val response = generate(Protoc.request(mapOf(ui, foundation)))
        assertEquals("", response.error)
        val fn = response.fileList.single { it.name == "test/ui/Ui.kt" }.content
        assertTrue("public interface Implementation<Input : kotlin.Any, Output : kotlin.Any>" in fn, fn)
        assertTrue("public val input: Input?" in fn, fn)
        assertFalse("data class Input(" in fn, "abstract types are not generated")
        val square = response.fileList.single { it.name == "test/foundation/Square.kt" }.content
        assertTrue(": test.ui.Fn.Implementation<test.foundation.Square.Input, test.foundation.Square.Output>" in square, square)
        assertTrue("override val input: test.foundation.Square.Input? = null," in square, square)
    }
}
