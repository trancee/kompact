# ADR-0001 — Mutable view classes: write-through `var` setters on `VehicleTelemetry`

- **Status:** accepted (2026-09-06)
- **Tags:** api, wire, bc-break
- **Superseded by:** none
- **Contradicts:** PROMPT.md §1 ("Fields must be exposed as Kotlin `val` properties"), kompact-spec ticket 07 ("generated value-class views are read-only")

## Context

PROMPT.md §1 requires model fields to be `val` — read-only zero-allocation views over a
caller-owned `ByteArray`. Ticket 07 (write-builder-interface) reinforces this: the `KompactWriter`
owns the write path, and "the generated value-class views (ticket 02/03) are read-only."

The VehicleTelemetry example is the reference model for this contract. In a BLE workflow, a consumer
receives a small frame, mutates one field (e.g. update speed), and re-transmits the same buffer —
all on a hot path where allocation matters. With `val`-only views, every in-place modification
requires round-tripping through `KompactWriter` (build a new buffer, then wrap it in a new
`VehicleTelemetry`). That defeats the zero-copy value-class pattern: the receive/modify/retransmit
cycle allocates a fresh `ByteArray` per field change.

## Decision

Change `VehicleTelemetry`'s three field properties from `val` to `var` with **write-through
setters** that mutate the backing `ByteArray` in-place via `KompactRuntime.writeBits` /
`writeBitsBoolean`. Add a `Companion.create(...)` factory that builds a fresh encoded frame via
`KompactWriter` and wraps it.

The zero-allocation read contract is preserved: getters still use the checked
`readScalar`/`readBool` accessors with `getOrThrow()` — same zero-alloc `Long`-packed result value
class. Only the write path gains an in-place mutation option alongside the existing `KompactWriter`
path. `val raw: ByteArray` remains the wire-format backing store; no copy is made on read or write.

## Alternatives considered

1. **Keep `val` — write via `KompactWriter` only (ticket 07 path).** Preserves the read-only
   contract exactly. Rejected: the receive/modify/retransmit BLE cycle allocates a fresh
   `ByteArray` per field edit, violating the zero-copy intent of the value-class wrapper.
2. **Add a separate `modify { it.speed = 30 }` method returning a new wrapper.** Avoids `var`
   but still requires wrapping a new `ByteArray` (or mutating the shared one and returning the
   same wrapper — same surprise as `var`, just hidden behind a method). Rejected as the worse of
   both: it hides the mutation (violates D7 command/query distinction) and provides no allocation
   benefit over direct `var`.
3. **Introduce a distinct `MutableVehicleTelemetry` type.** Keeps `VehicleTelemetry` as the pure
   read-only view and adds a mutable sibling. Rejected: doubles the generated surface area for
   every model in the codegen strategy (ticket 02), and the mutability gap only exists in the
   hand-written example — there is no codegen yet to emit both.

## Risks

- **Breaking semantic shift:** `val → var` adds public setters to a previously read-only type.
  Binary-compatible on the JVM (new methods only; existing reads unaffected), but source-level
  callers who relied on immutability cannot enforce it at compile time.
- **Shared-buffer mutation surprise:** the setter mutates `raw` in-place. Two `VehicleTelemetry`
  instances wrapping the same `ByteArray` will observe each other's writes. Mitigated by the
  KDoc and README documenting the write-through contract; `raw` is already `public val`, so
  direct `writeBits` on `raw` was already possible.
- **No setter input validation (intentional):** `writeBits` performs no range check on
  the incoming value — a caller passing `batteryStatus = 20` (5 bits) into the 4-bit
  field silently truncates to `4`. This mirrors the raw `KompactRuntime.writeBits`
  contract: the write path is a zero-overhead mutation API, not a checked accessor.
  Getters remain checked (`readScalar(...).getOrThrow()`); callers needing validation
  should use `KompactWriter` for outbound construction or validate before calling
  setters.
- **Klib ABI golden:** must be regenerated on macOS via `updateKotlinAbi` (ticket 10 testing model).
  Hand-editing the golden on Linux is a temporary workaround — CI on macOS will validate.

## Migration

No API removals. Existing `val`-style read code compiles and behaves identically. Callers who need
immutable views should copy `raw` before mutation or continue using the `KompactWriter` path for
outbound construction.
