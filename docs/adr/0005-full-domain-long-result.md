# ADR-0005: Full-domain `LongResult`

- Status: Accepted for the `LongResult` representation.
  [ADR-0010](0010-minimal-checked-result-surface.md) supersedes the opt-in
  `decodeFull` diagnostics tier. Allocation behavior is not measured.
- Date: 2026-09-18
- Tags: api, perf, bc-break
- Superseded in part by: [ADR-0010](0010-minimal-checked-result-surface.md),
  for the opt-in `decodeFull` diagnostics tier only. The `LongResult` decision
  stands.
- Reconsiders: [ticket 08, runtime error
  model](../../.scratch/kompact-spec/issues/08-runtime-error-model.md). Its
  original intent was packed results for both success and failure, with byte
  offsets kept out of the basic result types.

## Context

An external code review of `KompactRuntime` and the typed result value classes
questioned the cost of carrying detailed diagnostics in the basic result
types. Before this decision, ticket 08 specified packed `*Result` value classes
for success and failure. The error kind and the raw enum code shared the
packed representation with the value. Byte-offset diagnostics came from an
opt-in `decodeFull()` path. This section records the original representation
design. It is not a measurement of allocation on each runtime or call shape.

That design had real costs:

- Seven nearly identical `expect` and `actual` value classes, with JVM
  `@JvmInline` actuals and plain `actual` classes on iOS. They meant heavy
  boilerplate and many places where the copies could drift apart.
- Fragile packing for each type. The former `LongResult` representation gave
  up a sentinel band near `Long.MIN_VALUE`. `DoubleResult` uses a NaN-payload
  scheme with IEEE-754 edge cases.
- A caller that cares only about success still pays for the full pack and
  unpack work on every read, because no simpler return shape exists for
  success alone.

The central suggestion of the review was to keep the basic result
representation simple, and to make detailed offsets an explicit diagnostic
choice. The review named this as the biggest single improvement to
maintainability.

## Decision

`LongResult` is a regular common class that holds a nullable `Long` value and
a nullable `KompactDecodeError`. Every `Long` bit pattern, including
`Long.MIN_VALUE`, stays representable. Unlike the other packed scalar result
types, `LongResult` is not a value class.

This decision puts a full-domain API ahead of a single-`Long` representation.
Any reserved sentinel band would silently misclassify valid user data. The
public JVM and Native representation changes, so this is a breaking API
change.

## Alternatives considered

1. Keep the sentinel band. This keeps the packed representation, but valid
   values look like errors. Rejected: callers may use the entire signed `Long`
   domain.
2. Use a generic `KompactResult<Long>`. Rejected: it adds a generic result
   abstraction and still has to represent both the value and the error.
3. Throw from `readScalarAsLong`. Rejected: malformed input must stay a typed
   result on the checked read path, not an exception.

## Consequences

- Every checked long read returns a regular-class `LongResult`. Whether that
  allocates at run time depends on the call shape and the platform. No
  device-level allocation evidence is recorded.
- Replacing the public value-class shape and removing `packed` breaks binary
  and source compatibility. The change shipped in `0.4.0` under the pre-`1.0`
  versioning policy of the repository.

## Migration

Callers that use `LongResult.success`, `failure`, `isSuccess`, `error`, and
`getOrThrow()` keep those operations. Callers that read `packed` must switch to
`value` and `error`, or to the typed accessors. No wire bytes change. This is
a change to the read API representation only.

## References

- [Ticket 08, runtime error
  model](../../.scratch/kompact-spec/issues/08-runtime-error-model.md), the
  packed representation that this ADR revisits
- [Ticket 03, value-class representation and zero-allocation
  reads](../../.scratch/kompact-spec/issues/03-value-class-representation.md)
- [Result representations and performance
  evidence](../architecture.md#result-representations-and-performance-evidence)
  in `docs/architecture.md`
- [Generated runtime API reference](../../kompact/docs/api/index.md)
- [Allocation and boxing research
  note](../research/allocation-boxing-measurement.md)
