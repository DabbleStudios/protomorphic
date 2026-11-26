import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The protomorphic schema rules (library extension ranges, layering, interfaces) and the
// language-independent schema model every generator builds on: protobuf-java descriptors, source
// comments and the protomorphic options. Generators for each target language depend on this module.
plugins {
    alias(libs.plugins.kotlinJvm)
    `java-library`
    `java-test-fixtures`
    alias(libs.plugins.mavenPublish)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Descriptors (FileDescriptor.buildFrom resolves proto3 and editions semantics) and the
    // CodeGeneratorRequest plugin protocol.
    api(libs.protobuf.java)
    testImplementation(libs.kotlin.test)
}

// The tests run the real protoc on small schemas (see the Protoc test fixture in :rules).
val protoc: Configuration by configurations.creating { isTransitive = false }
dependencies {
    protoc("com.google.protobuf:protoc:${libs.versions.protobuf.get()}:${protocClassifier()}@exe")
}

tasks.test {
    val protocFiles: FileCollection = files(protoc)
    val options = rootProject.file("gradle-plugin/src/main/resources")
    inputs.files(protocFiles).withPropertyName("protoc")
    inputs.dir(options).withPropertyName("options")
    doFirst {
        val exe = protocFiles.singleFile.also { it.setExecutable(true) }
        systemProperty("protoc", exe.absolutePath)
        systemProperty("optionsProtos", options.absolutePath)
    }
}

fun protocClassifier(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val osPart = when {
        "mac" in os -> "osx"
        "linux" in os -> "linux"
        "windows" in os -> "windows"
        else -> error("Unsupported OS for protoc: $os")
    }
    val archPart = if (arch == "aarch64" || arch == "arm64") "aarch_64" else "x86_64"
    return "$osPart-$archPart"
}

mavenPublishing {
    pom {
        name = "ProtoMorphic rules"
        description = "The ProtoMorphic schema rules and the language-independent schema model generators build on."
    }
}

// The test fixtures run the real protoc and are for this build's tests only.
(components["java"] as AdhocComponentWithVariants).apply {
    withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
    withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
}
afterEvaluate {
    (components["java"] as AdhocComponentWithVariants)
        .withVariantsFromConfiguration(configurations["testFixturesSourcesElements"]) { skip() }
}
