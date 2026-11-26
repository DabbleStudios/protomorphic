package com.dabble.protomorphic

import com.squareup.wire.FieldEncoding
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.Syntax
import kotlin.reflect.KClass

/** A generated message's adapter, with its fields by proto name for [ProtoFields]. */
public abstract class MessageAdapter<E : Any>(
    fieldEncoding: FieldEncoding,
    type: KClass<*>?,
    typeUrl: String?,
    syntax: Syntax,
    identity: E?,
    sourceFile: String?,
) : ProtoAdapter<E>(fieldEncoding, type, typeUrl, syntax, identity, sourceFile) {
    public abstract val fields: Map<String, ProtoField>
}

public class ProtoField(
    public val number: Int,
    private val messageAdapter: (() -> MessageAdapter<*>)? = null,
) {
    public val message: MessageAdapter<*>? get() = messageAdapter?.invoke()
}
