# ADR-0006 — Immutable value-class views by default (supersedes ADR-0001)

- **Status:** proposed
- **Tags:** api, bc-break, wire
- **Superseded by:** none
- **Supersedes:** [ADR-0001 — Mutable view classes: write-through `var` setters on `VehicleTelemetry`](0001-mutable-view-classes-with-write-through-setters.md)
- **Reconsiders:** [ticket 07 — write/builder interface](../../.scratch/kompact-spec/issues/07-write-builder-interface.md) (the "generated value-class views are read-only" consequence that ADR-0001 deviated from)

## Context

An external code review flagged the mutable value-class views as a
foot-gun: two views over the same backing `ByteArray` observe each other's
writes, the in-place setters truncate out-of-width values silently, and the
mutability fights with the otherwise zero-copy read contract. ADR-0001
deliberately deviated from `PROMPT.md` §1 ("Fields must be exposed as Kotlin
`val` properties") to enable a zero-allocation receive/modify/retransmit BLE
cycle (mutating one field without round-tripping through `KompactWriter`).

The review's counter: immutable-by-default views + an explicit builder/`copy`
API that returns a new `ByteArray` (or mutates a private buffer and re-wraps)
removes the shared-buffer surprise at the cost of one allocation per field edit
on the *write* path — which is acceptable because writes are not the
zero-allocation hot path (only scalar *reads* are, per ticket 03/10).

## Decision (Proposed)

1. **Views are immutable by default.** Generated and hand-written model value
   classes expose fields as `val`; `raw` is exposed for interop but the model
   does not mutate it silently. This realigns the product with `PROMPT.md` §1 and
   ticket 07's "read-only views" consequence.
2. **Writes go through `KompactWriter` / a builder.** The receive/modify/retransmit
   cycle uses `KompactWriter` to write the new field value(s) into a fresh buffer
   and wraps a new view — one allocation per edited frame, never per field read.
3. **A bounded mutable escape hatch, opt-in.** An explicit `MutableVehicleTelemetry`
   (or a `scratch` builder) may still wrap a private `ByteArray` and write through
   in-place, but it is a distinct type the caller must opt into — not the default.
4. **Remove the silent-truncation setters** from the primary view; keep range
   validation on outbound construction (`Companion.create(...)` / `KompactWriter`),
   as flagged in ADR-0001's risks.

This supersedes ADR-0001. The zero-allocation read contract (ticket 03/08/10) is
unaffected: getters stay checked and allocation-free.

## Alternatives considered

1. **Keep ADR-0001 as-is.** Preserves the zero-copy write path but keeps the
   shared-buffer foot-gun. Rejected by this proposal: the review and ADR-0001's
   own "shared-buffer mutation surprise" risk note both point here; the
   immutability cost is on the write path, which is already non-hot.
2. **Immutable views + builder-only, no mutable escape hatch.** Rejected (for v1):
   some BLE firmware-update workflows genuinely need an in-place edit buffer;
   provide it as an explicit `Mutable*` type so accidental sharing is impossible
   by default.
3. **Separate `MutableVehicleTelemetry` sibling only (ADR-0001 alternative 3).**
   Rejected as "the worse of both" in ADR-0001 because it hides mutation behind a
   method and doubles generated surface. Retained here only as an opt-in `Mutable*`
   type, which is a narrower, intentional version of the same idea.

## Risks

- **Binary/source break:** removing setters from the value class is a MAJOR
  change. Gate it on the v1 boundary (the API is SNAPSHOT today).
- **BLE modify/retransmit latency:** one `ByteArray` allocation per edited frame
   (vs. zero today). Acceptable because it is off the read hot path; verify with
   ticket 11's perf evidence before shipping.
- **Codegen surface:** an opt-in `Mutable*` type doubles generated declarations
   for every model. Mitigation: emit it only when the schema requests it, not
   always.

## Migration

Callers using the write-through setters switch to `KompactWriter` (or the
generated `Mutable*` builder) and wrap a new view on `build()`. Read-only callers
are unaffected. `raw` remains available for the existing "pass-through to BLE
characteristic" path (unchanged).

## References
- [ADR-0001](0001-mutable-view-classes-with-write-through-setters.md) (superseded)
- [ticket 07 — write/builder interface](../../.scratch/kompact-spec/issues/07-write-builder-interface.md)
- [ticket 03 — zero-alloc reads](../../.scratch/kompact-spec/issues/03-value-class-representation.md)
- `docs/architecture.md` § "Wire format"; `docs/how-to/integrate-ble.md` § "The shared ByteArray contract"
