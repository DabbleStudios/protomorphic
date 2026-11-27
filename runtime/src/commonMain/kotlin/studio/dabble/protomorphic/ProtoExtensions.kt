package studio.dabble.protomorphic

import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoReader
import com.squareup.wire.ProtoWriter
import okio.Buffer
import okio.ByteString

/**
 * Reads and writes extension fields, which travel in a message's `unknownFields`.
 *
 * A generated class never knows the extensions other libraries declare on it; the extending
 * library's generated accessors call these functions with their own [ProtoExtension].
 */
public object ProtoExtensions {

    /** The value of [extension] in [unknownFields] (the last occurrence wins), or null. */
    public fun <T : Any> get(unknownFields: ByteString, extension: ProtoExtension<T>): T? {
        if (unknownFields.size == 0) return null
        val reader = ProtoReader(Buffer().write(unknownFields))
        var result: T? = null
        reader.forEachTag { tag ->
            if (tag == extension.number) result = extension.adapter.decode(reader) else reader.skip()
        }
        return result
    }

    /** [unknownFields] with [extension] set to [value], replacing any previous occurrence. */
    public fun <T : Any> set(unknownFields: ByteString, extension: ProtoExtension<T>, value: T): ByteString {
        val kept = if (unknownFields.size == 0) {
            ByteString.EMPTY
        } else {
            val reader = ProtoReader(Buffer().write(unknownFields))
            reader.forEachTag { tag -> if (tag == extension.number) reader.skip() else reader.readUnknownField(tag) }
        }
        return Buffer().write(kept).also { encode(it, extension, value) }.readByteString()
    }

    /**
     * The `unknownFields` of an interface message (whose own fields are abstract) holding only
     * [extension] set to [value]. Setting one implementation replaces any other.
     */
    public fun <T : Any> only(extension: ProtoExtension<T>, value: T): ByteString =
        Buffer().also { encode(it, extension, value) }.readByteString()

    /** The first field number not in [skipped]: an interface skips its abstract fields' numbers. */
    public fun firstNumber(unknownFields: ByteString, skipped: Set<Int> = emptySet()): Int? {
        if (unknownFields.size == 0) return null
        val reader = ProtoReader(Buffer().write(unknownFields))
        var first: Int? = null
        reader.forEachTag { tag ->
            if (first == null && tag !in skipped) first = tag
            reader.skip()
        }
        return first
    }

    private fun <T : Any> encode(buffer: Buffer, extension: ProtoExtension<T>, value: T) {
        extension.adapter.encodeWithTag(ProtoWriter(buffer), extension.number, value)
    }
}
