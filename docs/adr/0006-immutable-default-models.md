# ADR-0006 — Immutable value-class views by default (supersedes ADR-0001)

- **Status:** Accepted (implemented; performance measurements are not recorded).
- **Tags:** api, bc-break, wire
- **Superseded by:** none
- **Supersedes:** [ADR-0001 — Mutable view classes: write-through `var` setters on `VehicleTelemetry`](0001-mutable-view-classes-with-write-through-setters.md)
- **Reconsiders:** [ticket 07 — write/builder interface](../../.scratch/kompact-spec/issues/07-write-builder-interface.md) (the "generated value-class views are read-only" consequence that ADR-0001 deviated from)

## Context

An external code review flagged the mutable value-class views as a
foot-gun: two views over the same backing `ByteArray` observe each other's
writes, the in-place setters truncate out-of-width values silently, and the
mutability makes a read view change the bytes visible to other owners. ADR-0001
deliberately deviated from `PROMPT.md` §1 ("Fields must be exposed as Kotlin
`val` properties") to avoid rebuilding a frame during a BLE
receive/modify/retransmit workflow. That was the original design rationale,
not a measured performance result.

The review's counter: immutable-by-default views plus an explicit `copy` or
writer path make mutation visible and return a new frame rather than silently
changing a buffer shared by other views. This is an ownership and API tradeoff;
its performance impact must be measured for a target workload before it is
described as a performance guarantee.

## Decision

1. **Views are immutable by default.** Model properties are read-only; `raw`
   remains available for interoperability, so immutability is shallow rather
   than an owned copy of the bytes.
2. **Updates are explicit.** Use a generated `copy(...)` or `KompactWriter` to
   build a new frame when changes should not mutate the current buffer.
3. **Mutable fixed-layout views are opt-in.** A fixed-layout schema can request
   a separate `Mutable<Name>` sibling with `@KompactModel(mutable = true)`.
   Its setters write through to the shared array and store the low bits of the
   supplied value; callers must validate values first when truncation is not
   acceptable. Framed schemas do not support mutable views.
4. **Default views have no write-through setters.** This avoids an unexpected
   mutation through an ordinary read view.

This decision supersedes ADR-0001. It does not establish an allocation or
latency guarantee; see the
[allocation and boxing research note](../research/allocation-boxing-measurement.md).

## Alternatives considered

1. **Keep ADR-0001 as-is.** Preserves the in-place write path but keeps the
   shared-buffer foot-gun. Rejected by this decision: the review and ADR-0001's
   own "shared-buffer mutation surprise" risk note both point here.
2. **Immutable views + builder-only, no mutable escape hatch.** Rejected because
   callers that intentionally need in-place writes can opt into a distinct
   `Mutable*` type. The mutable sibling makes write-through behavior visible,
   though it does not provide exclusive ownership of the backing array.
3. **Separate `MutableVehicleTelemetry` sibling only (ADR-0001 alternative 3).**
   Rejected as "the worse of both" in ADR-0001 because it hides mutation behind a
   method and doubles generated surface. Retained here only as an opt-in `Mutable*`
   type, which is a narrower, intentional version of the same idea.

## Risks

- **Binary/source break:** removing setters from the default view is a breaking
   change. It shipped in `0.4.0` under the project's pre-`1.0` versioning policy.
- **Shared-buffer mutation remains possible:** callers can still mutate `raw`
   directly or opt into a mutable sibling. The type makes the choice explicit,
   but does not provide exclusive ownership of the array.
- **Codegen surface:** an opt-in `Mutable*` type doubles generated declarations
   for a model. Mitigation: emit it only when the fixed-layout schema requests
   it, not always.

## Migration

Callers that want an immutable update use `copy(...)` or `KompactWriter` and
wrap the returned frame. Callers that intentionally need in-place writes opt
into the fixed-layout `Mutable*` sibling. Read-only callers are unaffected.
`raw` remains available for passing bytes to a transport.

## References
- [ADR-0001](0001-mutable-view-classes-with-write-through-setters.md) (superseded)
- [ticket 07 — write/builder interface](../../.scratch/kompact-spec/issues/07-write-builder-interface.md)
- [ticket 03 — zero-alloc reads](../../.scratch/kompact-spec/issues/03-value-class-representation.md)
- `docs/architecture.md` § "Borrowed views keep the owner visible"; `docs/how-to/integrate-ble.md` § "Keep buffer ownership clear"
