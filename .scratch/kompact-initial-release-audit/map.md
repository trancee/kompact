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
- [Choosing initial wire-versioning policy](issues/10-schema-versioning-policy.md):
  Kompact remains versionless; applications needing mixed versions define an
  outer envelope and migration policy. Add an illustrative caller-owned
  envelope example, but no Kompact header or helper.
- [Designing the guaranteed byte-oriented API](issues/11-low-allocation-api-shape.md):
  caller-owned bounded cursors and reusable generated holders/workspaces form
  the checked no-allocation path; UTF-8 strings/convenience wrappers may
  allocate, while Android Native proof gates its claim.
- [Validating generated field writes](issues/14-generated-write-validation.md):
  checked/generated writes reject out-of-range values and unknown enum codes
  without mutation; convenience `create`/`copy` throw, while truncation/raw
  codes require an explicit unchecked path.
- [Removing the legacy nested-region API](issues/15-nested-region-api-surface.md):
  remove `NestedRegion`/`NestedRegionResult` and their unique APIs/tests from
  the next artifact; retain Frame/slice as allocating conveniences, with the
  cursor path owning no-allocation nested reads.
- [Defining generated-view equality](issues/13-generated-view-equality.md):
  preserve identity-based equality/hash, document that views are not
  content keys, test fixed/framed behavior, and do not format payload values.
- [Setting the Gradle plugin compatibility boundary](issues/16-gradle-plugin-compatibility-contract.md):
  support only tested Kotlin/KSP pairs; validate KGP at configuration and KSP2
  immediately before task execution with actionable diagnostics.
- [Defining codegen diagnostics](issues/17-codegen-diagnostics-contract.md):
  empty annotated models and resolved invalid fields are errors with no partial
  model output; unresolved symbols may defer and unrelated valid models
  continue.
- [Setting cross-platform release gates](issues/18-cross-platform-release-gates.md):
  PRs test JVM/Android JVM and iOS Simulator; device tests are required before
  release, allocation claims need per-target validated zero counters, and
  Linux/macOS CI checks must be required statuses.
- [Setting artifact and plugin quality gates](issues/19-release-artifact-gates.md):
  every PR dry-runs the `:kompact` bundle; the published Gradle plugin gets a
  Linux JVM ABI check and 100% production line/branch coverage with no
  production exclusions.
- [Aligning release automation with G1](issues/20-release-policy-alignment.md):
  preserve G1; require human-reviewed release PRs for all version commits and
  prohibit post-publication bot commits directly to protected `main`.
- [Selecting the first supported release version](issues/21-published-version-and-first-release.md):
  release `0.8.0` from the current `0.8.0-SNAPSHOT`, leave published `0.7.0`
  immutable, use the root Gradle version as the canonical candidate, and gate
  consumer-doc version drift against that version and the changelog.
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

All identified audit and decision tickets (01–11, 13–21) are resolved. The
implementation work is now decomposed into the ordered task tickets below.
Reopen Wayfinding if implementation evidence exposes a material unanswered
decision; do not silently weaken the resolved contracts.

## Implementation route

The following tickets are the implementation plan, not authorization to start
implementation in this planning effort. Keep one implementation task active
at a time and use TDD for behavior changes.

1. [Building the caller-owned codec path](issues/22-caller-owned-codec-api.md):
   bounded runtime cursors, reusable generated holders and repeat workspace,
   with failure-atomic checked operations and no fresh wrappers.
2. [Enforcing generated API contracts](issues/23-generated-api-contracts.md):
   checked write validation, explicit unchecked APIs, removal of the legacy
   nested-region surface, and tested/documented generated-view identity
   equality.
3. [Hardening KSP and Gradle integration](issues/24-ksp-gradle-contracts.md):
   tested Kotlin/KSP pairs, actionable compatibility failures, and the
   resolved KSP model-diagnostic behavior.
4. [Proving platform behavior and allocation claims](issues/25-platform-proof.md):
   target-specific behavioral tests, allocation measurement with positive
   controls, and required device evidence before release or per-target claims.
5. [Aligning consumer docs and version references](issues/26-consumer-docs-and-version-drift.md):
   public API/schema documentation, explicit copy/unchecked semantics,
   release/version references, and an automated drift check.
6. [Gating artifacts and release governance](issues/27-release-and-ci-gates.md):
   runtime bundle dry-run, plugin ABI/coverage, required CI checks, and
   human-reviewed release PR flow; publish `0.8.0` only after all required
   gates pass.

## Out of scope

- Implementing the roadmap in this planning effort.
- Building BLE transport behavior or other application-specific protocol
  features beyond Kompact's serialization contract.
- Changes to unrelated local mutation-testing tooling that do not affect
  Kompact's supported product or its delivery gates.
