package com.dabble.protomorphic.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

/** The `protomorphic { }` block. */
abstract class ProtomorphicExtension {
    /** This module's proto root; every .proto file under it is generated. Default: `src/commonMain/proto`. */
    abstract val sourceDir: DirectoryProperty

    /** Proto roots of dependencies, read for imports but not generated. */
    abstract val includes: ConfigurableFileCollection

    /**
     * Uses the `:protoc-gen-protomorphic-kotlin` and `:runtime` projects of this build instead of the
     * published artifacts. Only for developing protomorphic itself. Default: false.
     */
    abstract val useLocalProjects: Property<Boolean>
}
