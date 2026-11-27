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
- **Interface fields.** Fields declared on an interface are abstract, like a Kotlin interface's
  properties: every implementation must declare them, and they become properties of the Kotlin
  `Implementation` interface.
- **Abstract types.** Messages nested in an interface are abstract types, like type parameters:
  every implementation declares its own (e.g. `Input` and `Output`), and the Kotlin interface is
  generic, `Action.Implementation<Input, Output>`.
- **Scoped extensions.** One message can implement several interfaces under the same name with
  extensions declared inside a message (`Horizontal.center`, `Vertical.center`); their Kotlin
  `ProtoExtension`s live in a nested object per scope.
- **Typed Kotlin Multiplatform code** on the Wire runtime (Android, iOS, JVM). Each interface gets a
  Kotlin `Implementation` interface its implementations implement, typed factories and accessors,
  and an `implementationNumber` for dispatch.
- **Build-time rules.** Each library declares its extension-number range and dependencies with
  `option (protomorphic.library)`. The build fails on numbers outside a library's range, overlapping
  ranges, imports of undeclared libraries, or implementations missing an interface's fields or types.
- **Full proto semantics.** proto3 and editions 2023: implicit and explicit presence, packing, maps,
  oneofs (as sealed interfaces), nested types, well-known types, preserved unknown fields and enum
  values. Verified against `protoc --encode` on JVM, iOS and Android (`:conformance`).
- **Nothing to install.** The Gradle plugin resolves protoc and the generator from Maven.

## Example

```proto
// ui/shape.proto
message Shape {
  option (protomorphic.interface) = true;
  string test_tag = 1;   // abstract: every implementation declares it
  extensions 100 to max;
}

// foundation/shape.proto: implements ui's Shape without ui importing it
message RoundedCornerShape { float radius_dp = 1; string test_tag = 2; }
extend ui.Shape { RoundedCornerShape rounded_corner = 1000; }
```

```kotlin
val shape: Shape = RoundedCornerShape(radiusDp = 8f).toShape()   // or Shape.roundedCorner(...)
shape.roundedCorner?.radiusDp                                   // 8f
val impl: Shape.Implementation = RoundedCornerShape(testTag = "card")
impl.testTag                                                    // "card"
when (shape.implementationNumber) { ShapeExtensions.roundedCorner.number -> ... }
```

## Usage

```kotlin
plugins {
    kotlin("multiplatform")
    id("studio.dabble.protomorphic") version "0.1.0"
}

protomorphic {
    // Default: src/commonMain/proto (src/main/proto for Kotlin/JVM).
    // sourceDir = layout.projectDirectory.dir("src/commonMain/proto")
    // Proto roots of dependencies, read for imports only:
    // includes.from(project(":other").file("src/commonMain/proto"))
}
```

The plugin and its artifacts are on Maven Central, so `mavenCentral()` must be in both
`pluginManagement` and `dependencyResolutionManagement` repositories.
`protomorphic/options.proto` is on the proto path automatically.

## Modules

| Module | Artifact | Contents |
|---|---|---|
| `rules` | `studio.dabble.protomorphic:rules` | The schema rules and the language-independent schema model (JVM) |
| `protoc-gen-protomorphic-kotlin` | `studio.dabble.protomorphic:protoc-gen-protomorphic-kotlin` | The Kotlin generator: a protoc plugin that checks the rules and generates Kotlin (JVM) |
| `runtime` | `studio.dabble.protomorphic:runtime` | `ProtoExtension`, `ProtoExtensions`, `packAny` (KMP) |
| `gradle-plugin` | plugin `studio.dabble.protomorphic` | protoc + generator wiring, `protomorphic/options.proto` |
| `conformance` | - | Tests against `protoc --encode` |
| `conformance-suite` | - | A testee for protobuf's official conformance suite (binary proto3 and editions); known failures in `failure_list.txt` |

Kotlin is the first target. Swift and Go are planned as thin add-on plugins over the official
`protoc-gen-swift` / `protoc-gen-go`, which already handle messages and extensions; they add the
interface layer and reuse `rules`.

## Releasing

Push a `v<version>` tag. The release workflow sets the version from the tag and publishes every
artifact to Maven Central, signed.

## License

Apache License 2.0. See [LICENSE](LICENSE).
