# How to generate Kotlin and C99 interfaces from a schema

Use this guide when adding a Kompact schema to an existing Kotlin Multiplatform module. The module must own one protocol namespace and one registry.

Kompact is not externally published yet. Resolve `0.1.0-SNAPSHOT` from a repository where you have published this checkout, such as the disposable repository used by the [first-schema walkthrough](../tutorials/first-schema.md).

## Configure the KMP module

Apply the plugin and declare the runtime and annotation libraries explicitly:

```kotlin
plugins {
    kotlin("multiplatform") version "2.3.20"
    id("ch.trancee.kompact") version "0.1.0-SNAPSHOT"
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("ch.trancee.kompact:kompact-runtime:0.1.0-SNAPSHOT")
            implementation("ch.trancee.kompact:kompact-annotations:0.1.0-SNAPSHOT")
        }
    }
}

kompact {
    namespace.set("telemetry")
    maxPacketBytes.set(244)
}
```

The configured namespace and packet limit must equal the values in `kompact-registry.json`. The plugin fails before generation when they differ.

## Declare the schema in `commonMain`

Create `src/commonMain/kotlin/example/SwitchPacketSchema.kt`:

```kotlin
package example

import ch.trancee.kompact.annotations.KompactField
import ch.trancee.kompact.annotations.KompactReserved
import ch.trancee.kompact.annotations.KompactSchema

@KompactSchema(registryName = "switch_packet", id = 7, version = 0)
@KompactReserved(stableName = "future", bitOffset = 1, bitWidth = 7)
interface SwitchPacketSchema {
    @KompactField(
        stableName = "enabled",
        semanticType = "enabled",
        bitOffset = 0,
        bitWidth = 1,
    )
    val enabled: Boolean
}
```

Every body bit belongs to a field or a named reserved range. Stable schema, field, semantic, enum-entry, and reserved-range names use lowercase letters, digits, and underscores and start with a letter.

## Add the registry identity

Add the new identity to `kompact-registry.json`. For a new schema, use a temporary 64-character lowercase placeholder hash:

```json
{
  "$schema": "https://trancee.github.io/kompact/schema/kompact-registry-v1.schema.json",
  "formatVersion": 1,
  "namespace": "telemetry",
  "maxPacketBytes": 244,
  "schemas": [
    {
      "stableName": "switch_packet",
      "id": 7,
      "versions": [
        {
          "version": 0,
          "status": "active",
          "bodyBitSize": 8,
          "descriptorSha256": "0000000000000000000000000000000000000000000000000000000000000000"
        }
      ]
    }
  ]
}
```

Run generation:

```bash
./gradlew generateKompactSchemas
```

The task fails with `KOMPACT-KSP-1006` because the checked-in fingerprint differs from the generated descriptor. This is the review gate, not a recovery failure. Inspect:

```text
build/generated/kompact/telemetry/reports/kompact-registry.proposed.json
```

Review the proposed identity, body size, status, and fingerprint. Copy the reviewed proposal into `kompact-registry.json`, then rerun:

```bash
./gradlew checkKompactSchemas
```

The task succeeds and publishes complete Kotlin, C99, descriptor, and report directories. A schema error removes previously published generated outputs rather than leaving stale interfaces.

## Compile the generated Kotlin declarations

Compile a target that consumes `commonMain`:

```bash
./gradlew compileKotlinJvm
```

Use the generated facade from common Kotlin:

```kotlin
import ch.trancee.kompact.runtime.KompactDecodeResult

val packet = ByteArray(SwitchPacket.PACKET_BYTE_SIZE)
val writer = when (val result = SwitchPacket.initialize(packet)) {
    is KompactDecodeResult.Success -> result.value
    is KompactDecodeResult.Failure -> error(result.error.toString())
}
check(writer.writeEnabled(true) == null)
```

`wrap` and `edit` validate untrusted packet bytes. Direct view access assumes the packet was successfully wrapped and has not been modified outside Kompact since validation.

## Package the C headers

Create the deterministic C-header ZIP:

```bash
./gradlew packageKompactCHeaders
```

The archive is written under `build/distributions/` with classifier `c-headers` unless `cHeadersClassifier` changes it. Consumers can also resolve the `kompactCHeaders` Gradle variant.

To attach the same archive to the KMP root Maven publication, apply `maven-publish` yourself and opt in:

```kotlin
kompact {
    publishCHeaders.set(true)
}
```

## Validate against registry history

CI and release builds should provide the merge-base or previously published registry as a local file:

```kotlin
kompact {
    compatibilityBaseline.set(layout.projectDirectory.file("baseline/kompact-registry.json"))
    requireCompatibilityBaseline.set(true)
}
```

The task performs no Git, network, or credential lookup. It rejects removed history, identity reuse, fingerprint drift, illegal lifecycle transitions, missing supported decoder sources, and non-sequential versions.

## Recover from common failures

| Failure | Action |
| --- | --- |
| `KOMPACT-KSP-1003` | Make the plugin namespace and packet limit match the registry; also check schema identity values. |
| `KOMPACT-KSP-1006` | Review `kompact-registry.proposed.json`; accept it only when the schema change has the intended identity and version. |
| `KOMPACT-KSP-1009` | Supply `compatibilityBaseline` when required mode is enabled. |
| `KOMPACT-KSP-1012` | Restore source for every `active` or `decode-only` registry version, or perform a legal lifecycle change. |
| `KOMPACT-KSP-1104` | Make the declared bit width match the scalar, aggregate, optional, or nested type. |
| `KOMPACT-KSP-1105` / `1106` | Remove overlaps and assign every gap to an explicit reserved range. |
| `KOMPACT-KSP-1205` | Break the nested-schema cycle; nested references must be acyclic. |

See the [Gradle plugin reference](../reference/gradle-plugin.md) for every property and task, and the [schema authoring reference](../reference/schema-authoring.md) for supported types and annotations.
