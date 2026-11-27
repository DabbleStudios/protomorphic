package studio.dabble.protomorphic

import com.squareup.wire.EnumAdapter
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.Syntax
import com.squareup.wire.WireEnum

/** `google.protobuf.NullValue`, the type of fields declared with it. */
public enum class NullValue(override val value: Int) : WireEnum {
    NULL_VALUE(0),
    ;

    public companion object {
        public fun fromValue(value: Int): NullValue? = if (value == 0) NULL_VALUE else null

        public val ADAPTER: ProtoAdapter<NullValue> = object : EnumAdapter<NullValue>(NullValue::class, Syntax.PROTO_3, NULL_VALUE) {
            override fun fromValue(value: Int): NullValue? = NullValue.fromValue(value)
        }
    }
}
