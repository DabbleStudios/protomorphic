package com.dabble.protomorphic.conformance

import com.dabble.protomorphic.conformance.foundation.v1.CircleShape
import com.dabble.protomorphic.conformance.foundation.v1.Label
import com.dabble.protomorphic.conformance.foundation.v1.RoundedCornerShape
import com.dabble.protomorphic.conformance.foundation.v1.ShapesExtensions
import com.dabble.protomorphic.conformance.foundation.v1.circle
import com.dabble.protomorphic.conformance.foundation.v1.label
import com.dabble.protomorphic.conformance.foundation.v1.roundedCorner
import com.dabble.protomorphic.conformance.foundation.v1.withLabel
import com.dabble.protomorphic.conformance.ui.v1.ClipModifier
import com.dabble.protomorphic.conformance.ui.v1.RectangleShape
import com.dabble.protomorphic.conformance.ui.v1.Shape
import com.dabble.protomorphic.conformance.ui.v1.Tagged
import com.dabble.protomorphic.conformance.ui.v1.rectangle
import com.dabble.protomorphic.conformance.v1.Collections
import com.dabble.protomorphic.conformance.v1.Color
import com.dabble.protomorphic.conformance.v1.ExplicitPresence
import com.dabble.protomorphic.conformance.v1.ImplicitPresence
import com.dabble.protomorphic.conformance.v1.Mode
import com.dabble.protomorphic.conformance.v1.Scalars
import com.dabble.protomorphic.conformance.v1.Strict
import com.dabble.protomorphic.conformance.v1.Subset
import com.dabble.protomorphic.conformance.v1.Superset
import com.dabble.protomorphic.conformance.v1.WellKnownTypes
import com.dabble.protomorphic.packAny
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.durationOfSeconds
import com.squareup.wire.ofEpochSecond
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Every expected encoding comes from `protoc --encode` (protobuf's reference encoder) on the
 * textprotos in src/test-data, so these tests check the generated code against protobuf itself,
 * on every target.
 */
class ConformanceTest {

    /** [value] encodes to [golden], and [golden] decodes back to [value]. */
    private fun <T : Any> assertConforms(golden: String, value: T, adapter: ProtoAdapter<T>) {
        assertEquals(golden, adapter.encodeByteString(value).hex(), "encoding")
        assertEquals(value, adapter.decode(golden.decodeHex()), "decoding")
    }

    @Test
    fun scalars() = assertConforms(
        Goldens.ScalarsFull,
        Scalars(
            aDouble = 1.5,
            aFloat = -2.25f,
            anInt64 = -9_000_000_000L,
            aUint64 = -446_744_073_709_551_616L, // 18_000_000_000_000_000_000 as an unsigned 64-bit value
            anInt32 = -7,
            aFixed64 = 1_234_567_890_123L,
            aFixed32 = 0x12345678,
            aBool = true,
            aString = "héllo",
            someBytes = "0102ff".decodeHex(),
            aUint32 = -294_967_296, // 4_000_000_000 as an unsigned 32-bit value
            anSfixed32 = -42,
            anSfixed64 = -4_200_000_000L,
            anSint32 = -300,
            anSint64 = -3_000_000_000L,
            optionalInt32 = 0,
            color = Color.COLOR_GREEN,
        ),
        Scalars.ADAPTER,
    )

    @Test
    fun implicitPresenceSkipsDefaults() = assertConforms(Goldens.ScalarsEmpty, Scalars(), Scalars.ADAPTER)

    @Test
    fun negativeZeroIsNotTheDefault() =
        assertConforms(Goldens.NegativeZero, Scalars(aDouble = -0.0, aFloat = -0.0f), Scalars.ADAPTER)

    @Test
    fun unknownEnumValuesArePreserved() {
        val decoded = Scalars.ADAPTER.decode(Goldens.UnknownEnum.decodeHex())
        assertEquals(1, decoded.anInt32)
        assertEquals(Color.COLOR_UNSPECIFIED, decoded.color)
        assertEquals(Goldens.UnknownEnum, Scalars.ADAPTER.encodeByteString(decoded).hex())
    }

    @Test
    fun collections() = assertConforms(
        Goldens.Collections,
        Collections(
            packedInt32 = listOf(1, -1, 300),
            packedDouble = listOf(0.5, -0.5),
            packedSint64 = listOf(-1L, 1L),
            strings = listOf("a", ""),
            byteList = listOf("00".decodeHex(), "".encodeUtf8()),
            nestedList = listOf(
                Collections.Nested(label = "x", level = Collections.Nested.Level.LEVEL_HIGH),
                Collections.Nested(),
            ),
            colors = listOf(Color.COLOR_RED, Color.COLOR_GREEN),
            counts = mapOf("k" to 3),
            nestedById = mapOf(7L to Collections.Nested(label = "seven")),
            nested = Collections.Nested(label = "n"),
            level = Collections.Nested.Level.LEVEL_HIGH,
            choice = Collections.Choice.NumberChoice(0),
        ),
        Collections.ADAPTER,
    )

    @Test
    fun oneofCases() {
        assertConforms(Goldens.ChoiceText, Collections(choice = Collections.Choice.Text("hi")), Collections.ADAPTER)
        assertConforms(
            Goldens.ChoiceNested,
            Collections(choice = Collections.Choice.NestedChoice(Collections.Nested(label = "c"))),
            Collections.ADAPTER,
        )
        assertConforms(
            Goldens.ChoiceColor,
            Collections(choice = Collections.Choice.ColorChoice(Color.COLOR_RED)),
            Collections.ADAPTER,
        )
    }

    @Test
    fun wellKnownTypes() {
        val value = WellKnownTypes(
            any = Subset.ADAPTER.packAny(Subset(id = 5)),
            duration = durationOfSeconds(90, 5),
            timestamp = ofEpochSecond(1_700_000_000, 7),
            int32Value = 0,
            stringValue = "s",
            struct = mapOf("a" to 1.0),
            value = "v",
            values = mapOf("b" to true),
        )
        assertEquals(Goldens.WellKnown, WellKnownTypes.ADAPTER.encodeByteString(value).hex())

        val decoded = WellKnownTypes.ADAPTER.decode(Goldens.WellKnown.decodeHex())
        assertEquals(Subset(id = 5), decoded.any?.unpack(Subset.ADAPTER))
        assertEquals(90L, decoded.duration?.getSeconds())
        assertEquals(5, decoded.duration?.getNano())
        assertEquals(1_700_000_000L, decoded.timestamp?.getEpochSecond())
        assertEquals(7, decoded.timestamp?.getNano())
        assertEquals(0, decoded.int32Value)
        assertEquals("s", decoded.stringValue)
        assertEquals(mapOf("a" to 1.0), decoded.struct)
        assertEquals("v", decoded.value)
        assertEquals(mapOf("b" to true), decoded.values)
    }

    @Test
    fun unknownFieldsSurviveAnOlderReader() {
        val superset = Superset.ADAPTER.decode(Goldens.Superset.decodeHex())
        assertEquals(Superset(id = 1, addedLater = "new", color = Color.COLOR_RED), superset)

        // A reader built before fields 2 and 3 existed keeps them and writes them back unchanged.
        val subset = Subset.ADAPTER.decode(Goldens.Superset.decodeHex())
        assertEquals(1, subset.id)
        assertNotEquals(0, subset.unknownFields.size)
        assertEquals(Goldens.Superset, Subset.ADAPTER.encodeByteString(subset).hex())
    }

    @Test
    fun editionsExplicitPresenceWritesZeroValues() {
        assertConforms(
            Goldens.Explicit,
            ExplicitPresence(
                count = 0,
                name = "",
                flag = false,
                packed = listOf(1, 2),
                expanded = listOf(3, 4),
                mode = Mode.MODE_UNSPECIFIED,
                strict = Strict.STRICT_TWO,
            ),
            ExplicitPresence.ADAPTER,
        )
        assertEquals("", ExplicitPresence.ADAPTER.encodeByteString(ExplicitPresence()).hex())
    }

    @Test
    fun editionsImplicitPresence() {
        assertConforms(
            Goldens.Implicit,
            ImplicitPresence(count = 2, name = "n", ratio = 0.25f, mode = Mode.MODE_ON, explicitCount = 0),
            ImplicitPresence.ADAPTER,
        )
        assertConforms(Goldens.ImplicitZero, ImplicitPresence(explicitCount = 0), ImplicitPresence.ADAPTER)
    }

    @Test
    fun interfaceImplementedByAnotherLibrary() {
        val viaFactory = ClipModifier(shape = Shape.roundedCorner(RoundedCornerShape(radiusDp = 8f)))
        val viaImplementation = ClipModifier(shape = RoundedCornerShape(radiusDp = 8f).toShape())
        assertEquals(viaFactory, viaImplementation)
        assertConforms(Goldens.ClipRounded, viaFactory, ClipModifier.ADAPTER)

        val shape = ClipModifier.ADAPTER.decode(Goldens.ClipRounded.decodeHex()).shape!!
        assertEquals(8f, shape.roundedCorner?.radiusDp)
        assertNull(shape.circle)
        assertNull(shape.rectangle)
        assertEquals(ShapesExtensions.roundedCorner.number, shape.implementationNumber)
    }

    @Test
    fun interfaceImplementedByItsOwnLibrary() =
        assertConforms(Goldens.ClipRectangle, ClipModifier(shape = Shape.rectangle(RectangleShape())), ClipModifier.ADAPTER)

    @Test
    fun anInterfaceHoldsOneImplementation() {
        val implementation: Shape.Implementation = CircleShape()
        val shape = implementation.toShape()
        assertEquals(CircleShape(), shape.circle)
        assertNull(shape.roundedCorner)
        assertEquals(1001, shape.implementationNumber)
        assertNull(Shape().implementationNumber)
    }

    @Test
    fun extensionOfARegularMessage() {
        assertConforms(Goldens.Tagged, Tagged(id = 3).withLabel(Label(text = "t")), Tagged.ADAPTER)
        val replaced = Tagged(id = 3).withLabel(Label(text = "a")).withLabel(Label(text = "b"))
        assertEquals(Label(text = "b"), replaced.label)
        assertNull(Tagged(id = 3).label)
    }
}
