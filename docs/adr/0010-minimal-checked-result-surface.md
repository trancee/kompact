# ADR-0010 — Minimal checked-result surface

- **Status:** Accepted
- **Tags:** api, bc-break
- **Supersedes:** the opt-in `decodeFull` diagnostics tier described in
  [ADR-0005](0005-full-domain-long-result.md) (the `LongResult` decision
  there still applies)
- **Amended by:** [ADR-0013](0013-typed-status-and-caller-contract-errors.md)
  (adds `KompactDecodeError.TrailingData` as a fifth case)

## Context

A review of the runtime's public surface found several API elements that no
code path used, or that cost more than they gave:

- `KompactDecodeError.UnknownEnumCode(rawCode)` was never produced. Kompact has
  no enum field type, but every packed result layout reserved bits for a raw
  enum code.
- `KompactRuntime.decodeFull*`, `DetailedResult`, and `DetailedDecodeError`
  only added a byte offset (`bitOffset ushr 3`) that the caller already passed
  in. Their only other diagnostic, the raw enum code, was unreachable.
- The `Kompact.Result.*` type aliases duplicated the five result type names
  under another import path.
- The bundled `VehicleTelemetry` sample shipped in the runtime artifact,
  although it is a hand-written test fixture.
- The result value classes exposed a public constructor and their `packed`
  bits. Callers could build inconsistent results, and the JVM synthetic
  getters needed Java-based coverage scaffolding (`JvmCoveragePinning`).
- `FloatResult` canonicalized NaN, although a 32-bit float fits in the 48-bit
  value field with room to spare. NaN payloads were lost for no reason.
- `NestedRegionResult.getOrThrow()` returned `Pair<Int, Int>`, which allocates
  on every successful nested read.

Kompact is pre-1.0 ([ADR-0007](0007-pre-1-release-versioning.md)), so
breaking changes are allowed in a minor release.

## Decision

1. `KompactDecodeError` has four cases: `BoundsError`, `BadLengthPrefix`,
   `TruncatedNested`, and `InvalidUtf8`. They are all `data object`s, so
   `toString()` prints the case name. The packed layouts no longer carry a raw
   enum code.
2. Remove `decodeFull*`, `DetailedResult`, `DetailedDecodeError`, and the
   `ch.trancee.kompact.Kompact` alias namespace.
3. Move `VehicleTelemetry` and `MutableVehicleTelemetry` to `commonTest` as
   fixtures. The `ch.trancee.kompact.generated` runtime package is removed.
4. `IntResult`, `FloatResult`, `BooleanResult`, `DoubleResult`,
   `NestedRegionResult`, and `ScalarType` have private constructors and
   private packed state. Use the `success`/`failure` factories, or
   `ScalarType.of` and the predefined constants. `ScalarType.of` rejects widths
   outside 1..64 with `IllegalArgumentException`. Delete the JVM coverage
   pinning scaffolding.
5. `FloatResult` stores the raw IEEE-754 bits, so every NaN payload
   round-trips. `DoubleResult` keeps its reserved quiet-NaN failure payloads
   and canonicalizes successful NaNs. Callers that need exact Double NaN bits
   read `KompactRuntime.readBitsLong(raw, offset, 64)`.
6. `NestedRegionResult` exposes `startBit` and `bitLength`. Both throw
   `KompactDecodeException` on failure. Remove `getOrThrow()`, the
   `NestedRegion` alias, the `NestedRegionResult` `getOrElse`/`map` extensions,
   and `KompactFraming.readNestedOrThrow`.

## Alternatives considered

- **Keep `UnknownEnumCode` for application use.** Rejected: application enum
  mapping is application policy. A domain error type describes that failure
  better than a runtime decode error that the runtime never produces.
- **Return a non-allocating region object from `NestedRegionResult`.**
  Rejected: two `Int` accessors on the existing packed value class already
  avoid allocation without adding another type.

## Consequences

- Smaller ABI, less KDoc, and no Java coverage scaffolding.
- The packed error-kind encoding is simpler, with kinds 0..3 in every layout.
- Callers that used any removed declaration must migrate. No wire bytes
  change.
