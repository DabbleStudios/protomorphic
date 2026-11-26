package com.dabble.protomorphic.kotlin

import com.dabble.protomorphic.rules.Schema
import com.dabble.protomorphic.rules.SchemaException
import com.dabble.protomorphic.rules.SchemaRules
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse

/**
 * protoc-gen-protomorphic-kotlin entry point. protoc writes a CodeGeneratorRequest (every file to generate plus all
 * of its imports, parsed and linked) to stdin and reads a CodeGeneratorResponse from stdout.
 */
fun main() {
    val request = CodeGeneratorRequest.parseFrom(System.`in`)
    generate(request).writeTo(System.out)
}

/** Validates the schema rules, then generates Kotlin for each file in `file_to_generate`. */
fun generate(request: CodeGeneratorRequest): CodeGeneratorResponse {
    val response = CodeGeneratorResponse.newBuilder()
        .setSupportedFeatures(
            (CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL_VALUE or
                CodeGeneratorResponse.Feature.FEATURE_SUPPORTS_EDITIONS_VALUE).toLong(),
        )
        .setMinimumEdition(com.google.protobuf.DescriptorProtos.Edition.EDITION_PROTO2_VALUE)
        .setMaximumEdition(com.google.protobuf.DescriptorProtos.Edition.EDITION_2023_VALUE)
    val schema = try {
        Schema(request)
    } catch (e: SchemaException) {
        return response.setError(e.message).build()
    }
    val errors = SchemaRules(schema).check()
    if (errors.isNotEmpty()) {
        return response.setError(errors.joinToString("\n")).build()
    }
    try {
        val generator = KotlinGenerator(schema)
        schema.filesToGenerate.forEach { file ->
            val output = generator.generate(file)
            response.addFile(
                CodeGeneratorResponse.File.newBuilder().setName(output.path).setContent(output.content),
            )
        }
    } catch (e: SchemaException) {
        return response.setError(e.message).build()
    }
    return response.build()
}
