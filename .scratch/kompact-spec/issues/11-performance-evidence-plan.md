---
Type: research
Labels:
  - wayfinder:research
  - scope:perf
  - scope:testing
  - kind:evidence
Status: open
Blocked by:
  - "10 cross-platform testing model"
---

# Ticket 11 — Performance-evidence plan

## Question (research subagent)

Ticket 10 locked the **what** of zero-alloc verification (a CI gate that fails the build on regression; per-platform allocation profiling; the alloc counter delivered as `expect/actual` per ticket 03; test ABI locked via `binary-compatibility-validator`). This ticket gathers the **exact how** — re-derived from high-trust primary sources; the reference `docs/research` perf note is explicitly ignored. Resolved by a research subagent.

Produce a concrete, copy-paste-ready evidence plan as `.scratch/kompact-spec/research/perf-evidence-plan.md` (findings file to be folded into this ticket on resolution):

1. **JVM (Android/JVM)**: the strongest, most stable zero-allocation assertion for a Kotlin scalar `readBits`-style read — Kotlin allocation-instrumenter, JMH `-prof gc`, async-profiler `-e alloc`, or `-XX:+PrintGCDetails`+perf counters. Pick the one with a stable "0 allocations on success" signal under KMP/Gradle; give the exact Gradle/JMH invocation and a minimal test snippet that fails on >0 allocations.
2. **iOS (Kotlin/Native iosArm64 + Simulator)**: Allocations instrument / `malloc` zone / `malloc_count` tracking asserting 0 allocations on the read path; Swift call-site considerations; XCTest integration; exact invocation.
3. **expect/actual counter (ticket 03)**: how the per-platform alloc counter is implemented so its reset/measure does NOT itself count as a read-path allocation (reset outside the timed region).
4. **Baseline methodology**: 0 allocations per scalar read (strict) vs no-regression-vs-baseline-commit — recommend one with rationale.

Output: profiler flags, a minimal failing-on-regression test snippet per platform, the CI gate command, and the baseline rule. Informed by 03 (zero-alloc contract + value-class representation) and 10 (CI gate + expect/actual counter + ABI lock).
