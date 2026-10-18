# ADR-0001: Mutable view classes with write-through `var` setters on `VehicleTelemetry`

- Status: Superseded by [ADR-0006](0006-immutable-default-models.md) on
  2026-09-27
- Date: 2026-09-13
- Tags: api, wire, bc-break
- Superseded by: [ADR-0006: Immutable value-class views by
  default](0006-immutable-default-models.md)
- Conflicts with: the initial `PROMPT.md` §1 ("Fields must be exposed as Kotlin
  `val` properties") and kompact-spec ticket 07 ("generated value-class views
  are read-only")

> This ADR keeps the original reasons for a decision that ADR-0006 later
> replaced. Its statements about performance record design intent, not
> measurements. For the current position, read
> [ADR-0006](0006-immutable-default-models.md) and the
> [allocation research note](../research/allocation-boxing-measurement.md).

## Context

The initial `PROMPT.md` §1 described model fields as `val` properties over a
`ByteArray` that the caller owns. It did not promise zero allocation. Ticket 07
(write-builder-interface) also expected read-only views. In that design,
`KompactWriter` owns the write path, and "the generated value-class views
(ticket 02/03) are read-only."

`VehicleTelemetry` is the reference model for this contract. In a BLE
workflow, a consumer receives a small frame, changes one field (for example,
the speed), and sends the same buffer again. All of this runs on a hot path
where allocation matters. With `val`-only views, every change in place must go
through `KompactWriter`. The caller builds a new buffer and wraps it in a new
`VehicleTelemetry`. This round trip defeats the zero-copy value-class pattern,
because each receive, change, and send cycle allocates a new `ByteArray` for
every field change.

## Decision

Change the three field properties of `VehicleTelemetry` from `val` to `var`.
Each setter writes through to the backing `ByteArray` in place with
`KompactRuntime.writeBits` or `writeBitsBoolean`. Add a `Companion.create(...)`
factory that builds a new encoded frame with `KompactWriter` and wraps it.

The original design meant to keep a direct read shape. Getters used the
checked `readScalar` and `readBool` accessors with `getOrThrow()`. That shape
does not by itself give a zero-allocation contract. The result
representation, the call shape, and the platform can all affect allocation.
Only the write path gains a new option: mutation in place, next to the existing
`KompactWriter` path. `val raw: ByteArray` stays the wire-format backing store,
and the setters make no copy of it.

## Alternatives considered

1. Keep `val` and write only through `KompactWriter`, the ticket 07 path. This
   keeps the read-only contract exactly. Rejected: the BLE receive, change, and
   send cycle allocates a new `ByteArray` per field edit. That breaks the
   zero-copy intent of the value-class wrapper.
2. Add a separate `modify { it.speed = 30 }` method that returns a new wrapper.
   This avoids `var`, but the method still has to either produce another
   `ByteArray` or change shared storage and return the same wrapper. Rejected
   as the worse of both options: it hides the mutation, which violates the D7
   command and query distinction, and it does not avoid the shared-buffer
   tradeoff.
3. Add a distinct `MutableVehicleTelemetry` type. `VehicleTelemetry` stays a
   pure read-only view next to a mutable sibling. Rejected: this doubles the
   generated declarations for every model in the code generation strategy
   (ticket 02). The mutability gap also exists only in the hand-written
   example, and no code generator exists yet that could emit both types.

## Consequences

- Breaking semantic shift. Changing `val` to `var` adds public setters to a
  type that was read-only. On the JVM the change is binary compatible, because
  it adds methods and leaves existing reads alone. Source callers that relied
  on immutability can no longer enforce it at compile time.
- Shared-buffer mutation can surprise callers. The setter changes `raw` in
  place, so two `VehicleTelemetry` instances that wrap the same `ByteArray` see
  each other's writes. The KDoc and the README document the write-through
  contract. `raw` is already a `public val`, so a caller could already call
  `writeBits` on `raw` directly.
- Setters do not validate input, on purpose. `writeBits` does no range check
  on the incoming value. A caller that passes `batteryStatus = 20` (5 bits) to
  the 4-bit field silently stores `4`. This matches the raw
  `KompactRuntime.writeBits` contract: the write path is an unchecked mutation
  API, not a checked accessor. Getters stay checked
  (`readScalar(...).getOrThrow()`). Callers that need validation use
  `KompactWriter` to build outbound frames, or validate values before they
  call a setter.
- The klib ABI golden must be regenerated on macOS with `updateKotlinAbi`
  (ticket 10 testing model). Editing the golden by hand on Linux is a
  temporary workaround, and CI on macOS validates the result.

## Migration

No API is removed. Existing `val`-style read code compiles and behaves the same.
Callers that need immutable views copy `raw` before they change it, or keep
using the `KompactWriter` path to build outbound frames.
