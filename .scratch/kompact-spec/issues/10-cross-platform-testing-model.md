---
Type: grilling
Status: resolved
Labels:
  - scope:testing
  - scope:perf
  - kind:verification
Blocked by:
  - "03 value-class representation"
  - "08 runtime error model"
  - "09 versioning & schema evolution"
Decides:
  - "11 performance-evidence plan"
---

# Ticket 10 — Cross-platform testing model

## Question

The Destination (map §Destination) requires the spec to lock **"the cross-platform testing model"** — currently untracked in the fog (gap). Kompact must be verified end-to-end on Android/JVM + iOS as Kotlin/Native (`iosArm64`, `iosSimulatorArm64`) per ticket 03's platforms, and the ticket-03 **zero-alloc / zero-copy read** claim must be substantiated, not merely asserted. How is the testing model shaped? Informed by 01–09.

Decide:
- **Test categories**: unit round-trip (struct → wire → decode → equal, per platform); property-based (random struct → wire → decode → equality; fuzzed lengths, empty/nested/repeated edge cases); cross-version compatibility matrix (09: newer-writer/old-reader skip of trailing fields, old-writer/newer-reader defaults, version-skew `UnsupportedSchemaVersion`, malformed-prefix `BadLengthPrefix`/`TruncatedNested`); and a zero-alloc assertion test on the `readBits`-style scalar read path.
- **Zero-alloc substantiation**: how is the ticket-03 zero-alloc claim *measured and asserted* — what tool, what metric, and is it a CI gate (a test that fails the build on regression) or only documentation? JVM candidate: allocation profiling (e.g. JMH + allocation profiling, or `-XX:+PrintGCDetails`/async-profiler alloc counter) asserting 0 allocations on a scalar read. iOS candidate: Allocations instrument / malloc-zone tracking asserting 0 allocations on the read path. The alloc counter itself is `expect`/`actual` per ticket-03's representation rule.
- **Cross-platform harness**: shared `commonTest` (KMP) run on JVM + iosArm64 + iosSimulatorArm64; platform-specific measurement glue as `expect`/`actual` per ticket 03. Does the KMP test ABI get locked (e.g. via `binary-compatibility-validator`)?

Consequence: 10 sets what counts as "verified" for the spec. The detailed measurement *tooling* (exact profiler flags, benchmark harness schema) is a follow-on research subagent → **performance-evidence plan (ticket 11)**. Module split (12) decides whether tests ship in the published artifact.

## Answer

User decided: adopt the recommended option on both forks.

**Test categories — all four.**
- **(a) Round-trip unit, per platform**: `struct → write → ByteArray → read → assert field-per-field equal`; runs in `commonTest` on JVM + `iosArm64` + `iosSimulatorArm64`.
- **(b) Property-based**: random struct generation (randomized widths, nested depth, repeat counts, enum codes incl. unknown) → serialize → deserialize → assert equality; fuzzed lengths/empty/nested/repeated/edge cases via a KMP property-testing dependency (e.g. `kotlin-property`-style or `quicktheories`-equivalent on both platforms).
- **(c) Cross-version compatibility matrix**: exercises ticket 09's additive model in both directions — newer-writer/old-reader (older reader skips trailing unknown *length-delimited* fields via the uniform prefix, 09), old-writer/newer-reader (missing trailing fields → declared defaults), version-skew → typed `UnsupportedSchemaVersion` (06+09), and malformed-prefix/malformed-nested → typed `BadLengthPrefix`/`TruncatedNested` via ticket 08's never-throwing typed results. This is the correctness surface that 09's evolution rules buy — it must exist or the rules are unenforced.
- **(d) Zero-alloc assertion**: a test that reads a scalar via the `readBits`-style hot path and asserts 0 platform allocations (see measurement below).

Rejected: "round-trip + property only" — drops (c) the compat matrix (the whole point of 09) and (d) the zero-alloc assertion (the whole point of 03). Without these two, the framework's core guarantees are untested.

**Measurement + CI gate + harness — CI gate (fail-the-build on regression), per-platform alloc profiling.**
- **Gate**: a test that **fails the build** on any zero-alloc regression on the scalar-read hot path. The 03 zero-alloc claim is the framework's core value proposition; documentation-only is unenforced and meaningless.
- **JVM**: allocation profiling asserting 0 allocations on a scalar read — Kotlin allocation-instrumenter, JMH `-prof gc`, async-profiler `-e alloc`, or `-XX:+PrintGCDetails`+perf counter; whichever yields a stable, non-allocating-success assertion under KMP/Gradle. Exact choice → perf-evidence plan (11).
- **iOS (Kotlin/Native iosArm64 + Simulator)**: Allocations instrument / `malloc` zone / `malloc_count` tracking asserting 0 allocations on the read path; XCTest integration; exact invocation → perf-evidence plan (11).
- **Alloc counter as `expect/actual` (ticket 03)**: the per-platform allocation counter is delivered as `expect/actual` so the zero-alloc assertion test lives in shared `commonTest`; the counter itself must not count as a read-path allocation (its reset/measure is outside the timed read region).
- **Harness**: KMP `commonTest` run on JVM + `iosArm64` + `iosSimulatorArm64`; platform-specific measurement glue as `expect`/`actual`.
- **ABI lock**: the test/assertion ABI is locked via `binary-compatibility-validator` to prevent platform drift between the JVM and iOS test surfaces.

Rejected: "documentation only (no alloc assertion, no CI gate)" — the 03 zero-alloc claim becomes unenforced.

**Tradeoff accepted.** A 4-category model with a per-platform zero-alloc CI gate is heavier than "round-trip + property" — but 03 (zero-alloc) and 09 (compatibility) are the framework's defining properties; they must be *tested*, not documented. Locking the test ABI via `binary-compatibility-validator` is a real constraint: the testing surface becomes a published, version-checked contract (tests must evolve with the same discipline as the public API).

**Consequences.**
- 11 performance-evidence plan: 10 locked the *what* (CI gate + per-platform profiling + expect/actual counter + ABI lock); 11 gathers the *exact how* (profiler flags, a minimal failing-on-regression snippet, baseline rule) via a research subagent — re-derived from primary sources, not the ignored reference doc.
- 12 module split: informed — tests/infra live in `commonTest` (not published API); the split decision (12) determines whether test or benchmark artifacts ship.

## References
- ticket 03 (zero-alloc read contract; value-class representation for the alloc counter) 
- ticket 06 (typed runtime error types; never-throw) 
- ticket 08 (typed results, fail-fast on the read path) 
- ticket 09 (evolution rules the compat matrix exercises) 
