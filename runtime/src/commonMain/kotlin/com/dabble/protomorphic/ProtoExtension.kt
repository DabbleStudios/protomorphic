package com.dabble.protomorphic

import com.squareup.wire.ProtoAdapter

/**
 * One protobuf extension: field [number] of the message [extendee], holding a [T].
 *
 * protoc-gen-protomorphic-kotlin generates one of these per `extend` field, next to typed accessors that use it.
 * Registries (e.g. renderers keyed by extension) can collect them.
 */
public class ProtoExtension<T : Any>(
    /** Fully-qualified proto name of the extended message, e.g. "example.ui.v1.Shape". */
    public val extendee: String,
    /** The extension's field number. */
    public val number: Int,
    /** Fully-qualified proto name of the extension field, e.g. "example.foundation.v1.rounded_corner". */
    public val name: String,
    /** Adapter of the extension's value type. */
    public val adapter: ProtoAdapter<T>,
) {
    override fun toString(): String = "ProtoExtension($name = $number on $extendee)"
}
