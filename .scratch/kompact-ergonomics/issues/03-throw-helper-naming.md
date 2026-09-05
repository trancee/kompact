Type: grilling
Status: resolved

# 03 — `throwSmallFailure` / `throwLongFailure` / `throwDoubleFailure` leak the encoding

## Question

`KompactResult.kt` declares three `internal inline` helpers:
`throwSmallFailure`, `throwLongFailure`, `throwDoubleFailure`. They
each call `throw KompactDecodeException(decodeXxxError(packed))` and
exist so each platform actual's `getOrThrow()` stays one expression
(Ticket 10 deepen). The names describe the internal packed-Long
encoding ("small" = ≤32-bit packed layout, "long" = `LongResult`
sentinel band, "double" = NaN-payload), not the caller's intent. A
reader of `KompactResult.kt` has to know the encoding taxonomy to
understand which helper to call. The repo's decoding helpers follow
the opposite convention (`decodeErrorFromSmallBits`,
`decodeLongError`, `decodeDoubleError` — encoding-aware on the
*decode* side) so the asymmetry is real.

The decision: do we rename the three helpers to intent-revealing
names (`throwFromPacked`, `throwDecodeFailure`, or a single
`throwFailure(packed, kind: ErrorKind)`), and/or collapse them into
one helper that takes the kind as a parameter? This is Fowler's
*Mysterious Name* and scored P3 in the prior code-review of
`feat/laguna`.

## Context for the claiming session

- `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactResult.kt`
  — `throwSmallFailure` / `throwLongFailure` / `throwDoubleFailure`
  (lines ~134–141). Each is `internal inline fun ...(packed: Long): Nothing = throw KompactDecodeException(decodeXxxError(packed))`.
- The decoding helpers they wrap are
  `decodeErrorFromSmallBits(packed)`,
  `decodeLongError(packed)`, and
  `decodeDoubleError(packed)`. All three return a
  `KompactDecodeError`.
- The helpers are called from the seven result value class
  `getOrThrow()` members in `jvmMain` and `iosMain`
  (`kompact/src/jvmMain/.../KompactResult.kt`,
  `kompact/src/iosMain/.../KompactResult.kt`). Each `getOrThrow`
  does `if (isSuccess) primitive else throwXxxFailure(packed)`.
- These helpers are `internal` — they are not part of the public
  API. The public surface is `getOrThrow()` on the seven result
  classes. So the rename is an *internal* refactor with no
  consumer-visible effect; the cost is the touch on every platform
  actual (4 calls in jvmMain, 4 in iosMain).

## Open sub-questions

1. **One helper or three?** A single `throwFailure(packed, kind:
   ErrorKind)` would force the kind to be a parameter at every call
   site, which is what the current three-helper split avoids. The
   trade is clarity (one name) vs. terseness at the call site (no
   extra arg).
2. **Naming convention** — `throwFromPacked` matches the repo's
   `decodeErrorFromSmallBits` pattern. `throwDecodeFailure` matches
   the failure semantic. Pick one (or propose a third).
3. **Should the decoding helpers be renamed for symmetry too?**
   Out of scope for this ticket (they are already encoding-aware by
   design), but the answer here might point at a follow-up.

## What "resolved" looks like

- The chosen name (or collapsed-form) is recorded under
  `## Answer` with a one-line rationale.
- The four call sites in each platform's `KompactResult.kt` are
  noted so the implementation commit can apply the rename
  consistently.
- The decision is propagated to ticket 09 (test file split) if
  any test file references the old names directly.

## Answer

**Decision: rename the three helpers to mirror the existing decode
pattern. `throwSmallFailure` → `throwDecodeErrorFromSmallBits`,
`throwLongFailure` → `throwDecodeErrorFromLong`,
`throwDoubleFailure` → `throwDecodeErrorFromDouble`. Three-helper
split is kept (each picks the right decoder for its encoding);
the rename is internal-only with no consumer-visible effect.**

The sub-questions, settled:

- **One helper or three?** Three. A single `throwFromPacked(packed,
  kind)` would add a `kind` parameter at every call site; the
  three-helper split keeps `getOrThrow()` a one-expression
  function per the Ticket 10 deepen note. The three encodings
  are genuinely different decoders (`decodeErrorFromSmallBits` /
  `decodeLongError` / `decodeDoubleError`) so the split is
  intrinsic to the data, not a stylistic choice.
- **Naming convention** — mirror the decode pattern. The
  decode helpers are `decodeErrorFrom{SmallBits,Long,Double}`;
  the throw helpers become `throwDecodeErrorFrom{SmallBits,Long,Double}`.
  The `throw` prefix is the caller's intent (the action);
  `DecodeError` is the kind of exception (matching
  `KompactDecodeException`'s class name); `From{SmallBits,Long,Double}`
  is the encoding suffix (the same suffix as the decode
  helper each one wraps). The result is a clean
  `{verb}DecodeErrorFrom{Encoding}` pattern where `verb ∈
  {decode, throw}`.
- **Rename the decode helpers too?** No. They are already
  encoding-aware by design (`decodeErrorFrom{SmallBits,Long,Double}`);
  the new throw names now share the `From{Encoding}` suffix
  with them, completing the symmetry. No follow-up needed.

### Sketch (for the implementation commit)

```kotlin
// KompactResult.kt — commonMain (lines 136–141)
- internal inline fun throwSmallFailure(packed: Long): Nothing =
-     throw KompactDecodeException(decodeErrorFromSmallBits(packed))
- internal inline fun throwLongFailure(packed: Long): Nothing =
-     throw KompactDecodeException(decodeLongError(packed))
- internal inline fun throwDoubleFailure(packed: Long): Nothing =
-     throw KompactDecodeException(decodeDoubleError(packed))
+ internal inline fun throwDecodeErrorFromSmallBits(packed: Long): Nothing =
+     throw KompactDecodeException(decodeErrorFromSmallBits(packed))
+ internal inline fun throwDecodeErrorFromLong(packed: Long): Nothing =
+     throw KompactDecodeException(decodeLongError(packed))
+ internal inline fun throwDecodeErrorFromDouble(packed: Long): Nothing =
+     throw KompactDecodeException(decodeDoubleError(packed))
```

The seven call sites in `jvmMain/.../KompactResult.kt` and
`iosMain/.../KompactResult.kt` (one per result class, in each
class's `getOrThrow()`) update to match. For example, the
`IntResult` `getOrThrow()` in `jvmMain` (line ~55) becomes:

```kotlin
- else throwSmallFailure(packed)
+ else throwDecodeErrorFromSmallBits(packed)
```

### Propagation

- **No public API change.** The three helpers are `internal`;
  the rename is contained to the `:kompact` module. No BCV
  golden regen needed.
- **No test changes needed.** The helpers are tested
  transitively through `getOrThrow()` (the public surface);
  the test assertions (`assertFailsWith<KompactDecodeException>`)
  are name-agnostic.
- **No docs change.** The helpers are not mentioned in
  `docs/api-reference.md` (they are `internal`); the
  `docs/architecture.md#runtime-error-encoding` discussion
  talks about the packed-Long layout, not the helper names.
- **Implementation commit scope** — pure rename. 3 helper
  declarations + 14 call sites (7 in `jvmMain`, 7 in
  `iosMain`). No behavior change. No golden regen.

## Comments
