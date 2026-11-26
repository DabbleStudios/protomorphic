import java.util.Properties

// The protomorphic Gradle plugin (`com.dabble.protomorphic`): runs protoc (resolved from Maven) with
// protoc-gen-protomorphic on a module's protos, puts protomorphic/options.proto on the proto path,
// and adds the generated Kotlin and the runtime to the module.
plugins {
    `kotlin-dsl`
}

val rootProperties = Properties().apply { file("../gradle.properties").inputStream().use(::load) }
group = rootProperties.getProperty("group")
version = rootProperties.getProperty("version")

dependencies {
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("protomorphic") {
            id = "com.dabble.protomorphic"
            implementationClass = "com.dabble.protomorphic.gradle.ProtomorphicPlugin"
        }
    }
}

// The plugin resolves the generator, runtime and protoc versions it was built with.
val writeVersions by tasks.registering {
    val out = layout.buildDirectory.dir("generated/versions")
    val values = mapOf("version" to version.toString(), "protobuf" to libs.versions.protobuf.get())
    inputs.properties(values)
    outputs.dir(out)
    doLast {
        val file = out.get().file("protomorphic/versions.properties").asFile
        file.parentFile.mkdirs()
        file.writeText(values.entries.joinToString("\n") { "${it.key}=${it.value}" } + "\n")
    }
}
sourceSets.main {
    resources.srcDir(writeVersions)
}
