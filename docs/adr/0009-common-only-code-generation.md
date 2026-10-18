# ADR-0009 — Common-only code generation

- **Status:** Accepted
- **Date:** 2026-10-18
- **Tags:** codegen, kmp, api, bc-break
- **Supersedes in part:** [ADR-0003](0003-kmp-consumer-enablement.md) (the
  `jvmCommon` source-set hierarchy and the `-Xexpect-actual-classes` flag) and
  [ADR-0008](0008-framed-generated-views.md) (routing generated platform
  sources into per-target source sets)
- **Amends:** [ADR-0006](0006-immutable-default-models.md) (generated type names)
- **Amended by:** [ADR-0011](0011-inferred-nested-fields-and-checked-fixed-decode.md)
  (fixed-layout views gain a checked `decode` factory)

## Context

Kompact originally modeled its runtime result types and generated fixed-layout
views as `expect`/`actual` value classes, because `@JvmInline` was treated as
JVM-only. Every fixed-layout schema therefore required a hand-written
`expect value class` that repeated the `create` and `copy` signatures, and the
processor emitted one common file plus one `actual` file per platform family.
Framed schemas already used a regular schema class, but their generated view
was still an `expect` declaration with identical JVM, iOS, and Android Native
actuals.

This shape had several costs. Consumers needed `-Xexpect-actual-classes`
(a Beta feature), the Gradle plugin mapped every Kotlin target to a
platform-specific output directory and rejected targets it did not know, the
processor exposed a `kompact.generate` mode option, and default `copy`
arguments could only be declared on the `expect` side.

`kotlin.jvm.JvmInline` is an optional expectation that common code may use; it
marks the value class as inline on the JVM and is ignored by Kotlin/Native. The
runtime moved to common `@JvmInline value class` declarations in commit
`ae91458`, so generated code no longer needs platform actuals either.

## Decision

1. Neither the runtime nor generated code uses `expect`/`actual`.
2. Each `@KompactModel` declaration produces exactly one generated common
   Kotlin file, `<Name>View.kt`, containing ordinary declarations.
3. Fixed-layout schemas are regular annotated classes, like framed schemas.
   A schema `SensorFrame` generates `@JvmInline public value class
   SensorFrameView(public val raw: ByteArray)` with its minimum-size `init`
   check, companion `create`, `copy` with `= this.field` defaults, and typed
   getters. `@KompactModel(mutable = true)` also generates
   `MutableSensorFrameView`. The same file contains the reusable
   `SensorFrameViewHolder` operations and the `internal`
   `encodeSensorFrameView` function.
4. Framed schemas generate one regular `<Name>View` class in the same single
   common file, with default arguments declared directly.
5. A schema declared with `expect` is a processor error that tells the user to
   remove `expect` and use the generated view.
6. The `kompact.generate` processor option and its generation modes are
   removed.
7. The `ch.trancee.kompact.codegen` plugin registers its output directory only
   with `commonMain`. It no longer inspects Kotlin targets, adds no compiler
   arguments, and passes no processor options.

## Alternatives considered

- **Keep `expect`/`actual` for generated views.** This preserves the old
  source shape but keeps the Beta compiler flag, the target allowlist, and
  three identical actual bodies per schema. Rejected.
- **Keep the user-written `expect value class` for fixed-layout schemas and
  generate a non-`expect` implementation beside it.** The generated
  declaration would collide with the user's declaration name. Rejected in
  favor of the framed `<Name>View` naming rule.

## Consequences

- Any Kotlin Multiplatform target that the runtime publishes can consume
  generated code; the plugin itself has no target restrictions.
- Generated output is a single directory registered with `commonMain`, which
  removes per-target source-directory wiring and simplifies build caching.
- Fixed-layout and framed schemas follow one naming rule: `<Name>View` and,
  for mutable fixed-layout schemas, `Mutable<Name>View`. Holders are named
  `<Name>ViewHolder`.
- `copy` default arguments are generated with the view, so callers no longer
  repeat signatures.
- The processor's public ABI is unchanged; generated consumer source shapes
  change and require migration.

## Migration

This is a pre-1.0 breaking change:

1. Replace each fixed-layout `@KompactModel expect value class Name(raw)` with
   a regular `@KompactModel class Name` whose annotated `val` properties carry
   placeholder initializers. Delete the hand-written companion and `copy`
   signatures.
2. Rename usages of fixed-layout model types from `Name` to `NameView`,
   `MutableName` to `MutableNameView`, and `NameHolder` to `NameViewHolder`.
3. Remove `-Xexpect-actual-classes` and any `kompact.generate` option from
   consumer builds that only needed them for Kompact.
4. Framed schemas keep their `<Name>View` names; delete any hand-written
   `expect` declarations of those views.
