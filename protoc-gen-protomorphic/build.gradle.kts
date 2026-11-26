import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// protoc-gen-protomorphic: a protoc plugin that generates Kotlin Multiplatform message classes,
// enums and typed extension accessors on the Wire runtime, and enforces the protomorphic schema
// rules (library extension ranges, layering, interfaces). The protomorphic Gradle plugin resolves
// this module's jar and runs it as protoc's plugin.
plugins {
    alias(libs.plugins.kotlinJvm)
    `java-library`
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
    // CodeGeneratorRequest / CodeGeneratorResponse plugin protocol.
    implementation(libs.protobuf.java)
    testImplementation(libs.kotlin.test)
}

// The rule tests run the real protoc on small schemas.
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
