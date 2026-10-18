# ADR-0009: Common-only code generation

- Status: Accepted
- Date: 2026-10-18
- Tags: codegen, kmp, api, bc-break
- Supersedes in part: [ADR-0003](0003-kmp-consumer-enablement.md) (the
  `jvmCommon` source-set hierarchy and the `-Xexpect-actual-classes` flag) and
  [ADR-0008](0008-framed-generated-views.md) (routing generated platform
  sources into source sets for each target)
- Amends: [ADR-0006](0006-immutable-default-models.md) (generated type names)
- Amended by: [ADR-0011](0011-inferred-nested-fields-and-checked-fixed-decode.md)
  (fixed-layout views gain a checked `decode` factory)

## Context

Kompact first modeled its runtime result types and its generated fixed-layout
views as `expect` and `actual` value classes, because it treated `@JvmInline`
as JVM-only. Every fixed-layout schema therefore needed a hand-written
`expect value class` that repeated the `create` and `copy` signatures. The
processor emitted one common file, plus one `actual` file for each platform
family. Framed schemas already used a regular schema class, but their
generated view was still an `expect` declaration, with identical actuals for
JVM, iOS, and Android Native.

This shape had four costs:

- Consumers needed `-Xexpect-actual-classes`, a Beta feature.
- The Gradle plugin mapped every Kotlin target to a platform-specific output
  directory, and it rejected targets it did not know.
- The processor exposed a `kompact.generate` mode option.
- Default `copy` arguments could be declared only on the `expect` side.

`kotlin.jvm.JvmInline` is an optional expectation that common code may use.
It marks a value class as inline on the JVM, and Kotlin/Native ignores it.
Commit `ae91458` moved the runtime to common `@JvmInline value class`
declarations, so generated code no longer needs platform actuals either.

## Decision

1. Neither the runtime nor the generated code uses `expect` or `actual`.
2. Each `@KompactModel` declaration produces exactly one generated common
   Kotlin file, `<Name>View.kt`, that contains ordinary declarations.
3. Fixed-layout schemas are regular annotated classes, like framed schemas.
   A schema `SensorFrame` generates `@JvmInline public value class
   SensorFrameView(public val raw: ByteArray)`. The class has its minimum-size
   `init` check, a companion `create`, a `copy` with `= this.field` defaults,
   and typed getters. `@KompactModel(mutable = true)` also generates
   `MutableSensorFrameView`. The same file contains the reusable
   `SensorFrameViewHolder` operations and the `internal` function
   `encodeSensorFrameView`.
4. Framed schemas generate one regular `<Name>View` class in the same single
   common file, and declare default arguments directly.
5. A schema declared with `expect` is a processor error. The message tells the
   user to remove `expect` and use the generated view.
6. The `kompact.generate` processor option and its generation modes are
   removed.
7. The `ch.trancee.kompact.codegen` plugin registers its output directory only
   with `commonMain`. It no longer inspects Kotlin targets, adds no compiler
   arguments, and passes no processor options.

## Alternatives considered

- Keep `expect` and `actual` for generated views. This keeps the old source
  shape, but it also keeps the Beta compiler flag, the list of allowed
  targets, and three identical actual bodies for each schema. Rejected.
- Keep the user-written `expect value class` for fixed-layout schemas, and
  generate a non-`expect` implementation next to it. The generated declaration
  would collide with the name of the user's declaration. Rejected in favor of
  the framed `<Name>View` naming rule.

## Consequences

- Any Kotlin Multiplatform target that the runtime publishes can consume the
  generated code. The plugin itself has no target restrictions.
- Generated output is one directory registered with `commonMain`. This removes
  the source-directory wiring for each target and makes build caching simpler.
- Fixed-layout and framed schemas follow one naming rule: `<Name>View` and,
  for mutable fixed-layout schemas, `Mutable<Name>View`. Holders are named
  `<Name>ViewHolder`.
- The generator emits the `copy` default arguments with the view, so callers no
  longer repeat signatures.
- The public ABI of the processor is unchanged. The source shape of the
  generated code changes, and consumers must migrate.

## Migration

This is a breaking change before 1.0. Follow these steps:

1. Replace each fixed-layout `@KompactModel expect value class Name(raw)` with
   a regular `@KompactModel class Name`. Its annotated `val` properties carry
   placeholder initializers. Delete the hand-written companion and the `copy`
   signatures.
2. Rename usages of fixed-layout model types from `Name` to `NameView`,
   `MutableName` to `MutableNameView`, and `NameHolder` to `NameViewHolder`.
3. Remove `-Xexpect-actual-classes` and any `kompact.generate` option from
   consumer builds that needed them only for Kompact.
4. Framed schemas keep their `<Name>View` names. Delete any hand-written
   `expect` declarations of those views.
