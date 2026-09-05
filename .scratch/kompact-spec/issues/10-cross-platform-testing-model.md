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
- **Test categories**: unit round-trip (struct → wire → decode → equal, per platform); property-based (random struct → wire → decode → equality; fuzzed lengths, empty/nested/repeated edge cases); cross-version compatibility matrix (09: newer-writer/old-reader = trailing-field skip, old-writer/newer-reader = defaults, version-skew → `UnsupportedSchemaVersion`, malformed-prefix → `BadLengthPrefix`/`TruncatedNested`/`UnknownEnumCode`); and a zero-alloc assertion test on the `readBits`-style scalar read path.
- **Zero-alloc substantiation**: how is the ticket-03 zero-alloc claim *measured and asserted* — what tool, what metric, is it a CI gate (a test that fails the build on regression) or only documentation?
- **Cross-platform harness**: shared `commonTest` (KMP) run on JVM + iosArm64 + iosSimulatorArm64; platform-specific measurement glue as `expect`/`actual` per ticket 03. Does the KMP test ABI get locked (e.g. via `binary-compatibility-validator`)?

Consequence: 10 sets what counts as "verified." The detailed measurement *tooling* (exact profiler flags) is a follow-on research subagent → **performance-evidence plan (ticket 11)**. Module split (12) decides whether tests ship in the published artifact.

## Answer

User decided: adopt the recommended option on both forks.

**Test categories — all four.**
- **(a) Round-trip unit, per platform**: `struct → write → ByteArray → read → assert field-per-field equal`; runs in `commonTest` on JVM + `iosArm64` + `iosSimulatorArm64`.
- **(b) Property-based**: random struct generation (randomized widths, nested depth, repeat counts, enum codes incl. unknown via ticket 04) → serialize → deserialize → assert equality; fuzzed lengths / empty / nested / repeated / edge cases.
- **(c) Cross-version compatibility matrix**: the correctness surface that ticket 09's evolution rules buy — must exist or the rules are unenforced. Exercises both directions: newer-writer/old-reader (older reader skips trailing unknown *length-delimited* fields via the uniform prefix, 09); old-writer/newer-reader (missing trailing fields → declared defaults); version-skew → typed `UnsupportedSchemaVersion` (06+09); malformed-prefix/malformed-nested → typed `BadLengthPrefix`/`TruncatedNested` via ticket 08's never-throwing typed results.
- **(d) Zero-alloc assertion**: a test that reads a scalar via the `readBits`-style hot path and asserts 0 platform allocations (exact tooling → ticket 11).

Rejected: "round-trip + property only" — drops (c) the compat matrix (the point of 09) and (d) the zero-alloc assertion (the point of 03). Without these two, the framework's core guarantees are untested.

**Measurement + CI gate + harness — CI gate (fail-the-build on regression).**
- **Gate**: a test that **fails the build** on any zero-alloc regression on the scalar-read hot path. Ticket 03's zero-alloc claim is the framework's core value proposition; documentation-only is unenforced and meaningless.
- **Per-platform profiling**: JVM allocation profiling asserting 0 (ticket 11 — JMH `-prof gc` / async-profiler `-e alloc`); iOS allocation instrumentation asserting 0 (ticket 11 — `assertNoAllocations` / alloc-instrumentation runtime).
- **Alloc counter as `expect/actual` (ticket 03)**: per-platform allocation counter delivered as `expect/actual` so the (d) assertion test lives in shared `commonTest`; reset/measure is outside the timed read region so the counter does not charge the read path (ticket 11).
- **Harness**: KMP `commonTest` on JVM + `iosArm64` + `iosSimulatorArm64`.
- **ABI lock**: the test/assertion ABI is locked via `binary-compatibility-validator` to prevent platform drift between the JVM and iOS test surfaces.

Rejected: "documentation only (no alloc assertion, no CI gate)" — ticket 03's zero-alloc claim becomes unenforced.

**Tradeoff accepted.** The four-category model with a per-platform zero-alloc CI gate is heavier than "round-trip + property" — but tickets 03 (zero-alloc) and 09 (compatibility) are the framework's defining properties; they must be *tested*, not documented. Locking the test ABI via `binary-compatibility-validator` is a real constraint: the testing surface becomes a published, version-checked contract (tests must evolve with the same discipline as the public API).

**Consequences.**
- 11 performance-evidence plan: 10 locked the *what* (CI gate + profiling + `expect/actual` counter + ABI lock); 11 gathers the *exact how* (per-platform profiler flags, a minimal failing-on-regression snippet) via a research subagent, verified against primary sources. Informed by 03 + 10.
- 12 module split: informed — tests / benchmarks live in `commonTest` / `benchmark`, not in the published API. Informed by 02 + 10 + 11.

## References
- ticket 03 (zero-alloc read contract; value-class representation for the alloc counter) 
- ticket 06 (typed runtime errors; never-throw) 
- ticket 08 (typed results, fail-fast on the read path) 
- ticket 09 (evolution rules the compat matrix exercises) 
