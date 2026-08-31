# Generate and use your first Kompact packet

This walkthrough generates a Kotlin packet API, a C99 header, and a canonical descriptor from the repository's smallest example schema. It uses the checked-in disposable consumer so the unreleased `0.1.0-SNAPSHOT` artifacts never leave your machine.

## Prerequisites

Use a clone of this repository with:

- JDK 21;
- the checked-in Gradle wrapper;
- an Android SDK containing platform 36, which the repository build configures;
- a POSIX shell.

Run all commands from the repository root.

## 1. Publish Kompact to the disposable repository

Publish the runtime, annotations, processor, and Gradle plugin:

```bash
./gradlew \
  :kompact-runtime:publishAllPublicationsToVerificationRepository \
  :kompact-annotations:publishAllPublicationsToVerificationRepository \
  :kompact-processor:publishAllPublicationsToVerificationRepository \
  :kompact-gradle-plugin:publishAllPublicationsToVerificationRepository \
  --no-build-cache --no-configuration-cache
```

The build ends with `BUILD SUCCESSFUL`. The artifacts are now under `build/verification-repository/`; nothing is published externally.

## 2. Look at the schema

Open [`publication-consumer/src/commonMain/kotlin/example/PublishedPacketSchema.kt`](../../publication-consumer/src/commonMain/kotlin/example/PublishedPacketSchema.kt). It declares one body bit:

```kotlin
@KompactSchema(registryName = "published_packet", id = 1, version = 0)
interface PublishedPacketSchema {
    @KompactField(
        stableName = "enabled",
        semanticType = "enabled",
        bitOffset = 0,
        bitWidth = 1,
    )
    val enabled: Boolean
}
```

The corresponding entry in [`publication-consumer/kompact-registry.json`](../../publication-consumer/kompact-registry.json) fixes the schema ID, layout version, body size, and descriptor fingerprint.

## 3. Generate and compile the Kotlin API

Run the disposable JVM consumer:

```bash
./gradlew -p publication-consumer \
  clean compileKotlinJvm \
  --refresh-dependencies --no-build-cache --no-configuration-cache
```

The build ends with `BUILD SUCCESSFUL`. It creates these files:

```text
publication-consumer/build/generated/kompact/publication/
├── c/published_packet_v0.h
├── descriptors/published_packet_v0.json
├── kotlin/example/PublishedPacket.kt
└── reports/kompact-registry.proposed.json
```

The consumer source [`UsePublishedPacket.kt`](../../publication-consumer/src/commonMain/kotlin/example/UsePublishedPacket.kt) compiles against the generated `PublishedPacket` facade and `PublishedPacketWriter`. This proves that generated declarations are connected to `commonMain` before JVM compilation.

## 4. Inspect the generated packet API

Open `publication-consumer/build/generated/kompact/publication/kotlin/example/PublishedPacket.kt`. The generated facade reports:

```kotlin
PublishedPacket.SCHEMA_ID          // 1
PublishedPacket.LAYOUT_VERSION     // 0
PublishedPacket.BODY_BIT_SIZE      // 1
PublishedPacket.PACKET_BYTE_SIZE   // 3
```

`PACKET_BYTE_SIZE` is three bytes: the two-byte Kompact envelope plus one body bit, rounded up to the next byte. The remaining seven transport-tail bits must stay zero.

The generated API provides:

- `PublishedPacket.initialize(packet)` for a zeroed packet and writer;
- `PublishedPacket.wrap(packet)` for checked read access;
- `PublishedPacket.edit(packet)` for checked mutation of an existing packet;
- `PublishedPacketView.enabled` for direct read access;
- `PublishedPacketWriter.writeEnabled(value)` for in-place mutation.

## 5. Compile the C99 consumer

Compile both checked-in translation units against the same generated headers:

```bash
cc -std=c99 \
  -Wall -Wextra -Wconversion -Wsign-conversion -Werror -pedantic-errors \
  -I publication-consumer/build/generated/kompact/publication/c \
  publication-consumer/c/main.c \
  publication-consumer/c/other.c \
  -o /tmp/kompact-publication-c

/tmp/kompact-publication-c
```

The compiler prints nothing, and the program exits successfully. The second translation unit verifies that the header-only API has no linkage conflict.

You now have one schema consumed through generated Kotlin and C99 interfaces. To add a schema to another KMP module, continue with [How to generate Kotlin and C99 interfaces](../how-to/generate-kotlin-and-c.md). For the complete configuration and generated surface, see the [Gradle plugin reference](../reference/gradle-plugin.md) and [schema authoring reference](../reference/schema-authoring.md).
