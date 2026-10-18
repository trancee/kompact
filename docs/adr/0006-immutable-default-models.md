# ADR-0006: Immutable value-class views by default

- Status: Accepted and implemented. Performance measurements are not recorded.
- Date: 2026-09-18
- Tags: api, bc-break, wire
- Supersedes: [ADR-0001: Mutable view classes with write-through `var` setters
  on `VehicleTelemetry`](0001-mutable-view-classes-with-write-through-setters.md)
- Amended by: [ADR-0009](0009-common-only-code-generation.md). Generated views
  are named `<Name>View`, and the opt-in sibling is `Mutable<Name>View`.
- Reconsiders: [ticket 07, write and builder
  interface](../../.scratch/kompact-spec/issues/07-write-builder-interface.md).
  ADR-0001 departed from its consequence that "generated value-class views are
  read-only".

## Context

An external code review called the mutable value-class views a trap. Two views
over the same backing `ByteArray` see each other's writes. The in-place
setters silently truncate values that are wider than the field. A read view
that can change bytes also changes what every other owner of the array sees.
ADR-0001 departed from `PROMPT.md` §1 ("Fields must be exposed as Kotlin `val`
properties") on purpose, to avoid rebuilding a frame in a BLE receive, change,
and send workflow. That was the design reason. It was not a measured
performance result.

The review proposed views that are immutable by default, plus an explicit
`copy` or writer path. A change then becomes visible in the code and returns a
new frame, instead of silently changing a buffer that other views share. This
is a tradeoff about ownership and API shape. Before anyone describes it as a
performance guarantee, its cost must be measured for a target workload.

## Decision

1. Views are immutable by default. Model properties are read-only. `raw` stays
   available for interoperability, so the immutability is shallow. The view
   does not own a copy of the bytes.
2. Updates are explicit. To build a new frame without changing the current
   buffer, use a generated `copy(...)` or `KompactWriter`.
3. Mutable fixed-layout views are opt-in. A fixed-layout schema can request a
   separate `Mutable<Name>` sibling with `@KompactModel(mutable = true)`. Its
   setters write through to the shared array and store the low bits of the
   value. If truncation is not acceptable, the caller validates values first.
   Framed schemas do not support mutable views.
4. Default views have no write-through setters, so an ordinary read view
   cannot change bytes by surprise.

This decision supersedes ADR-0001. It does not promise any allocation or
latency result. See the
[allocation and boxing research note](../research/allocation-boxing-measurement.md).

## Alternatives considered

1. Keep ADR-0001 as it is. This keeps the in-place write path, and it keeps the
   shared-buffer trap. Rejected: both the review and the "shared-buffer
   mutation surprise" risk in ADR-0001 point to this decision.
2. Immutable views with a builder only, and no mutable option. Rejected:
   callers that need in-place writes on purpose can opt into a distinct
   `Mutable*` type. The mutable sibling makes write-through behavior visible,
   although it does not give exclusive ownership of the backing array.
3. Only a separate `MutableVehicleTelemetry` sibling (alternative 3 of
   ADR-0001). ADR-0001 rejected it because it doubles the generated
   declarations. This decision keeps the idea only as an opt-in `Mutable*`
   type, which is a narrower and deliberate version of it.

## Consequences

- Removing setters from the default view breaks binary and source
  compatibility. The change shipped in `0.4.0` under the pre-`1.0` versioning
  policy of the project.
- Shared-buffer mutation is still possible. Callers can change `raw` directly
  or opt into a mutable sibling. The type makes the choice explicit, but it
  does not give exclusive ownership of the array.
- An opt-in `Mutable*` type doubles the generated declarations for a model. To
  limit this, the generator emits the type only when the fixed-layout schema
  requests it.

## Migration

For an immutable update, use `copy(...)` or `KompactWriter` and wrap the
returned frame. If you need in-place writes on purpose, opt into the
fixed-layout `Mutable*` sibling. Read-only callers need no change. `raw` stays
available for passing bytes to a transport.

## References

- [ADR-0001](0001-mutable-view-classes-with-write-through-setters.md),
  superseded
- [Ticket 07, write and builder
  interface](../../.scratch/kompact-spec/issues/07-write-builder-interface.md)
- [Ticket 03, zero-allocation
  reads](../../.scratch/kompact-spec/issues/03-value-class-representation.md)
- [Borrowed views keep the owner
  visible](../architecture.md#borrowed-views-keep-the-owner-visible) in
  `docs/architecture.md`
- [Keep buffer ownership clear](../how-to/integrate-ble.md#keep-buffer-ownership-clear)
  in `docs/how-to/integrate-ble.md`
