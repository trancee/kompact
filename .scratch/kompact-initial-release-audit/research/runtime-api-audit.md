# Runtime and public API audit

## Ranked findings

### Generated view equality is based on backing-array identity

Fixed-layout generated value-class views wrap `ByteArray`; arrays compare by
identity, so byte-identical content backed by different arrays does not imply
equal views. The framed regular classes likewise do not generate equality or
hashing. This can surprise consumers using values in sets/maps or comparing
decoded messages. Decide whether model equality is wire-content/value equality
or explicitly document reference semantics; cover fixed and bounded framed
views consistently.

Evidence: `kompact/src/commonMain/kotlin/ch/trancee/kompact/generated/VehicleTelemetry.kt`,
JVM/Native actuals under `kompact/src/{jvmCommon,nativeMain}`, and
`kompact-ksp/src/main/kotlin/ch/trancee/kompact/ksp/gen/FramedClassGenerator.kt`.
ADR-0006 describes immutable "value-class views"; the current
`VehicleTelemetryTest` does not appear to pin equality semantics.

### Generated writes silently truncate out-of-range values

The raw runtime `writeBits`/`writeBitsLong` methods mask values; generated
`create`/`copy` and mutable setters call those writes without validating values
against the declared bit width. For example, an over-width battery value can
silently become a different encoded value. This differs from prefix-writing
paths that validate before mutation. Decide whether generated model writes
reject out-of-range fields, return an observable typed failure, or document
truncation as a deliberate low-level contract. Preserve the no-allocation goal
when choosing the failure shape.

Evidence: `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactRuntime.kt`,
`kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactFraming.kt`,
and `kompact/src/commonMain/kotlin/ch/trancee/kompact/generated/VehicleTelemetry.kt`;
mutable actual setters are in `kompact/src/jvmCommon/kotlin/generated/VehicleTelemetry.kt`.
ADR-0006 discusses truncation for mutable setters but does not settle checked
validation for default factories/copies.

### Two public nested-region APIs overlap

`KompactFraming.readNested` and `NestedRegionResult` remain public and tested,
but the current framed generator uses `KompactFrame.readNested` and
`KompactByteSlice`. The legacy result's `getOrThrow()` returns `Pair<Int, Int>`,
which introduces an object on the success path. Decide whether to remove it
before first supported release or retain it with a distinct documented
purpose; coordinate with the source-level API cutover and ABI state.

Evidence: `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactFraming.kt`,
`NestedRegionResult.kt`, `KompactResultExtensions.kt`,
`kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactFrame.kt`,
and `kompact-ksp/src/main/kotlin/ch/trancee/kompact/ksp/gen/FramedFieldCodeGenerator.kt`.

### Convenience nested writer allocates per call

The public lambda overload of `KompactWriter.writeNested` creates a child
writer and buffer, then calls `build()` for a snapshot. Generated code uses
the borrowed-region overload instead. It must remain outside the guaranteed
no-allocation API and be clearly separated/documented as allocating.

Evidence: `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactWriter.kt`
and `kompact-ksp/src/main/kotlin/ch/trancee/kompact/ksp/gen/FramedFieldCodeGenerator.kt`.
This is addressed by the user's chosen rule that allocating convenience APIs
may coexist outside the guarantee; the exact naming/documentation remains to
be designed.

### Additional boundary coverage to confirm

An audit pass found no obvious test for rejecting nonzero unused trailing
bits, and no explicit test for every over-width factory/setter case. Verify the
complete test inventory before deciding whether to add those behaviors as
separate tickets.
