package studio.dabble.protomorphic.rules

import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.DescriptorValidationException
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.DynamicMessage
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest

/**
 * Everything the rules and the language generators need about a protoc request, independent of any
 * target language: protobuf-java descriptors (which resolve proto3 and editions semantics such as
 * presence, packing and open/closed enums), source comments and the protomorphic options.
 */
class Schema(request: CodeGeneratorRequest) {

    private val protos: Map<String, FileDescriptorProto> = request.protoFileList.associateBy { it.name }
    private val built = mutableMapOf<String, FileDescriptor>()

    /** Every file in the request (files to generate and all their imports). */
    val files: List<FileDescriptor> = request.protoFileList.map { build(it.name) }

    val filesToGenerate: List<FileDescriptor> = request.fileToGenerateList.map { build(it) }

    private fun build(name: String): FileDescriptor = built[name] ?: run {
        val proto = protos[name] ?: throw SchemaException("$name: missing from the request")
        val deps = proto.dependencyList.map { build(it) }.toTypedArray()
        val file = try {
            FileDescriptor.buildFrom(proto, deps)
        } catch (e: DescriptorValidationException) {
            throw SchemaException("$name: ${e.message}")
        }
        built[name] = file
        file
    }

    fun proto(file: FileDescriptor): FileDescriptorProto = protos.getValue(file.name)

    // ----- Comments -----------------------------------------------------------------------------

    private val comments: Map<String, Map<List<Int>, String>> = protos.mapValues { (_, proto) ->
        proto.sourceCodeInfo.locationList
            .filter { it.hasLeadingComments() }
            .associate { it.pathList to it.leadingComments }
    }

    /** The leading comment at [path] (a SourceCodeInfo path) in [file], or null. */
    fun comment(file: FileDescriptor, path: List<Int>): String? = comments[file.name]?.get(path)

    // ----- protomorphic options -----------------------------------------------------------------

    /** The (protomorphic.library) declaration of [file], or null. */
    fun library(file: FileDescriptor): Library? {
        val descriptor = findMessage(LIBRARY_MESSAGE) ?: return null
        val bytes = file.toProto().options.unknownFields.getField(LIBRARY_OPTION_NUMBER)
            .lengthDelimitedList.lastOrNull() ?: return null
        val message = DynamicMessage.parseFrom(descriptor, bytes)
        fun field(name: String) = message.getField(descriptor.findFieldByName(name))
        @Suppress("UNCHECKED_CAST")
        return Library(
            name = field("name") as String,
            extensionStart = field("extension_start") as Int,
            extensionEnd = field("extension_end") as Int,
            dependencies = (field("dependencies") as List<String>).toSet(),
        )
    }

    /** Whether [message] is marked `option (protomorphic.interface) = true`. */
    fun isInterface(message: Descriptor): Boolean =
        message.toProto().options.unknownFields.getField(INTERFACE_OPTION_NUMBER)
            .varintList.lastOrNull()?.let { it != 0L } ?: false

    private fun findMessage(fullName: String): Descriptor? =
        files.firstNotNullOfOrNull { it.findMessageTypeByName(fullName.substringAfterLast('.'))?.takeIf { m -> m.fullName == fullName } }

    /** Extensions whose extendee is a google.protobuf.*Options message: schema-only, not generated. */
    fun isOptionExtension(extension: FieldDescriptor): Boolean =
        extension.containingType.fullName.startsWith("google.protobuf.") &&
            extension.containingType.name.endsWith("Options")

    data class Library(
        val name: String,
        val extensionStart: Int,
        val extensionEnd: Int,
        val dependencies: Set<String>,
    ) {
        val hasRange get() = extensionStart != 0 || extensionEnd != 0
        val range get() = extensionStart..extensionEnd
    }

    companion object {
        const val LIBRARY_MESSAGE = "protomorphic.Library"
        const val LIBRARY_OPTION_NUMBER = 50001
        const val INTERFACE_OPTION_NUMBER = 50002
    }
}
