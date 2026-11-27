package studio.dabble.protomorphic.conformance.suite

import com.google.protobuf.ByteString
import com.google.protobuf.conformance.Conformance.ConformanceRequest
import com.google.protobuf.conformance.Conformance.ConformanceResponse
import com.google.protobuf.conformance.Conformance.WireFormat
import com.squareup.wire.ProtoAdapter
import java.io.DataInputStream
import java.io.EOFException
import java.io.OutputStream

private val adapters: Map<String, ProtoAdapter<*>> = mapOf(
    "protobuf_test_messages.proto3.TestAllTypesProto3" to
        com.google.protobuf_test_messages.proto3.TestAllTypesProto3.ADAPTER,
    "protobuf_test_messages.editions.proto3.TestAllTypesProto3" to
        com.google.protobuf_test_messages.editions.proto3.TestAllTypesProto3.ADAPTER,
)

/**
 * The conformance_test_runner testee: reads length-prefixed (4-byte little-endian)
 * ConformanceRequests from stdin and answers each with a ConformanceResponse on stdout.
 */
fun main() {
    val input = DataInputStream(System.`in`.buffered())
    val output = System.out.buffered()
    while (true) {
        val size = try {
            Integer.reverseBytes(input.readInt())
        } catch (e: EOFException) {
            return
        }
        val request = ConformanceRequest.parseFrom(ByteArray(size).also(input::readFully))
        val response = respond(request).toByteArray()
        output.writeIntLe(response.size)
        output.write(response)
        output.flush()
    }
}

fun respond(request: ConformanceRequest): ConformanceResponse {
    val response = ConformanceResponse.newBuilder()
    @Suppress("UNCHECKED_CAST")
    val adapter = adapters[request.messageType] as ProtoAdapter<Any>?
        ?: return response.setSkipped("${request.messageType} is not generated (proto2 and groups are unsupported)").build()
    if (request.payloadCase != ConformanceRequest.PayloadCase.PROTOBUF_PAYLOAD) {
        return response.setSkipped("only the binary format is supported").build()
    }
    if (request.requestedOutputFormat != WireFormat.PROTOBUF) {
        return response.setSkipped("only the binary format is supported").build()
    }
    val message = try {
        adapter.decode(request.protobufPayload.toByteArray())
    } catch (e: Exception) {
        return response.setParseError(e.toString()).build()
    } catch (e: StackOverflowError) {
        return response.setParseError(e.toString()).build()
    }
    return try {
        response.setProtobufPayload(ByteString.copyFrom(adapter.encode(message))).build()
    } catch (e: Exception) {
        response.setSerializeError(e.toString()).build()
    }
}

private fun OutputStream.writeIntLe(value: Int) {
    for (shift in 0 until 32 step 8) write(value ushr shift)
}
