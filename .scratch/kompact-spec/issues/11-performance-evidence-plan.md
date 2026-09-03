---
Type: research
Status: resolved
Labels:
  - wayfinder:research
  - scope:perf
  - scope:testing
  - kind:evidence
Blocked by:
  - "10 cross-platform testing model"
Decides:
  - "12 module split & publication"
---

# Ticket 11 — Performance-evidence plan

## Question (research subagent)

Ticket 10 locked the **what** of zero-alloc verification (a CI gate that fails the build on regression; per-platform allocation profiling; the alloc counter as `expect/actual` per ticket 03; test ABI locked via `binary-compatibility-validator`). This ticket gathered the **exact how** — re-derived from primary sources; the reference `docs/research` perf note is ignored. Resolved by a research subagent (`PerfEvidenceResearch`), findings written to [research/perf-evidence-plan.md](research/perf-evidence-plan.md), then **verified by source check**.

## Answer

Resolved by the research subagent's findings, then **verified against primary sources — 2 subagent claims corrected** (see VERIFICATION NOTE; the findings file is the subagent draft, this ticket is authoritative):

**(a) JVM/Android — allocation profiling, NOT `assertNoAllocations`.**
Correction: `assertNoAllocations` (kotlin-test) is a Kotlin/Native (iOS) API via the allocation-instrumentation runtime; on the JVM it is experimental/unsupported. For the JVM zero-alloc assertion:
- **Stronger, stable choice**: JMH `-prof gc` (GC profiler) over a `@Benchmark` of the scalar read; assert `GC: 0 allocations` / alloc count 0. Run: `java -jar benchmarks.jar -prof gc -jvmArgs "-XX:+UseSerialGC -Xmx64m -XX:-TieredCompilation"`. `-prof gc` reports `GC: <cnt> allocations` per operation; 0 = zero-alloc.
- alt: async-profiler `-e alloc` (object-allocation profiling), assert 0 alloc events on the read (`profiler.sh -e alloc -d 10s --test ...`),
- alt: `-XX:+PrintGCDetails` + parse allocation counters; Android: Android Studio "Record Java/Kotlin allocations".
- Kotlin's internal compiler `AllocationInstrumenter` exists (JetBrains/kotlin `compiler/test-infrastructure`) but is test infra, not a public `kotlin.test` assertion on the JVM.

**(b) iOS (Kotlin/Native iosArm64 + Simulator) — allocation instrumentation, NOT `malloc_zone_statistics`.**
Correction: `malloc_zone_statistics` / `malloc_default_zone` counts only C `malloc` allocations, **not** Kotlin/Native runtime/page-allocator allocations — Kotlin/Native uses its own page-based allocator (per verification: Kotlin Slack/forums; Kotlin docs native-memory-manager). For the precise zero-alloc assertion:
- `assertNoAllocations { readBits(...) }` via Kotlin/Native **allocation-instrumentation runtime** — `kotlin.native.enableAllocationInstrumentation=true` in `gradle.properties` (or `-Xallocator=debug` compiler flag). This instruments the KN allocator and counts KN-managed allocations. Source: Kotlin docs (native memory manager); `kotlin.test.assertNoAllocations`.
- Supporting: `GC.collect()` + `GC.lastGCInfo()!!.memoryUsageAfter["heap"]!!.totalObjectsSizeBytes` (kotlin.native.internal) for a "no heap growth" assertion — source: Kotlin native-memory-manager docs.
- Supporting: Instruments Allocations (system-level dev inspection); CI via `xcodebuild test -project ... -scheme ... -destination 'platform=iOS Simulator,...'`.

**(c) `expect/actual` alloc counter (ticket 03) — reset/measure OUTSIDE the timed read.**
- common: `expect class AllocationCounter { fun reset(); fun count(): Long }`.
- JVM `actual`: a `@JvmInline value class` backed by an JMH/async-profiler snapshot (start profiling → `reset()` → [ readBits scalar read region ] → `count()`); the timed region is the scalar read only.
- iOS `actual`: a plain `actual` value class backed by the alloc-instrumentation counter (or `GC.lastGCInfo` before/after); `reset()`+`count()` wraps the read region **outside** the timed read call (ticket 03: the read call is the untimed zero-alloc path).

**(d) Baseline — strict 0-allocs-per-scalar-read, fail-fast.**
Rigorous: ticket 03's contract is "direct non-nullable concrete scalar reads are zero-alloc," so the assertion is **per-scalar-read, 0 allocations, fail-fast** (build fails on any >0). "No-regression-vs-baseline-commit" is a weaker fallback.

**Sources (verified):** JetBrains/kotlin `AllocationInstrumenter` (compiler test-infra); Kotlin docs `native-memory-manager` (`GC.collect` / `GC.lastGCInfo`); async-profiler (`-e alloc`); OpenJDK/JMH (`-prof gc`); Android Studio "Record Java/Kotlin allocations"; Apple Instruments; Kotlin forums (malloc_zone statistics limitation for KN).

**Consequences.** 12 (module split): the perf-evidence tests/benchmarks live in `commonTest`/`benchmark`, not in the published API (confirmed by the expect/actual counter being test-only infra).

## References
- ticket 03 (zero-alloc read contract; value-class representation for the counter) 
- ticket 10 (CI gate + expect/actual counter + ABI lock) 
