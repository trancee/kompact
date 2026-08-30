# ADR-0009: Gradle modules, generation, and publication

Status: accepted

## Context

Kompact must process each common schema once, generate Kotlin and C from the same descriptor, make generated declarations visible to every KMP target and IDE import, restore outputs safely from the Gradle build cache, and publish complete KMP and firmware artifacts. Standard target-specific KSP tasks repeat common processing, while raw generated-directory paths create implicit task dependencies and stale-output risks.

## Decision

### Modules and coordinates

Kompact uses four focused Gradle modules:

- `kompact-runtime` is a public KMP library published as `ch.trancee.kompact:kompact-runtime`.
- `kompact-annotations` is a public KMP library published as `ch.trancee.kompact:kompact-annotations`.
- `kompact-processor` is an internal JVM module containing symbol analysis, descriptor construction, validation, and Kotlin/C generation.
- `kompact-gradle-plugin` is a public JVM Gradle plugin implementation published as `ch.trancee.kompact:kompact-gradle-plugin` with plugin ID `ch.trancee.kompact`.

The processor ships as an undocumented implementation dependency of the Gradle plugin. It remains separately testable but is not a supported direct integration interface. Processor and KSP2 types never appear in runtime, annotation, generated, or consumer public interfaces.

Runtime and annotation modules publish Kotlin Multiplatform root metadata plus JVM, explicit Android, `iosArm64`, and `iosSimulatorArm64` variants. Schema annotations use source retention where KSP processing permits and add no runtime dependency.

Conformance fixtures, Gradle TestKit fixtures, publication consumers, and benchmarks remain internal test source sets or internal projects. They do not enter production publications.

### Plugin interface and ownership

The plugin requires an existing Kotlin Multiplatform project and `commonMain`. It does not apply Kotlin, Android, target, Maven Publish, repository, or dependency plugins and does not declare consumer dependencies.

Consumers explicitly declare `kompact-runtime` and `kompact-annotations`. The plugin validates that its version, generator version, annotation version, runtime version, and runtime interface version align exactly. A mismatch fails before generation.

One plugin application owns one protocol namespace, one registry, one packet limit, one descriptor set, one generation task, and one C-header archive. A project needing another protocol namespace uses another schema-owning KMP module.

The typed `kompact` extension exposes:

- required protocol namespace;
- required maximum packet byte size;
- registry file, defaulting to project-root `kompact-registry.json`;
- C-header generation and publication settings, including the default `c-headers` classifier.

Configured namespace and packet limit must equal their registry values. Generated directories and task implementation details are not configurable public interface.

### Generation task

`generateKompactSchemas` is a cacheable task that submits KSP2 common processing to a process-isolated Gradle worker exactly once. The worker classpath contains the internal processor and KSP2 embeddable implementation without placing them on consumer runtime or compilation classpaths.

Declared normalized inputs include:

- common schema source roots;
- protocol registry;
- schema compile classpath;
- processor and KSP classpaths;
- plugin, generator, annotation, runtime, and runtime-interface versions;
- namespace, packet limit, language/API versions, and generator options.

Complete output directories contain generated common Kotlin, C headers, canonical descriptors, and machine-readable reports. KSP caches are Gradle local state and are never published or restored as output artifacts.

Gradle `InputChanges` provide added, modified, and removed schema sources to KSP2. Per-schema output dependencies remain isolating where possible; registries and aggregate indexes are aggregating. Whole output directories remain declared for correct clean and build-cache restoration.

Generation occurs in a task-owned staging workspace. Only complete successful output replaces published output directories. Any validation or generation failure removes published outputs and fails the task, leaving no partial Kotlin, C, descriptor, registry, or report files.

Plugin-owned paths are rooted under:

```text
build/generated/kompact/<namespace>/
build/kompact/<namespace>/
```

The first root contains publishable generated output. The second contains staging and local state. Namespace path segments use the same deterministic sanitization and collision validation as generated public symbols.

### Stable task interface and wiring

The plugin exposes three stable tasks:

