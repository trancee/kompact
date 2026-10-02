# ADR-0005 — Full-domain LongResult

- **Status:** Accepted for the `LongResult` representation; allocation behavior is not measured.
- **Tags:** api, perf, bc-break
- **Superseded by:** none
- **Reconsiders:** [ticket 08 — runtime error model](../../.scratch/kompact-spec/issues/08-runtime-error-model.md) (the original representation intent was packed results on both success and failure, with byte offsets kept off the basic result types)

## Context

An external code review of `KompactRuntime` and the typed-result value classes
questioned the complexity of carrying detailed diagnostics in the basic result
types. Before this decision, ticket 08 specified packed `*Result` value classes
for success and failure: the error kind and raw enum code share the packed
representation with the value, while byte-offset diagnostics are available
through an opt-in `decodeFull()` path. This records the original representation
design; it is not a measurement of allocation behavior on each runtime or call
shape.

The costs of that design are real:
- seven nearly-identical `expect`/`actual` value classes (JVM `@JvmInline`
  actuals vs. plain `actual` on iOS) — heavy boilerplate and drift surface;
- fragile per-type packing: the former `LongResult` representation removed a sentinel band
  near `Long.MIN_VALUE`; `DoubleResult` uses a NaN-payload scheme with IEEE-754
  edge cases;
- a caller only caring about success still pays the full pack/unpack machinery
  on every read, because there is no single, simpler success-shaped return.

The review's central suggestion was to keep the basic result representation
simple and make detailed offsets an explicit diagnostic choice. That is the
largest single lever the review identified for maintainability.

## Accepted decision — LongResult

`LongResult` is a regular common class holding a nullable `Long` value and a
nullable `KompactDecodeError`. Every `Long` bit pattern, including
`Long.MIN_VALUE`, remains representable. Unlike the other packed scalar result
types, it does not use a value-class representation.

This deliberately prioritizes a full-domain API over a single-`Long`
representation. Reserving any sentinel band would silently misclassify valid
user data. This changes the public JVM/Native representation and must be
treated as a breaking API change.

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

- **Representation:** each checked long read returns a regular-class
  `LongResult`. Its runtime allocation cost depends on the call shape and
  platform; no device-level allocation evidence is recorded.
- **Binary/source compatibility:** replacing the public value-class shape and
  removing `packed` is a breaking API change. It shipped in `0.4.0` under the
  repository's pre-`1.0` versioning policy.

## Migration

Callers using `LongResult.success`, `failure`, `isSuccess`, `error`, and
`getOrThrow()` retain those operations. Callers reading `packed` must switch to
`value`/`error` or the typed accessors. No data on the wire changes; this is a
read-API representation change.

## References
- [ticket 08 — runtime error model](../../.scratch/kompact-spec/issues/08-runtime-error-model.md) (the packed representation this revisits)
- [ticket 03 — value-class representation / zero-alloc reads](../../.scratch/kompact-spec/issues/03-value-class-representation.md)
- `docs/architecture.md` § "Result representations and performance evidence"
- [Generated runtime API reference](../../kompact/docs/api/index.md)
- `docs/research/allocation-boxing-measurement.md`
