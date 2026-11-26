import java.util.Properties

// The protomorphic Gradle plugin (`studio.dabble.protomorphic`): runs protoc (resolved from Maven) with
// protoc-gen-protomorphic-kotlin on a module's protos, puts protomorphic/options.proto on the proto path,
// and adds the generated Kotlin and the runtime to the module.
plugins {
    `kotlin-dsl`
    alias(libs.plugins.mavenPublish)
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
            id = "studio.dabble.protomorphic"
            implementationClass = "studio.dabble.protomorphic.gradle.ProtomorphicPlugin"
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

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
    pom {
        url = "https://github.com/DabbleStudios/protomorphic"
        inceptionYear = "2026"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "DabbleStudios"
                name = "Dabble Studios"
                url = "https://github.com/DabbleStudios"
            }
        }
        scm {
            url = "https://github.com/DabbleStudios/protomorphic"
            connection = "scm:git:https://github.com/DabbleStudios/protomorphic.git"
            developerConnection = "scm:git:ssh://git@github.com/DabbleStudios/protomorphic.git"
        }
    }
}

mavenPublishing.pom {
    name = "ProtoMorphic Gradle plugin"
    description = "Runs protoc with protoc-gen-protomorphic-kotlin on a module's protos and adds the generated Kotlin and the ProtoMorphic runtime."
}
