---
Type: grilling
Status: open
Labels:
  - scope:testing
  - scope:perf
  - kind:verification
Blocked by:
  - "03 value-class representation"
  - "08 runtime error model"
  - "09 versioning & schema evolution"
---

# Ticket 10 — Cross-platform testing model

## Question

The Destination (map §Destination) requires the spec to lock **"the cross-platform testing model"** — currently untracked in the fog (gap). Kompact must be verified end-to-end on Android/JVM + iOS as Kotlin/Native (`iosArm64`, `iosSimulatorArm64`) per ticket 03's platforms, and the ticket-03 **zero-alloc / zero-copy read** claim must be substantiated, not merely asserted. How is the testing model shaped? Informed by 01–09.

Decide:
- **Test categories**: unit round-trip (struct → wire → decode → equal, per platform); property-based (random struct → wire → decode → equality; fuzzed lengths, empty/nested/repeated edge cases); cross-version compatibility matrix (09: newer-writer/old-reader skip of trailing fields, old-writer/newer-reader defaults, version-skew `UnsupportedSchemaVersion`, malformed-prefix `BadLengthPrefix`/`TruncatedNested`); and a zero-alloc assertion test on the `readBits`-style scalar read path.
- **Zero-alloc substantiation**: how is the ticket-03 zero-alloc claim *measured and asserted* — what tool, what metric, and is it a CI gate (a test that fails the build on regression) or only documentation? JVM candidate: allocation profiling (e.g. JMH + allocation profiling, or `-XX:+PrintGCDetails`/async-profiler alloc counter) asserting 0 allocations on a scalar read. iOS candidate: Allocations instrument / malloc-zone tracking asserting 0 allocations on the read path. The alloc counter itself is `expect`/`actual` per ticket-03's representation rule.
- **Cross-platform harness**: shared `commonTest` (KMP) run on JVM + iosArm64 + iosSimulatorArm64; platform-specific measurement glue as `expect`/`actual` per ticket 03. Does the KMP test ABI get locked (e.g. via `binary-compatibility-validator`)?

Consequence: 10 sets what counts as "verified" for the spec. The detailed measurement *tooling* (exact profiler flags, benchmark harness schema) is a follow-on research subagent → **performance-evidence plan (ticket 11)**. Module split (12) decides whether tests ship in the published artifact.
