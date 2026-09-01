---
Type: research
Status: resolved
Labels: wayfinder:research
Findings: ../research/generation-strategy.md
---

## Question

`PROMPT.md` §2 says schemas are "generated via an annotation processor or compiler plugin"; §3 shows manual-looking getters and says "how the boilerplate will eventually be automated" — i.e. manual-first with a future generator that emits the §3 getter style from `@KompactField` annotations.

Which code-generation approach can produce the Phase 3 common value-class getters from `@KompactField` annotations for a KMP module consumed by Android/JVM and `iosArm64` / `iosSimulatorArm64`, with deterministic output, build-cache reuse, IDE visibility, and incremental processing? Compare KSP (incl. KMP common-generation caveats), Kotlin compiler plugins (K2), and manual — and recommend one, with the caveat that a generator is not required to ship Phase 3 but the chosen strategy must not paint future automation into a corner.

## Answer

**Decision: KSP (Kotlin Symbol Processing), KSP 2.3.9+ on Kotlin 1.9+.** KSP generates complete value-class source files into `commonMain` with deterministic output, incremental processing, and Gradle build-cache reuse. K2 compiler macros are explicitly experimental (opt-in, not production-ready for a multi-target KMP library) and are rejected.

**Critical boundary:** KSP cannot inject into existing source files, so the generator emits *whole* `value class` declarations (the `@KompactField` getters / setters) rather than patching hand-written ones. `PROMPT.md` §3's "manual-looking getters" are therefore the generator's output, deliberately kept human-readable so automation later replaces them 1:1.

**Caveat (see 03):** KSP generates common `expect` source by default; the per-platform `actual` value classes still need documented source-set wiring. Acceptable for the v1 spec but must be explicit.

Findings: [../research/generation-strategy.md](../research/generation-strategy.md).
