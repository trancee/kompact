# Gradle plugin reference

This page describes the public Gradle interface of Kompact `0.1.0-SNAPSHOT`.

## Coordinates

| Item | Value |
| --- | --- |
| Plugin ID | `ch.trancee.kompact` |
| Runtime | `ch.trancee.kompact:kompact-runtime:0.1.0-SNAPSHOT` |
| Annotations | `ch.trancee.kompact:kompact-annotations:0.1.0-SNAPSHOT` |
| Plugin implementation | `ch.trancee.kompact:kompact-gradle-plugin:0.1.0-SNAPSHOT` |
| Internal processor | `ch.trancee.kompact:kompact-processor:0.1.0-SNAPSHOT` |

The artifacts are not externally published. The repository's verification build publishes them to `build/verification-repository/`.

## Project requirements

The plugin requires:

- the Kotlin Multiplatform plugin already applied;
- a `commonMain` source set;
- explicit runtime and annotation dependencies in the consumer;
- one protocol namespace and one registry per plugin application.

The plugin does not apply Kotlin, Android, Maven Publish, repository, target, runtime, or annotation dependencies.

## `kompact` extension

```kotlin
kompact {
    namespace.set("telemetry")
    maxPacketBytes.set(244)
}
```

| Property | Type | Required/default | Constraints and effect |
| --- | --- | --- | --- |
| `namespace` | `Property<String>` | Required | Must equal the registry `namespace`. Used in generated and local-state paths. Stable-name syntax is `[a-z][a-z0-9_]*`. |
| `maxPacketBytes` | `Property<Int>` | Required | Must equal registry `maxPacketBytes` and be at least 2. Generation rejects schemas whose envelope and body exceed it. |
| `registryFile` | `RegularFileProperty` | `kompact-registry.json` in the project root | Current protocol identity ledger and reviewed descriptor fingerprints. |
| `compatibilityBaseline` | `RegularFileProperty` | Optional | Local historical registry input. The task does not fetch it from Git, Maven, or the network. |
| `requireCompatibilityBaseline` | `Property<Boolean>` | `false` | When `true`, missing `compatibilityBaseline` fails with `KOMPACT-KSP-1009`. |
| `publishCHeaders` | `Property<Boolean>` | `false` | When `true` and `maven-publish` is already applied, attaches the C-header ZIP to the `kotlinMultiplatform` publication. |
| `cHeadersClassifier` | `Property<String>` | `c-headers` | Classifier used by the C-header archive and publication attachment. |

The plugin, runtime, and annotation artifact versions must match. A mismatch fails before generation with `KOMPACT-KSP-1003`.

## Tasks

### `generateKompactSchemas`

Builds all descriptors in one process-isolated KSP2 common pass.

Inputs include common Kotlin sources, registry and optional baseline files, compile and worker classpaths, protocol configuration, Kotlin language/API versions, file changes, and aligned library versions.

Outputs:

```text
build/generated/kompact/<namespace>/
├── c/
├── descriptors/
├── kotlin/
└── reports/
```

Local state is stored under `build/kompact/<namespace>/`. Generation uses a task-owned staging area and replaces output directories only with complete results. A validation or compatibility failure removes published Kotlin, C, and descriptor outputs. The registry proposal remains available when current schema output differs from the registry.

### `checkKompactSchemas`

Depends on `generateKompactSchemas` and participates in the project's `check` task. It validates schema structure, current registry consistency, generated descriptor availability, registry proposals, and the optional historical baseline.

### `packageKompactCHeaders`

Creates a reproducible ZIP from the generated C directory:

- preserved file timestamps: disabled;
- file order: reproducible;
- destination: `build/distributions/`;
- classifier: `cHeadersClassifier`.

## Generated source wiring

The generated Kotlin directory is registered through the generation task's output provider as a `commonMain` source directory. Target compilation, IDE import, and source-consuming tasks inherit the task dependency from that provider.

## C-header variant

The consumable `kompactCHeaders` configuration exposes the deterministic ZIP with:

| Attribute | Value |
| --- | --- |
| Category | `documentation` |
| Usage | `java-runtime` |
| Library elements | `kompact-c-headers` |

## Publication behavior

The plugin does not apply `maven-publish`. When the consumer applies it and sets `publishCHeaders` to `true`, the plugin attaches the C archive only to the `kotlinMultiplatform` root publication. Runtime and annotations publish KMP root metadata plus JVM, Android, `iosArm64`, and `iosSimulatorArm64` variants from the repository build.

## Output ownership

Generated Kotlin, C headers, descriptors, reports, registry proposals, and KSP local state are build outputs. Do not check consumer-generated files into source control. The compact conformance suite under `conformance/` is the repository-owned reviewed exception.

For an executable configuration sequence, see [How to generate Kotlin and C99 interfaces](../how-to/generate-kotlin-and-c.md). For authoring and generated declarations, see the [schema authoring reference](schema-authoring.md).