- `generateKompactSchemas` creates all generated outputs from one validated descriptor pass.
- `checkKompactSchemas` runs generation and schema, registry, descriptor, and compatibility checks and participates in project `check`.
- `packageKompactCHeaders` creates a deterministic ZIP from generated headers.

`commonMain` receives the generated Kotlin directory through the generation task's output provider. This provider carries task dependencies into every target compile and the IDE model. Source archive tasks consume the same provider and include generated public declarations.

`packageKompactCHeaders` consumes the generated-header provider. The plugin exposes its deterministic ZIP through a consumable `kompactCHeaders` variant. When the project already applies Maven Publish and explicitly enables C publication, the plugin attaches the same ZIP to the KMP root publication with classifier `c-headers`. The plugin never applies Maven Publish itself.

Compilation, checking, source archives, C packaging, and publication consume task providers rather than raw build-directory strings. Generated consumer files remain build outputs and are never checked into source control.

### Publication

One macOS release job publishes every coordinate once:

- runtime and annotation root metadata, JVM, explicit Android, `iosArm64`, and `iosSimulatorArm64` artifacts;
- source and documentation artifacts;
- Gradle plugin marker and implementation artifacts;
- the internal processor implementation dependency;
- each explicitly enabled C-header classifier.

Publication first targets a disposable Maven repository. Real JVM, Android, iOS, Gradle-plugin, and C-header consumers resolve and exercise those artifacts before external publication. Android publication is configured explicitly. One host owns all root and target publications to prevent duplicate coordinates.

### Required gates

Gradle TestKit fixtures prove:

- clean generation and a second `UP-TO-DATE` run;
- added, modified, renamed, and removed schema incrementality;
- stale-output cleanup;
- parallel task execution;
- configuration-cache reuse;
- Gradle isolated-project compatibility;
- relocated `FROM-CACHE` restoration;
- deterministic repeated and relocated outputs;
- validation failures with stable diagnostics and no published output.

Target fixtures compile generated code for JVM, Android, `iosArm64`, and `iosSimulatorArm64`. macOS runs iOS simulator tests and links device artifacts. Gradle IDE import resolves generated `commonMain` declarations without manual path configuration.

Publication fixtures inspect root and target metadata, generated source archives, plugin dependency isolation, C ZIP contents, classifier and variant resolution, checksums, and reproducibility. They resolve real disposable-repository consumers for every supported target and artifact.

Missing KMP or `commonMain`, version mismatch, namespace mismatch, packet-limit mismatch, missing registry, output collision, and unsupported target wiring fail closed with stable diagnostics.

## Alternatives

Combining annotations with runtime was rejected because schema authoring and runtime release cycles would be coupled. Embedding processor code directly in the plugin was rejected because symbol processing needs an independently testable owner. Publishing the processor as a supported public interface was rejected because it creates a second path that bypasses Gradle ownership. Multiple namespaces per module were rejected because source selection, output ownership, and publication become ambiguous. Configurable output directories and checked-in generated code were rejected because they expand cache and cleanup behavior and duplicate the schema source of truth. In-daemon KSP execution was rejected because processor classloaders and memory would share the Gradle daemon. Multi-host publication was rejected because root and target coordinates can race or diverge.

## Risks

Four modules and an internal published processor dependency increase release plumbing. Process-isolated workers add startup time. Exact version alignment requires coordinated releases of runtime, annotations, processor, and plugin. One namespace per module may create more modules in applications serving several BLE protocols. Removing outputs on validation failure can temporarily remove IDE symbols until the schema is corrected. Attaching consumer-generated C headers to KMP publications requires careful publication ordering and reproducibility checks.

## Migration

No Gradle or Maven interface has been released. Implementation must introduce all four modules, the typed extension, stable tasks, provider-based wiring, staged outputs, local-state caches, variants, publications, and TestKit fixtures together. After release, plugin ID, Maven coordinates, extension properties, task names, consumable variant, classifier, output ownership, and version-alignment rules are public compatibility contracts. Later module consolidation or direct processor support requires a documented migration and SemVer impact.
