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
}
