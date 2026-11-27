package studio.dabble.protomorphic

import com.squareup.wire.FieldEncoding
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoReader
import com.squareup.wire.ProtoWriter
import com.squareup.wire.Syntax

/**
 * The adapter of `bool` fields. Wire's `ProtoAdapter.BOOL` reads only the low 32 bits of the varint,
 * so 2^32 decodes as false; protobuf reads any non-zero varint as true.
 */
public val PROTO_BOOL: ProtoAdapter<Boolean> = object : ProtoAdapter<Boolean>(
    FieldEncoding.VARINT, Boolean::class, null, Syntax.PROTO_3, false,
) {
    override fun encodedSize(value: Boolean): Int = 1

    override fun encode(writer: ProtoWriter, value: Boolean) {
        writer.writeVarint32(if (value) 1 else 0)
    }

    override fun decode(reader: ProtoReader): Boolean = reader.readVarint64() != 0L

    override fun redact(value: Boolean): Boolean = value
}
