# ProtoMorphic

Polymorphism for Protocol Buffers: a protoc plugin, a small Kotlin Multiplatform runtime, and a
Gradle plugin.

Protobuf has no interfaces or inheritance. A `oneof` is closed: the message that declares it must
import every case, which inverts library layering and creates import cycles in recursive trees.
`google.protobuf.Any` is open but untyped, and costs a ~80-byte type URL per value on the wire.
ProtoMorphic adds **interfaces**: open, typed polymorphism across libraries.

## What it does

- **Interfaces.** A message marked `option (protomorphic.interface) = true;` with an extension range
  holds exactly one implementation. Other libraries implement it by extending it, so the declaring
  library never imports its implementations and third parties add implementations without changing
  it. On the wire an implementation costs its field tag (2-3 bytes), not a type URL.
- **Typed Kotlin Multiplatform code** on the Wire runtime (Android, iOS, JVM). Each interface gets a
  Kotlin `Implementation` interface its implementations implement, typed factories and accessors,
  and an `implementationNumber` for dispatch.
- **Build-time rules.** Each library declares its extension-number range and dependencies with
  `option (protomorphic.library)`. The build fails on numbers outside a library's range, overlapping
  ranges, imports of undeclared libraries, or interfaces with fields.
- **Full proto semantics.** proto3 and editions 2023: implicit and explicit presence, packing, maps,
  oneofs (as sealed interfaces), nested types, well-known types, preserved unknown fields and enum
  values. Verified against `protoc --encode` on JVM, iOS and Android (`:conformance`).
- **Nothing to install.** The Gradle plugin resolves protoc and the generator from Maven.

## Example

```proto
// ui/shape.proto
message Shape {
  option (protomorphic.interface) = true;
  extensions 1 to max;
}

// foundation/shape.proto: implements ui's Shape without ui importing it
message RoundedCornerShape { float radius_dp = 1; }
extend ui.Shape { RoundedCornerShape rounded_corner = 1000; }
```

```kotlin
val shape: Shape = RoundedCornerShape(radiusDp = 8f).toShape()   // or Shape.roundedCorner(...)
shape.roundedCorner?.radiusDp                                   // 8f
when (shape.implementationNumber) { ShapeExtensions.roundedCorner.number -> ... }
```

## Usage

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.dabble.protomorphic")
}

protomorphic {
    // Default: src/commonMain/proto (src/main/proto for Kotlin/JVM).
    // sourceDir = layout.projectDirectory.dir("src/commonMain/proto")
    // Proto roots of dependencies, read for imports only:
    // includes.from(project(":other").file("src/commonMain/proto"))
}
```

`protomorphic/options.proto` is on the proto path automatically.

## Modules

| Module | Artifact | Contents |
|---|---|---|
| `protoc-gen-protomorphic` | `com.dabble.protomorphic:protoc-gen-protomorphic` | The protoc plugin (JVM) |
| `runtime` | `com.dabble.protomorphic:runtime` | `ProtoExtension`, `ProtoExtensions`, `packAny` (KMP) |
| `gradle-plugin` | plugin `com.dabble.protomorphic` | protoc + generator wiring, `protomorphic/options.proto` |
| `conformance` | - | Tests against `protoc --encode` |

Until artifacts are published, consume it as a composite build (e.g. a git submodule plus
`includeBuild`).
