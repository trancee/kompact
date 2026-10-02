# Documentation

Choose a page by the job you need to do. The root
[`README.md`](../README.md) is the project overview; this page is the full map.

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
| Find a signature, parameter, or error definition | [API reference](api-reference.md) |
| Understand the wire format and design tradeoffs | [Architecture](architecture.md) |

## Contribute

Start with [`CONTRIBUTING.md`](../CONTRIBUTING.md) for the repository setup,
local checks, documentation workflow, and pull-request guidance. Use
[`ci.md`](ci.md) for the host-specific CI matrix and exact Gradle tasks.

### Design decisions

The ADRs record decisions that affect the public API, wire format, builds, and
releases:

| Decision | ADR |
| --- | --- |
| Mutable view setters (superseded) | [ADR-0001](adr/0001-mutable-view-classes-with-write-through-setters.md) |
| Defer schema versioning | [ADR-0002](adr/0002-defer-versioning-surface-to-v2.md) |
| KMP consumer and publication support | [ADR-0003](adr/0003-kmp-consumer-enablement.md) |
| Release pull-request automation | [ADR-0004](adr/0004-release-pr-automation.md) |
| Full-domain `LongResult` | [ADR-0005](adr/0005-relax-fail-path-zero-alloc.md) |
| Immutable model views by default | [ADR-0006](adr/0006-immutable-default-models.md) |
| Pre-1.0 versioning | [ADR-0007](adr/0007-pre-1-release-versioning.md) |
| Sequential framed views | [ADR-0008](adr/0008-framed-generated-views.md) |

### Technical research

These notes preserve implementation evidence and are aimed at contributors
investigating the build or performance model; they are not setup guides:

- [KSP common-source generation](research/ksp-kmp-generation.md)
- [How to measure allocation and boxing](research/allocation-boxing-measurement.md)

## For coding agents

The compact, directive-style agent policy is kept separate from the human
guides:

- [`AGENTS.md`](../AGENTS.md) and [`CONSTITUTION.md`](../CONSTITUTION.md) define
  repository execution and review rules.
- [`docs/agents/`](agents/) contains issue-tracker conventions, domain-document
  guidance, triage labels, and the agent quick start.
