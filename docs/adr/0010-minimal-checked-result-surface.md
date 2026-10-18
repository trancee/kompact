# ADR-0010: Minimal checked-result API

- Status: Accepted
- Date: 2026-10-18
- Tags: api, bc-break
- Supersedes: the opt-in `decodeFull` diagnostics tier described in
  [ADR-0005](0005-full-domain-long-result.md). The `LongResult` decision there
  still applies.
- Amends: [ADR-0003](0003-kmp-consumer-enablement.md) (removes the JVM
  coverage-pinning code that its Android target section kept in `jvmMain`)
- Amended by: [ADR-0013](0013-typed-status-and-caller-contract-errors.md)
  (adds `KompactDecodeError.TrailingData` as a fifth case)

## Context

A review of the public runtime API found several elements that no code path
used, or that cost more than they gave:

- No code produced `KompactDecodeError.UnknownEnumCode(rawCode)`. Kompact has
  no enum field type, yet every packed result layout reserved bits for a raw
  enum code.
- `KompactRuntime.decodeFull*`, `DetailedResult`, and `DetailedDecodeError`
  added only a byte offset (`bitOffset ushr 3`) that the caller had already
  passed in. Their only other diagnostic, the raw enum code, was unreachable.
- The `Kompact.Result.*` type aliases repeated the five result type names
  under another import path.
- The runtime artifact shipped the bundled `VehicleTelemetry` sample, even
  though it is a hand-written test fixture.
- The result value classes exposed a public constructor and their `packed`
  bits. Callers could build inconsistent results. The JVM synthetic getters
  also needed Java coverage support code (`JvmCoveragePinning`).
- `FloatResult` canonicalized NaN, although a 32-bit float fits in the
  48-bit value field. NaN payloads were lost for no reason.
- `NestedRegionResult.getOrThrow()` returned `Pair<Int, Int>`, which allocates
  on every successful nested read.

Kompact is pre-1.0 ([ADR-0007](0007-pre-1-release-versioning.md)), so a minor
release may contain breaking changes.

## Decision

1. `KompactDecodeError` has four cases: `BoundsError`, `BadLengthPrefix`,
   `TruncatedNested`, and `InvalidUtf8`. Each case is a `data object`, so
   `toString()` prints the case name. The packed layouts no longer carry a raw
   enum code.
2. Remove `decodeFull*`, `DetailedResult`, `DetailedDecodeError`, and the
   `ch.trancee.kompact.Kompact` alias namespace.
3. Move `VehicleTelemetry` and `MutableVehicleTelemetry` to `commonTest` as
   fixtures. Remove the `ch.trancee.kompact.generated` runtime package.
4. `IntResult`, `FloatResult`, `BooleanResult`, `DoubleResult`,
   `NestedRegionResult`, and `ScalarType` have private constructors and
   private packed state. Use the `success` and `failure` factories, or
   `ScalarType.of` and the predefined constants. `ScalarType.of` rejects widths
   outside 1..64 with `IllegalArgumentException`. Delete the JVM
   coverage-pinning code.
5. `FloatResult` stores the raw IEEE-754 bits, so every NaN payload survives a
   round trip. `DoubleResult` keeps its reserved quiet-NaN failure payloads and
   canonicalizes NaN on success. Callers that need the exact `Double` NaN bits
   read `KompactRuntime.readBitsLong(raw, offset, 64)`.
6. `NestedRegionResult` exposes `startBit` and `bitLength`. Both throw
   `KompactDecodeException` on failure. Remove `getOrThrow()`, the
   `NestedRegion` alias, the `getOrElse` and `map` extensions on
   `NestedRegionResult`, and `KompactFraming.readNestedOrThrow`.

## Alternatives considered

- Keep `UnknownEnumCode` for application use. Rejected: mapping application
  enums is application policy. A domain error type describes that failure
  better than a runtime decode error that the runtime never produces.
- Return a region object that does not allocate from `NestedRegionResult`.
  Rejected: two `Int` accessors on the existing packed value class already
  avoid allocation without another type.

## Consequences

- The ABI and the KDoc are smaller, and no Java coverage support code is left.
- The packed error-kind encoding is simpler, with kinds 0 to 3 in every
  layout.
- Callers that used any removed declaration must migrate. No wire bytes
  change.
