# ADR-0013: Typed cursor status and caller-contract errors

- Status: Accepted
- Date: 2026-10-18
- Tags: api, bc-break, codegen
- Amends: [ADR-0008](0008-framed-generated-views.md) (cursor and
  generated-holder operations return `KompactStatus`) and
  [ADR-0010](0010-minimal-checked-result-surface.md) (`KompactDecodeError`
  gains a fifth case, `TrailingData`)

## Context

The runtime had three error vocabularies that overlapped, with no mapping
between them:

- `KompactCursor` reported outcomes as `Int` constants
  (`KompactCursor.STATUS_OK`, `STATUS_BOUNDS_ERROR`, and others). Any `Int`
  type-checked as a status, and a `when` could not cover the constants
  exhaustively. Nothing related a cursor status to the `KompactDecodeError`
  that framed and checked readers report for the same malformed input.
- `KompactFrame`, `KompactFraming`, and `KompactRuntime.readScalar` reported
  invalid caller arguments as malformed input. A `readBits` width outside
  1..64, or an invalid `readRepeated` element shape, became `BoundsError`. An
  invalid prefix width became `BadLengthPrefix`. A `ScalarType` wider than 32
  bits passed to the `Int`-returning `readScalar` became `BoundsError`. These
  are programming errors in generated or hand-written code, not properties of
  the received bytes. The block overload of `KompactFrame.decode` hid them as
  `KompactFrameResult.Failure`.
- `KompactFrame.requireComplete()` reported unread trailing bytes and non-zero
  padding as `BoundsError`, the same case as a read past the end of the input.
  A receiver could not tell a truncated frame from one that was too long.

Kompact is pre-1.0 ([ADR-0007](0007-pre-1-release-versioning.md)), so a minor
release may contain breaking changes.

## Decision

1. Replace the `STATUS_*` constants in the `KompactCursor` companion with a
   public `enum class KompactStatus`. Its entries are `OK`, `INVALID_BOUNDS`,
   `INVALID_WIDTH`, `BOUNDS_ERROR`, `INVALID_VALUE`, `BAD_LENGTH_PREFIX`,
   `UNALIGNED_REGION`, `INVALID_ARGUMENT`, `INVALID_UTF8`,
   `WORKSPACE_TOO_SMALL`, and `TRUNCATED_INPUT`. `KompactCursor.status` and
   every cursor operation return `KompactStatus`. Status precedence and the
   meaning of `errorBitOffset` and `errorDetail` do not change. Enum entries
   are singletons, so returning a status does not allocate.
2. `KompactStatus.decodeError` maps each malformed-input status to the shared
   vocabulary:
   - `BOUNDS_ERROR` maps to `BoundsError`.
   - `BAD_LENGTH_PREFIX` maps to `BadLengthPrefix`.
   - `INVALID_UTF8` maps to `InvalidUtf8`.
   - `TRUNCATED_INPUT` maps to `TruncatedNested`.
   - `OK` and the caller-contract statuses map to `null`.
3. The generated holder functions `decodeInto` and `encodeFrom`, and the
   nested and repeated element helpers, return `KompactStatus`.
4. Invalid caller arguments throw `IllegalArgumentException` through
   `require`, instead of returning a decode error. These cases are:
   - `KompactFrame.readBits` with a width outside 1..64
   - any `KompactFrame` or `KompactFraming` prefix width other than 8, 16, or
     32, including the count width of `readRepeated`
   - a `KompactFrame.readRepeated` element shape in which not exactly one of
     `elementWidth` (1..64) and `elementPrefixWidth` is non-zero
   - `KompactRuntime.readScalar` or `readScalarOrThrow` with a `ScalarType`
     wider than 32 bits

   Failures caused by the data keep their `KompactDecodeError`. The block
   overload of `KompactFrame.decode` translates only `KompactDecodeException`,
   so caller-contract violations propagate. `KompactFraming.INVALID_LENGTH_PREFIX`
   now means only that the prefix overruns the buffer.
5. Add `KompactDecodeError.TrailingData`. `KompactFrame.requireComplete()`
   reports unread bytes or non-zero padding bits with it, and so does every
   complete framed decode. Packed result layouts encode it as error kind 4, so
   the public `*Result.failure(...)` factories round-trip every case.

## Alternatives considered

- Keep the `Int` constants and add a mapping function. Rejected: an `Int`
  status still type-checks against unrelated integers, and a `when` cannot
  check it exhaustively.
- A sealed status hierarchy with cases that carry data. Rejected: those
  instances would allocate on failure paths that do not allocate today, and
  `errorBitOffset` and `errorDetail` already carry the details.
- Keep reporting invalid widths as decode errors. Rejected: it turns a
  programming error into a failure that depends on the input, and callers may
  silently discard it as a bad packet.
- Reuse `BoundsError` for trailing data. Rejected: truncated frames and frames
  that are too long need different diagnostics.

## Consequences

- Cursor callers compare against `KompactStatus.OK`, or use `when`, instead of
  `KompactCursor.STATUS_OK`. Code that stored a status in an `Int` must use
  `KompactStatus`, or `ordinal` for primitive checksums.
- Generated holder code must be regenerated. It now imports
  `ch.trancee.kompact.runtime.KompactStatus`.
- Code that relied on `KompactFrame.decode { … }` turning an invalid width into
  a `Failure` now receives `IllegalArgumentException`.
- Framed decoders that reported trailing data as `BoundsError` now report
  `TrailingData`. Exhaustive `when` expressions over `KompactDecodeError` need
  a new branch.
- No wire bytes change.
