# Annotations and the KSP processor

Two annotations live in `ch.trancee.kompact.annotation` and are consumed by the `:kompact-ksp` JVM-only KSP processor. The processor generates per-schema value-class views in the consumer's `commonMain` source root.

## `@KompactModel` (target: `AnnotationTarget.CLASS`)

Marks a class as a Kompact schema. The processor looks for `@KompactField`-annotated properties on the class and emits a value-class view with one accessor per field.

The annotated class can be a regular class. The KSP processor generates a companion `<ModelName>View` value class. The hand-written class is the schema declaration; the generated view is the read path.

## `@KompactField` (target: `AnnotationTarget.PROPERTY`)

Marks a property on a `@KompactModel` class as a packed field.

### Parameters

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `bitOffset` | `Int` | — (required) | The bit index where the field starts, 0-based, LSB-first within the byte stream. |
| `bitWidth` | `Int` | — (required) | The field's width in bits, 1..64. |
| `lengthPrefixBits` | `Int` | `0` | When non-zero, the field is length-delimited. Must be `8`, `16`, or `32`; `0` means "not length-prefixed". |
| `enumWidth` | `Int` | `0` | When the field is a dense-ordinal enum, the bit width of the wire ordinal, 1..8. `0` means "not an enum". |
| `signed` | `Boolean` | `false` | When true, the assembled magnitude is interpreted as two's-complement signed. When false, zero-extended. |
| `defaultValue` | `Int` | `0` | Default value used when a newer reader sees an older stream that does not contain this field. Defaults to 0 (the type's zero for Int / `false` for Boolean). Applied at read time by the `KompactRead.readXxxWithDefault` helpers. |

### Validation (compile-time, by `KompactProcessor`)

The processor's `LayoutModel.validate(...)` checks each `@KompactModel` schema before emitting anything. On failure it emits a hard error that halts generation for that schema. The Gradle build fails.

- Bit-offset overlap: two fields' `[bitOffset, bitOffset + bitWidth)` ranges must not intersect.
- Per-struct width sum: the total of all field widths is logged as informational.
- Length-prefix width: must be one of `8`, `16`, `32` when `lengthPrefixBits > 0`.
- Uniform length-prefix width: every length-prefixed field in the schema must share the same `lengthPrefixBits` value (required for forward-compat skip).
- Enum width: `1..8` when `enumWidth > 0`.
- Enum vs bit width: `enumWidth ≤ bitWidth`.

A schema that fails validation produces no generated source. The build is red until the schema is fixed.

## `KompactProcessor` (the JVM-only KSP processor)

Class: `ch.trancee.kompact.ksp.KompactProcessor`. Registered via `META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider` → `ch.trancee.kompact.ksp.KompactProcessorProvider`.

### What it emits

For each `@KompactModel` class the processor emits:

1. A common `expect value class <ModelName>View(val raw: ByteArray)` in the consumer's `commonMain` source root. One accessor per `@KompactField` (no body. The JVM `actual` and the iOS `actual` provide the implementation).
2. A JVM `actual value class <ModelName>View` annotated `@JvmInline` in the consumer's `jvmMain` source root.
3. A plain `actual value class <ModelName>View` for `iosArm64Main` and `iosSimulatorArm64Main`.
4. A `KompactAnnotations.kt` stub (aggregating). The `@KompactModel` and `@KompactField` annotations, emitted into the consumer's `commonMain` source root so generated sources compile without a hand-written copy.

### Known limitation: per-target actuals

A pre-existing KSP limitation prevents the processor from emitting per-target actuals into the correct source set from a `kspCommonMainMetadata` invocation. v1 of the processor emits only the common `expect`; the per-target actuals are the consumer's responsibility. The `:kompact-example` module demonstrates the pattern: hand-written `VehicleTelemetrySchemaView.jvm.kt`, `.iosArm64.kt`, and `.iosSimulatorArm64.kt`. The KSP-generated `expect` is the contract the per-target actuals must satisfy.

A future version of the processor can run a second KSP round per target to emit the per-target actuals automatically. Until then, copy the per-target skeleton from the example and fill in the accessor bodies via the `KompactRead.readXxx*(raw, …)` calls.

## `LayoutModel` (internal validation helper, commonMain within `:kompact-ksp`)

The pure-Kotlin validation class that the processor uses. The processor constructs one `LayoutModel` per `@KompactModel` class, calls `LayoutModel.validate(logger, decl)`, and reports hard errors through the `KSPLogger`. The `LayoutModel` is internal to the processor. Consumers do not call it directly.

The `LayoutModel.uniformPrefixWidthSatisfied()` predicate is the forward-compat guard. It returns `true` only when all length-prefixed fields in a struct share the same prefix width. A `false` result forces a hard error.
