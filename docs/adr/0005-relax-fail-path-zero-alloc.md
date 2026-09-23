# ADR-0005 — Relax zero-allocation on the failure path

- **Status:** proposed (Byte/Short collapse shipped on `feat/v1-refactor-ratified-shape`:
  `ByteResult`/`ShortResult` dropped, 5 scalar result types remain; Float/Double
  NaN-tag unification deferred to Y3 — see `.scratch/kompact-v1-refactor/map.md`)
- **Tags:** api, perf, bc-break
- **Superseded by:** none
- **Reconsiders:** [ticket 08 — runtime error model](../../.scratch/kompact-spec/issues/08-runtime-error-model.md) (the accepted consequence: result value classes are zero-alloc on *both* success and failure, byte offset not on the fast path)

## Context

An external code review of `KompactRuntime` and the typed-result value classes
questioned the zero-allocation contract on the **failure** path. Today, per
ticket 08, the seven specialized `*Result` value classes (`ByteResult` …
`BooleanResult`) each wrap a single packed `Long` and are zero-alloc on both
success and failure: the error code + raw enum code live in the value bits, and
a full diagnostic (byte/bit offset, raw enum code, offending-field id) is
reachable only on an opt-in `decodeFull()` path that allocates the
`DecodeError` object only on the rare failure path.

The costs of that design are real:
- seven nearly-identical `expect`/`actual` value classes (JVM `@JvmInline`
  actuals vs. plain `actual` on iOS) — heavy boilerplate and drift surface;
- fragile per-type packing: `LongResult` permanently removes a sentinel band
  near `Long.MIN_VALUE`; `DoubleResult` uses a NaN-payload scheme with IEEE-754
  edge cases;
- a caller only caring about success still pays the full pack/unpack machinery
  on every read, because there is no single, simpler success-shaped return.

The review's central suggestion: make the **success** path the only thing the
zero-alloc contract covers, and let the **failure** path allocate a richer
structured error. That is the largest single lever the review identifies for
maintainability.

## Decision (Proposed)

Adopt a **tiered result API** and relax the failure-path zero-alloc guarantee,
while preserving the zero-alloc **success** fast path that ticket 03 / 08
depend on:

1. **Keep the zero-alloc fast path on success.** The common scalar read
   (`readScalar` / `readBool` / …) must stay allocation-free on success — that
   is the hot path over a caller-owned `ByteArray`, and ticket 10 locks it as a
   CI gate. This is non-negotiable.
2. **Allow allocation on the failure path.** Replace the requirement that the
   packed `*Result` carry error info as packed-Long bits with a two-tier design:
   - `readScalar(raw, bitOffset, type): ScalarResult<T>` — thin, success-shaped
     (an inline value class or a small result with the value + a *non-allocating*
     ok/error discriminator), zero-alloc on success;
   - `decodeFull(raw, …): DetailedResult<T>` — opt-in, may allocate a
     `DecodeError(value, offset, kind, rawCode)` on failure.
3. **Collapse the seven types.** Move toward one platform value class per value
   *shape* (a ≤32-bit integer result, a 64-bit integer result, a float result,
     a boolean result), generated from a single template to kill `expect`/`actual`
   drift.

This reverts the specific ticket-08 consequence that *error info is packed into
the same zero-alloc `Long`*, in favor of "success stays zero-alloc; failure may
allocate, and is opt-in."

## Alternatives considered

1. **Keep as-is.** Preserves the ratified ticket-08 contract and the zero-alloc
   CI gate (ticket 10) unchanged. Rejected by this proposal: the review
   characterizes the seven-type packing as the maintenance anchor dragging the
   rest of the API down; the proposal exists to trade a little failure-path
   allocation for a far simpler type surface.
2. **Generic `KompactResult<T>` over a boxed scalar on the success path.**
   Rejected: JVM boxes the primitive, violating ticket 03's zero-alloc read
   contract (explicitly rejected in ticket 08's answer).
3. **Throw on failure.** Rejected: exceptions allocate (ticket 06) and ticket 08
   forbids throwing on the read path.

## Risks

- **Binary/source compatibility:** collapsing `*Result` types and changing the
  error-encoding from packed-Long bits is a breaking change — at minimum MAJOR
  by SemVer. Mitigation: stage it behind the *current* SNAPSHOT status and the
  v1 boundary; the v1 release should ship the simpler tiered design, not both.
- **Perf regression on the failure path is intentional** and acceptable (failures
  are off the hot path), but the success path must be re-asserted against ticket
  10's zero-alloc CI gate after the change.
- **Opt-in `decodeFull()` must be genuinely opt-in**, or the simplification
  collapses: if every caller reaches for `decodeFull()` to get offsets, the
  success-path type becomes a vestige.

## Migration

Callers using `getOrThrow()` keep working (the success-shaped result retains it).
Callers that pattern-matched on packed error codes in the `Long` must move to the
`DecodeError` type returned by `decodeFull()`. No data on the wire changes — this
is a pure read-API representation change.

## References
- [ticket 08 — runtime error model](../../.scratch/kompact-spec/issues/08-runtime-error-model.md) (the decision this revisits)
- [ticket 03 — value-class representation / zero-alloc reads](../../.scratch/kompact-spec/issues/03-value-class-representation.md)
- `docs/architecture.md` § "Zero-allocation reads"; § "Runtime error encoding"
- `docs/api-reference.md` § "Typed result value classes"; § "`Kompact.Result` namespace"
