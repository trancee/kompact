# ADR-0013 — Typed cursor status and caller-contract errors

- **Status:** Accepted
- **Date:** 2026-10-18
- **Tags:** api, bc-break, codegen
- **Amends:** [ADR-0008](0008-framed-generated-views.md) (cursor and
  generated-holder operations return `KompactStatus`) and
  [ADR-0010](0010-minimal-checked-result-surface.md) (`KompactDecodeError`
  gains a fifth case, `TrailingData`)

## Context

The runtime had three error vocabularies that overlapped without a mapping:

- `KompactCursor` reported outcomes as `Int` constants
  (`KompactCursor.STATUS_OK`, `STATUS_BOUNDS_ERROR`, …). Any `Int` type-checked
  as a status, the constants could not be exhausted in a `when`, and nothing
  related a cursor status to the `KompactDecodeError` that framed and checked
  readers report for the same malformed input.
- `KompactFrame`, `KompactFraming`, and `KompactRuntime.readScalar` reported
  invalid caller arguments as malformed input. A `readBits` width outside
  1..64 or an invalid `readRepeated` element shape became `BoundsError`; an
  invalid prefix width became `BadLengthPrefix`; a `ScalarType` wider than 32
  bits passed to the `Int`-returning `readScalar` became `BoundsError`. These
  are programming errors in generated or hand-written code, not properties of
  the received bytes, and the block overload of `KompactFrame.decode` hid
  them as `KompactFrameResult.Failure`.
- `KompactFrame.requireComplete()` reported unread trailing bytes and
  non-zero padding as `BoundsError`, the same case as a read past the end of
  the input. A receiver could not tell a truncated frame from an over-long one.

Kompact is pre-1.0 ([ADR-0007](0007-pre-1-release-versioning.md)), so
breaking changes are allowed in a minor release.

## Decision

1. Replace the `KompactCursor` companion `STATUS_*` constants with a public
   `enum class KompactStatus` whose entries are `OK`, `INVALID_BOUNDS`,
   `INVALID_WIDTH`, `BOUNDS_ERROR`, `INVALID_VALUE`, `BAD_LENGTH_PREFIX`,
   `UNALIGNED_REGION`, `INVALID_ARGUMENT`, `INVALID_UTF8`,
   `WORKSPACE_TOO_SMALL`, and `TRUNCATED_INPUT`. `KompactCursor.status` and
   every cursor operation return `KompactStatus`. Status precedence and the
   `errorBitOffset`/`errorDetail` semantics are unchanged. Enum entries are
   singletons, so returning a status does not allocate.
2. `KompactStatus.decodeError` maps the malformed-input statuses to the shared
   vocabulary: `BOUNDS_ERROR` → `BoundsError`, `BAD_LENGTH_PREFIX` →
   `BadLengthPrefix`, `INVALID_UTF8` → `InvalidUtf8`, and `TRUNCATED_INPUT` →
   `TruncatedNested`. `OK` and the caller-contract statuses map to `null`.
3. Generated holder `decodeInto`/`encodeFrom` functions and the nested and
   repeated element helpers return `KompactStatus`.
4. Invalid caller arguments throw `IllegalArgumentException` (via `require`)
   instead of a decode error:
   - `KompactFrame.readBits` with a width outside 1..64;
   - any `KompactFrame` or `KompactFraming` prefix width other than 8, 16, or
     32, including the `readRepeated` count width;
   - a `KompactFrame.readRepeated` element shape where not exactly one of
     `elementWidth` (1..64) and `elementPrefixWidth` is non-zero;
   - `KompactRuntime.readScalar`/`readScalarOrThrow` with a `ScalarType`
     wider than 32 bits.

   Data-driven failures keep their `KompactDecodeError`. The block overload of
   `KompactFrame.decode` translates only `KompactDecodeException`, so caller
   contract violations propagate. `KompactFraming.INVALID_LENGTH_PREFIX` now
   means only "the prefix overruns the buffer".
5. Add `KompactDecodeError.TrailingData`. `KompactFrame.requireComplete()`,
   and therefore every complete framed decode, reports unread bytes or
   non-zero padding bits with it. Packed result layouts encode it as error
   kind 4, so the public `*Result.failure(...)` factories round-trip every
   case.

## Alternatives considered

- **Keep `Int` constants and add a mapping function.** Rejected: an `Int`
  status still type-checks against unrelated integers and cannot be checked
  exhaustively.
- **A sealed status hierarchy with payload-carrying cases.** Rejected: payload
  instances would allocate on failure paths that are currently
  allocation-free; `errorBitOffset`/`errorDetail` already carry the details.
- **Keep reporting invalid widths as decode errors.** Rejected: it turns a
  programming error into an input-dependent failure that callers may silently
  discard as "bad packet".
- **Reuse `BoundsError` for trailing data.** Rejected: truncated and
  over-long frames need different diagnostics.

## Consequences

- Cursor callers compare against `KompactStatus.OK` (or use `when`) instead of
  `KompactCursor.STATUS_OK`. Code that stored a status in an `Int` must use
  `KompactStatus` (or `ordinal` for primitive checksums).
- Generated holder code must be regenerated; it now imports
  `ch.trancee.kompact.runtime.KompactStatus`.
- Code that relied on `KompactFrame.decode { … }` turning an invalid width
  into a `Failure` now receives `IllegalArgumentException`.
- Framed decoders that previously reported trailing data as `BoundsError` now
  report `TrailingData`; exhaustive `when` expressions over
  `KompactDecodeError` need a new branch.
- No wire bytes change.
