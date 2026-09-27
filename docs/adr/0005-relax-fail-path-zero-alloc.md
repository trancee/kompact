# ADR-0005 — Full-domain LongResult

- **Status:** Accepted for the `LongResult` representation; other result representations are unchanged.
- **Tags:** api, perf, bc-break
- **Superseded by:** none
- **Reconsiders:** [ticket 08 — runtime error model](../../.scratch/kompact-spec/issues/08-runtime-error-model.md) (the accepted consequence: result value classes are zero-alloc on *both* success and failure, byte offset not on the fast path)

## Context

An external code review of `KompactRuntime` and the typed-result value classes
questioned the zero-allocation contract on the **failure** path. Before this
decision, per ticket 08, the seven specialized `*Result` value classes (`ByteResult` …
`BooleanResult`) each wrap a single packed `Long` and are zero-alloc on both
success and failure: the error code + raw enum code live in the value bits, and
a full diagnostic (byte/bit offset, raw enum code, offending-field id) is
reachable only on an opt-in `decodeFull()` path that allocates the
`DecodeError` object only on the rare failure path.

The costs of that design are real:
- seven nearly-identical `expect`/`actual` value classes (JVM `@JvmInline`
  actuals vs. plain `actual` on iOS) — heavy boilerplate and drift surface;
- fragile per-type packing: the former `LongResult` representation removed a sentinel band
  near `Long.MIN_VALUE`; `DoubleResult` uses a NaN-payload scheme with IEEE-754
  edge cases;
- a caller only caring about success still pays the full pack/unpack machinery
  on every read, because there is no single, simpler success-shaped return.

The review's central suggestion: make the **success** path the only thing the
zero-alloc contract covers, and let the **failure** path allocate a richer
structured error. That is the largest single lever the review identifies for
maintainability.

## Accepted decision — LongResult

`LongResult` is a regular common class holding a nullable `Long` value and a
nullable `KompactDecodeError`. Every `Long` bit pattern, including
`Long.MIN_VALUE`, remains representable. Unlike the other packed scalar result
types, creating a `LongResult` allocates on both success and failure.

This deliberately prioritizes a full-domain API over the zero-allocation
representation. Reserving any sentinel band would silently misclassify valid
user data. This changes the public JVM/Native representation and must be
treated as a breaking API change before a stable release.

## Alternatives considered

1. **Keep the sentinel band.** This preserves the packed representation but
   makes valid values look like errors. Rejected because callers may use the
   entire signed `Long` domain.
2. **Use a generic `KompactResult<Long>`.** Rejected because it adds a generic
   result abstraction without removing the need to represent both value and
   error.
3. **Throw from `readScalarAsLong`.** Rejected because malformed input must
   remain a typed result rather than throwing on the checked-read path.

## Risks

- **Allocation:** every checked long read allocates a result, on success and
  failure. This is an intentional tradeoff; device-level allocation evidence
  remains a separate measurement requirement.
- **Binary/source compatibility:** replacing the public value-class shape and
  removing `packed` is a breaking API change. Under the repository's pre-`1.0`
  policy, it ships in the next MINOR release (`0.4.0`); from `1.0.0` onward,
  the same change requires a MAJOR release.

## Migration

Callers using `LongResult.success`, `failure`, `isSuccess`, `error`, and
`getOrThrow()` retain those operations. Callers reading `packed` must switch to
`value`/`error` or the typed accessors. No data on the wire changes; this is a
read-API representation change.

## References
- [ticket 08 — runtime error model](../../.scratch/kompact-spec/issues/08-runtime-error-model.md) (the packed representation this revisits)
- [ticket 03 — value-class representation / zero-alloc reads](../../.scratch/kompact-spec/issues/03-value-class-representation.md)
- `docs/architecture.md` § "Zero-allocation reads"; § "Runtime error encoding"
- `docs/api-reference.md` § "Typed result value classes"; § "`Kompact.Result` namespace"
