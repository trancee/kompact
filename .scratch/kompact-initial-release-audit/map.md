---
Type: wayfinder:map
Status: active
Labels:
  - wayfinder:map
---

# Wayfinder Map: Kompact initial-release audit

## Destination

Produce a decision-resolved, implementation-ready roadmap for hardening
Kompact before its first supported production release. The route covers every
maintained product and delivery surface in this repository; this map plans
changes but does not implement them.

## Notes

- **Audit stance:** audit afresh. The Constitution, current source, and accepted
  ADRs are operative evidence; older `.scratch/` maps are historical inputs
  that may be challenged or superseded when inconsistent with current evidence.
- **Release context:** the user says no published artifact has been adopted or
  used in production. The README says `0.6.1`; research verified Maven Central
  has published `0.7.0` while the checkout is `0.8.0-SNAPSHOT`. Treat consumer
  compatibility as unconstrained for redesign, but never overwrite published
  coordinates/versions; choose a forward-moving first supported release.
- **Hard performance requirement:** byte-oriented read/write/encoding
  operations must have no per-operation library allocation after caller setup,
  using preallocated caller-owned buffers. UTF-8 `String` creation/consumption,
  fresh wrapper objects, and explicit owned copies/snapshots are excluded;
  caller-owned workspace is required for variable-width repeat indexes. Each
  current target needs independent evidence before the claim applies there.
- **API direction:** borrowed/lazy views remain the default with explicit
  copying for ownership; unchecked primitives remain available only as a
  clearly separated and explicitly unchecked surface.
- **Current target matrix:** JVM, Android JVM, iOS Arm64, iOS Simulator Arm64,
  and Android Native Arm64. The no-allocation claim requires per-target proof.
- **Scope:** runtime and wire format, public API, KSP generation and Gradle
  integration, platform support/publication, tests, docs, CI/build/release,
  and security boundaries. Exclude unrelated local mutation-tooling internals
  unless they affect product gates.
- **Skills:** wayfinder, grilling, domain-modeling, research, and applicable
  Kotlin/KMP/benchmark skills.
- **Tracker:** local Markdown under `.scratch/`, as specified by
  [`docs/agents/issue-tracker.md`](../../docs/agents/issue-tracker.md).

## Decisions so far

- [Setting the audit charter](issues/01-audit-charter.md): audit all maintained
  product and delivery surfaces afresh, using current authoritative sources
  while reconciling older maps; optimize for correctness, API clarity, platform
  consistency, and the user-mandated hard allocation goal.
- [Defining the steady-state allocation contract](issues/02-steady-state-zero-allocation-contract.md):
  no per-operation allocations after caller setup on preallocated caller-owned
  buffers, across reads and writes; no fresh result wrappers; explicit owned
  copies/snapshots are outside that guarantee.
- [Choosing borrowed and unchecked API defaults](issues/03-borrowed-and-unchecked-api-policy.md):
  borrowed/lazy data remains the default with explicit copy-to-own, and
  unchecked primitives must be visibly separated and clearly documented.
- [Keeping the current target matrix](issues/04-supported-target-matrix.md):
  retain current JVM, Android JVM, iOS Arm64/Simulator, and Android Native
  Arm64 targets, with evidence on every claimed target.
- [Reconciling schema-versioning evidence](issues/05-schema-evolution-evidence.md):
  ADR-0002 is current; the old scratch-map header decision is historical.
  Current framing is versionless, strict, positional, and lacks the old
  uniform-prefix/additive-skip contract. See [research](research/schema-evolution-evidence.md).
- [Assessing allocation-contract feasibility](issues/06-platform-allocation-feasibility.md):
  the required caller-buffer primitive path cannot be met by current wrapper
  APIs; byte-oriented operations and caller-supplied repeat workspaces are
  needed. Android Native proof requires a validation spike. See
  [research](research/allocation-feasibility.md).
- [Auditing runtime and consumer APIs](issues/07-runtime-public-api-audit.md):
  generated view equality, unchecked field-value truncation, and redundant
  nested-region API need explicit pre-release contracts. See
  [research](research/runtime-api-audit.md).
- [Auditing code generation and build integration](issues/08-codegen-and-build-audit.md):
  KSP round-safety and cache happy paths are tested; reflective compatibility,
  unsupported targets, and some diagnostics remain open. See
  [research](research/codegen-build-audit.md).
- [Auditing quality, docs, and release flow](issues/09-quality-docs-and-release-audit.md):
  published docs lag current artifacts; allocation/Native proof and several
  release gates are missing or asymmetric. See
  [research](research/quality-docs-release-audit.md).

## Not yet specified

The audit has graduated the currently visible questions into open child
tickets. New decision questions may still emerge as those tickets are resolved.

## Out of scope

- Implementing the roadmap in this planning effort.
- Building BLE transport behavior or other application-specific protocol
  features beyond Kompact's serialization contract.
- Changes to unrelated local mutation-testing tooling that do not affect
  Kompact's supported product or its delivery gates.
