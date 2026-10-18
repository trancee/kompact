# Documentation

Pick a page by the job you need to do. The root [`README.md`](../README.md)
gives the project overview. This page lists every guide, ADR, and research
note.

## Use Kompact

| Goal | Guide |
| --- | --- |
| Build and decode your first frame | [Getting started](getting-started.md) |
| Add Kompact to a Kotlin or KMP project | [Consume Kompact](how-to/consume-from-another-project.md) |
| Define a fixed-layout schema | [Fixed-layout models](how-to/define-message.md) |
| Define a sequential framed schema | [Framed models](how-to/define-framed-schema.md) |
| Read and write strings, blobs, nested regions, or repeats | [Long-form payloads](how-to/long-form-payloads.md) |
| Handle malformed input | [Decode errors](how-to/handle-decode-errors.md) |
| Pass frames to and from a BLE transport | [BLE integration](how-to/integrate-ble.md) |
| Measure runtime performance on a host | [Benchmark the runtime](how-to/benchmark-runtime.md) |
| Find a signature, parameter, or error definition | [API reference](api-reference.md) |
| Understand the wire format and design trade-offs | [Architecture](architecture.md) |

The [how-to index](how-to/README.md) groups the task guides on one page.

## Contribute

Start with [`CONTRIBUTING.md`](../CONTRIBUTING.md). It covers the repository
setup, the local checks, the documentation rules, and pull requests. These
pages hold the details:

- [CI checks and local commands](ci.md) lists the CI jobs for each host and
  the exact Gradle tasks they run.
- [Mutation testing disposition](mutation-testing-disposition.md) records the
  current MutFlow results for the runtime and the KSP processor, and how each
  earlier surviving or timed-out mutant was resolved.

### Design decisions

Each ADR records one decision about the public API, the wire format, the
build, or the release flow:

| Decision | ADR |
| --- | --- |
| Mutable view setters (superseded) | [ADR-0001](adr/0001-mutable-view-classes-with-write-through-setters.md) |
| Defer schema versioning | [ADR-0002](adr/0002-defer-versioning-surface-to-v2.md) |
| KMP consumer and publication support | [ADR-0003](adr/0003-kmp-consumer-enablement.md) |
| Release pull-request automation | [ADR-0004](adr/0004-release-pr-automation.md) |
| Full-domain `LongResult` | [ADR-0005](adr/0005-full-domain-long-result.md) |
| Immutable model views by default | [ADR-0006](adr/0006-immutable-default-models.md) |
| Pre-1.0 versioning | [ADR-0007](adr/0007-pre-1-release-versioning.md) |
| Sequential framed views | [ADR-0008](adr/0008-framed-generated-views.md) |
| Common-only code generation | [ADR-0009](adr/0009-common-only-code-generation.md) |
| Minimal checked-result API | [ADR-0010](adr/0010-minimal-checked-result-surface.md) |
| Inferred nested fields and checked fixed-layout decode | [ADR-0011](adr/0011-inferred-nested-fields-and-checked-fixed-decode.md) |
| Kotlin minor-line compatibility for the Gradle plugin | [ADR-0012](adr/0012-kotlin-minor-line-compatibility.md) |
| Typed cursor status and caller-contract errors | [ADR-0013](adr/0013-typed-status-and-caller-contract-errors.md) |
| detekt static analysis | [ADR-0014](adr/0014-detekt-static-analysis.md) |
| Generated MutFlow adapters | [ADR-0015](adr/0015-generated-mutation-adapters.md) |

### Technical research

These notes record measurements and implementation evidence for contributors
who investigate the build or performance. They are not setup guides.

- [KSP common-schema generation across Android and iOS](research/ksp-kmp-generation.md)
- [Allocation and boxing measurement on Android and iOS](research/allocation-boxing-measurement.md)
- [Physical runtime validation records](research/physical-runtime-validation.md)
- [Fixed-repeat checkpoint work](research/repeat-index-benchmark.md)
- [Generated-holder decoding: initial host comparison](research/generated-holder-benchmark.md)
- [Aligned count prefixes and small generated holders](research/aligned-count-prefix-benchmark.md)
- [In-place nested writes in `KompactWriter`](research/writer-nested-benchmark.md)
- [Faster bit reads in `KompactRuntime`](research/bit-primitive-fast-paths.md)

## For coding agents

The agent policy uses a short, directive style and lives apart from the human
guides:

- [`AGENTS.md`](../AGENTS.md) and [`CONSTITUTION.md`](../CONSTITUTION.md) define
  the rules for working in and reviewing the repository.
- [`docs/agents/`](agents/) holds the issue-tracker conventions, the
  domain-document guidance, the triage labels, and the agent quick start.
- [`.omp/AGENT-USAGE.md`](../.omp/AGENT-USAGE.md) describes the
  mutation-testing workflow.
