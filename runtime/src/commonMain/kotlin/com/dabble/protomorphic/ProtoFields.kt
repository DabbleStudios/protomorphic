package com.dabble.protomorphic

import com.squareup.wire.FieldEncoding
import com.squareup.wire.ProtoReader
import com.squareup.wire.ProtoWriter
import okio.Buffer
import okio.ByteString

/**
 * Reads and writes fields of encoded messages by field path, e.g. `["input", "url"]`.
 *
 * Writing appends the field: protobuf merges it into an earlier occurrence (the last scalar wins).
 */
public object ProtoFields {

    public class Value internal constructor(
        public val encoding: FieldEncoding,
        internal val number: Long,
        public val bytes: ByteString,
    ) {
        public companion object {
            public fun lengthDelimited(bytes: ByteString): Value = Value(FieldEncoding.LENGTH_DELIMITED, 0, bytes)
        }
    }

    public fun read(message: ByteString, adapter: MessageAdapter<*>, path: List<String>): Value? {
        require(path.isNotEmpty()) { "empty field path" }
        val field = adapter.fields[path.first()]
            ?: throw IllegalArgumentException("${adapter.typeUrl} has no field \"${path.first()}\"")
        val value = readNumber(message, field.number) ?: return null
        if (path.size == 1) return value
        val nested = field.message ?: throw IllegalArgumentException("\"${path.first()}\" of ${adapter.typeUrl} is not a message")
        return read(value.bytes, nested, path.drop(1))
    }

    public fun write(message: ByteString, adapter: MessageAdapter<*>, path: List<String>, value: Value): ByteString =
        Buffer().write(message).write(encode(adapter, path, value)).readByteString()

    private fun encode(adapter: MessageAdapter<*>, path: List<String>, value: Value): ByteString {
        val field = adapter.fields[path.first()]
            ?: throw IllegalArgumentException("${adapter.typeUrl} has no field \"${path.first()}\"")
        val buffer = Buffer()
        val writer = ProtoWriter(buffer)
        if (path.size == 1) {
            writer.writeTag(field.number, value.encoding)
            when (value.encoding) {
                FieldEncoding.VARINT -> writer.writeVarint64(value.number)
                FieldEncoding.FIXED32 -> writer.writeFixed32(value.number.toInt())
                FieldEncoding.FIXED64 -> writer.writeFixed64(value.number)
                FieldEncoding.LENGTH_DELIMITED -> {
                    writer.writeVarint32(value.bytes.size)
                    writer.writeBytes(value.bytes)
                }
            }
        } else {
            val nested = field.message ?: throw IllegalArgumentException("\"${path.first()}\" of ${adapter.typeUrl} is not a message")
            val inner = encode(nested, path.drop(1), value)
            writer.writeTag(field.number, FieldEncoding.LENGTH_DELIMITED)
            writer.writeVarint32(inner.size)
            writer.writeBytes(inner)
        }
        return buffer.readByteString()
    }

    /** For messages without an adapter, e.g. a wrapper type's value (field 1). */
    public fun read(message: ByteString, number: Int): Value? = readNumber(message, number)

    private fun readNumber(message: ByteString, number: Int): Value? {
        if (message.size == 0) return null
        val reader = ProtoReader(Buffer().write(message))
        var result: Value? = null
        reader.forEachTag { tag ->
            if (tag != number) {
                reader.skip()
            } else {
                result = when (val encoding = reader.peekFieldEncoding()!!) {
                    FieldEncoding.VARINT -> Value(encoding, reader.readVarint64(), ByteString.EMPTY)
                    FieldEncoding.FIXED32 -> Value(encoding, reader.readFixed32().toLong(), ByteString.EMPTY)
                    FieldEncoding.FIXED64 -> Value(encoding, reader.readFixed64(), ByteString.EMPTY)
                    FieldEncoding.LENGTH_DELIMITED -> Value(encoding, 0, reader.readBytes())
                }
            }
        }
        return result
    }
}
