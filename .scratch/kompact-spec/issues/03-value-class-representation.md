---
Type: research
Status: resolved
Labels: wayfinder:research
Findings: ../research/value-class-representation.md
---

## Question

`PROMPT.md` §1 wants multiplatform value classes wrapping a `ByteArray` with zero-allocation reads. The accepted resolution is: generated `expect/actual value class` declarations, with `@JvmInline` on the JVM `actual`.

How must the `expect` / `actual` value-class declarations be shaped so that (a) the common `expect` can omit `@JvmInline` while the JVM `actual` carries it, (b) Kotlin/Native represents the view class soundly (boxed where unavoidable, unboxed at direct concrete call sites), and (c) the verified zero-allocation call shape is: a non-null view held in a local of its concrete generated type, over a caller-owned `ByteArray`, with a direct scalar `val` read returning a primitive — and no generic, interface, nullable, reflection, collection, or bridge boundary inside the measured read? Name the exact boundaries where boxing is unavoidable so the spec can forbid them on the hot path.

## Answer

**Decision: `expect` in commonMain + `@JvmInline actual` per platform.** The common declaration is `expect value class Foo(val raw: ByteArray)` with **no** `@JvmInline` — the annotation is JVM-stdlib-only and errors on non-JVM targets. Each platform source set carries `actual value class`: `@JvmInline actual value class Foo` in `jvmMain`; plain `actual value class Foo` in `iosArm64Main` and `iosSimulatorArm64Main`.

**Cross-platform behavior:** Kotlin/Native renders value classes as Swift-value structs; wrapper allocation (boxing) occurs only at type-erasure boundaries — generics, nullable (`Foo?`), interface/`Any`-typed parameters, and ABI-crossing returns. On the JVM, `@JvmInline` is what unboxes at direct call sites.

**Hot-path guardrail:** the zero-allocation read contract covers only direct, non-nullable, concrete-typed scalar reads over a caller-owned `ByteArray`. The spec must forbid generic / interface / nullable / `Any`-typed usage on the measured path.

This reconciles the `PROMPT.md` §1 prohibition (hand-written common API, no `@JvmInline`) with the JVM value-class contract (generated JVM `actual` carries `@JvmInline`) and rides on the [generation-strategy](02-generation-strategy.md) decision.

Findings: [../research/value-class-representation.md](../research/value-class-representation.md).
