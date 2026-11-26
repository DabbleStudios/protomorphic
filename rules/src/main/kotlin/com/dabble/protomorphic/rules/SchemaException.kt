package com.dabble.protomorphic.rules

/** A schema the generator cannot handle; reported to protoc as the plugin's error. */
class SchemaException(message: String) : Exception(message)
