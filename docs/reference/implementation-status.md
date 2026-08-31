# Implementation and release status

This page records the verified state of Kompact `0.1.0-SNAPSHOT`. The project is pre-release and has not published external artifacts.

## Implemented targets and artifacts

| Area | Current state |
| --- | --- |
| Runtime and annotations | Kotlin Multiplatform root metadata plus JVM, Android, `iosArm64`, and `iosSimulatorArm64` variants. |
| Generator | One process-isolated KSP2 common pass; source-retained annotations are supplied to the isolated pass through a plugin-owned source stub. |
| Kotlin output | Active and decode-only facades and views; writers only for active versions. |
| C output | Header-only C99 runtime and versioned schema headers; writer API only for active versions. |
| Descriptors and registry | Canonical descriptors, SHA-256 fingerprints, reviewed proposals, lifecycle and history checks, and optional local baselines. |
| Publication verification | Disposable Maven repository consumers for JVM, Android, `iosArm64`, `iosSimulatorArm64`, and multi-translation-unit C99. |
| Compatibility | Runtime, annotations, and Gradle plugin ABI reference dumps; registry and generated C snapshot checks. |

## Automated workflows

| Workflow | Trigger | Evidence |
| --- | --- | --- |
| `CI` | Pull request and `main` push | Clean repository checks and Clang ASan/UBSan conformance. |
| `Android conformance` | Pull request or manual | Connected Android checks on an API 36 x86_64 emulator; a dedicated manifest-driven Android vector harness is not yet present. |
| `Apple targets` | Pull request or manual | iOS simulator tests and device/simulator compilation on macOS. |
| `Big-endian C conformance` | Pull request or manual | Static s390x C build and QEMU execution. |
| `Publication consumer` | Pull request or manual | Disposable publication, four Kotlin targets, multi-unit C99, and the 64 KiB generated-header limit. |
| `Sanitizer fuzz smoke` | Weekly or manual | Fixed-seed Clang ASan/UBSan C harness. |
| `Performance evidence` | Manual | Full JVM benchmark report and 1.10 generated/reference ratio gate. |
| `Physical Android performance` | Manual on `android-reference` | AndroidX Microbenchmark and connected-device metadata. |
| `Physical iPhone build evidence` | Manual on `ios-reference` | Host/device metadata and `iosArm64` benchmark compilation. |

## External release blockers

The repository does not contain the human-owned information required to finish these gates:

- concrete self-hosted runner and device identities to replace `android-reference` and `ios-reference`;
- signed iPhone installation and launch configuration;
- physical iPhone timing-loop execution;
- Instruments allocation capture and positive controls;
- a selected production firmware compiler, version, and target triple;
- independent human review.

The Android physical workflow is executable when a compatible runner adopts the generic label. The iPhone workflow intentionally stops at metadata capture and compilation; it is not physical performance or allocation evidence.

## Performance evidence

The retained workloads are:

| Workload | Packet size | Representative operation |
| --- | ---: | --- |
| Small | 4 bytes | 10-bit cross-byte access at bit 20. |
| Medium | 32 bytes | 17-bit unaligned access at bit 67. |
| Large | 244 bytes | 13-bit nested-element access at bit 1531. |

Shared CI runs a smoke profile. The manual JVM workflow runs the full profile and requires generated read latency no greater than 1.10 times its hand-written reference. Android physical timing uses AndroidX Microbenchmark. iPhone timing and allocation remain blocked as described above.

Performance results from shared hosted runners are informational unless the workflow and ADR identify the environment as blocking.

## Documented file-size deviations

These maintained files exceed Constitution D9's 300-line default but remain below its 500-line hard limit:

| File | Cohesion reason |
| --- | --- |
| `DescriptorBuilder.kt` | Keeps one descriptor construction and validation transaction together. |
| `CGenerator.kt`, `CNestedArrayGenerator.kt` | Keep each header-emission backend transaction together. |
| `GenerateKompactSchemas.kt` | Keeps one cacheable generation and publication transaction together. |
| `GeneratedContractFunctionalTest.kt`, `KompactPluginFunctionalTest.kt` | Keep each registry-backed TestKit fixture with its fingerprints and generated-consumer assertions. |

Splitting these files would duplicate protocol state or introduce a second mutation or fixture seam.

## SemVer and migration state

No Kompact artifact has been externally released. There is no published API or wire migration. Before release, replace generic physical-runner labels with pinned environment metadata, complete the iPhone evidence path, add each selected firmware compiler gate, and complete independent review without weakening existing workflows.

For the implementation decisions, see the [ADR index](../adr/). For user configuration, see the [Gradle plugin reference](gradle-plugin.md).
