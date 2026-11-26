package com.dabble.protomorphic.rules

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FileDescriptor

/**
 * The protomorphic schema rules, which protobuf itself cannot express and which hold whatever the
 * target language. Checked for the files being generated
 * (and, for library declarations, every file in the request); any violation fails the build.
 *
 * 1. An extension of a message other than a google.protobuf.*Options is declared in a file with a
 *    (library) declaration, and its number is inside that library's extension range.
 * 2. Every file of a library carries the same declaration.
 * 3. The extension ranges of different libraries do not overlap.
 * 4. A file imports files of another library only if that library is in its `dependencies`.
 * 5. An (interface) message has an extension range and no oneofs or nested enums. Its fields are
 *    abstract, like a Kotlin interface's properties: they are never set on the interface itself. Its
 *    nested messages are abstract types, like a Kotlin interface's type parameters.
 * 6. An extension of an (interface) message is a singular message.
 * 7. A message implementing an interface declares each of the interface's fields, with the same
 *    name, type, cardinality and presence (its own field number). A field the interface types with
 *    one of its abstract types is typed with the implementation's nested message of that name.
 * 8. A message implementing an interface declares a nested message for each of the interface's
 *    abstract types, with the same name.
 */
class SchemaRules(private val schema: Schema) {

    fun check(): List<String> = buildList {
        val libraries = schema.files.mapNotNull { file -> schema.library(file)?.let { file to it } }

        // 2. Consistent declarations.
        libraries.groupBy({ it.second.name }, { it }).forEach { (name, declared) ->
            val distinct = declared.map { it.second }.distinct()
            if (distinct.size > 1) {
                add("library \"$name\" is declared differently in: " + declared.joinToString { it.first.name })
            }
        }

        // 3. No overlapping ranges.
        val ranges = libraries.map { it.second }.distinctBy { it.name }.filter { it.hasRange }
        ranges.forEachIndexed { i, a ->
            ranges.drop(i + 1).forEach { b ->
                if (a.extensionStart <= b.extensionEnd && b.extensionStart <= a.extensionEnd) {
                    add("libraries \"${a.name}\" (${a.range}) and \"${b.name}\" (${b.range}) have overlapping extension ranges")
                }
            }
        }

        schema.filesToGenerate.forEach { file ->
            val library = schema.library(file)

            // 1. Extensions inside the library's range.
            allExtensions(file).filterNot { schema.isOptionExtension(it) }.forEach { ext ->
                when {
                    library == null -> add(
                        "${file.name}: extension ${ext.fullName} = ${ext.number} needs a " +
                            "(protomorphic.library) declaration on its file",
                    )
                    !library.hasRange || ext.number !in library.range -> add(
                        "${file.name}: extension ${ext.fullName} = ${ext.number} is outside library " +
                            "\"${library.name}\"'s extension range" + if (library.hasRange) " ${library.range}" else " (none declared)",
                    )
                }
            }

            // 4. Layering.
            if (library != null) {
                file.dependencies.forEach { dep ->
                    val other = schema.library(dep)
                    if (other != null && other.name != library.name && other.name !in library.dependencies) {
                        add(
                            "${file.name}: library \"${library.name}\" imports ${dep.name} of library " +
                                "\"${other.name}\", which is not in its dependencies ${library.dependencies.sorted()}",
                        )
                    }
                }
            }

            // 5. Interfaces.
            allMessages(file).filter { schema.isInterface(it) }.forEach { message ->
                if (message.enumTypes.isNotEmpty()) {
                    add("${file.name}: interface ${message.fullName} must not declare enums (it has ${message.enumTypes.joinToString { it.name }})")
                }
                if (message.realOneofs.isNotEmpty()) {
                    add("${file.name}: interface ${message.fullName} must not declare oneofs (it has ${message.realOneofs.joinToString { it.name }})")
                }
                if (message.toProto().extensionRangeCount == 0) {
                    add("${file.name}: interface ${message.fullName} needs an extension range, e.g. `extensions 1 to max;`")
                }
            }

            // 6. Interface implementations.
            allExtensions(file).filter { !schema.isOptionExtension(it) && schema.isInterface(it.containingType) }
                .forEach { ext ->
                    if (ext.isRepeated || ext.type != FieldDescriptor.Type.MESSAGE) {
                        add("${file.name}: ${ext.fullName} implements interface ${ext.containingType.fullName}, so it must be a singular message")
                        return@forEach
                    }
                    // 7. Interface fields.
                    val implementation = ext.messageType
                    val iface = ext.containingType
                    val implementedAs = "${implementation.fullName} implements interface ${iface.fullName} (as ${ext.fullName})"
                    iface.fields.forEach { required ->
                        val own = implementation.findFieldByName(required.name)
                        val prefix = "${file.name}: $implementedAs, so it must declare `${signature(required, iface)}`"
                        when {
                            own == null -> add(prefix)
                            own.realContainingOneof != null -> add("$prefix outside a oneof")
                            signature(own, implementation) != signature(required, iface) ->
                                add("$prefix (it declares `${signature(own, implementation)}`)")
                        }
                    }
                    // 8. Abstract types.
                    iface.nestedTypes.filterNot { it.options.mapEntry }.forEach { type ->
                        if (implementation.nestedTypes.none { it.name == type.name }) {
                            add("${file.name}: $implementedAs, so it must declare `message ${type.name}`")
                        }
                    }
                }
        }
    }

    /** The field as written in a .proto file; a message nested in [owner] is `Self.<Name>`. */
    private fun signature(field: FieldDescriptor, owner: Descriptor): String {
        fun type(f: FieldDescriptor) = when (f.type) {
            FieldDescriptor.Type.MESSAGE, FieldDescriptor.Type.GROUP ->
                if (f.messageType.containingType == owner && !f.messageType.options.mapEntry) "Self.${f.messageType.name}" else f.messageType.fullName
            FieldDescriptor.Type.ENUM -> f.enumType.fullName
            else -> f.type.name.lowercase()
        }
        val declared = when {
            field.isMapField -> "map<${type(field.messageType.findFieldByNumber(1))}, ${type(field.messageType.findFieldByNumber(2))}>"
            field.isRepeated -> "repeated ${type(field)}"
            else -> type(field)
        }
        val presence = if (!field.isRepeated && field.type != FieldDescriptor.Type.MESSAGE && field.hasPresence()) {
            " [features.field_presence = EXPLICIT]"
        } else {
            ""
        }
        return "$declared ${field.name}$presence"
    }

    private fun allMessages(file: FileDescriptor): List<Descriptor> =
        file.messageTypes.flatMap { listOf(it) + nested(it) }

    private fun nested(message: Descriptor): List<Descriptor> =
        message.nestedTypes.flatMap { listOf(it) + nested(it) }

    private fun allExtensions(file: FileDescriptor): List<FieldDescriptor> =
        file.extensions + allMessages(file).flatMap { it.extensions }
}
