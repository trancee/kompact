---
Type: ticket
Status: deferred
Labels:
  - scope:runtime
  - scope:codegen
  - kind:versioning
  - kind:error-model
Blocked by:
  - "02 generation strategy"
  - "04 v1 type set"
  - "05 variable-length framing"
  - "07 write-builder interface"
Decides:
  - "09 versioning-schema-evolution"
  - "08 runtime error model"
  - "10 cross-platform testing model"
---

# 15 — Versioning surface deferred to v2

## Summary

Implement the versioning/schema-evolution surface (ticket 09) in **v2**, not v1.
The surface consists of three coupled parts; none ship in v1.

1. `UnsupportedSchemaVersion` — a new `KompactDecodeError` kind.
2. A **stream version prefix** at the head of a frame (read + write).
3. `CURRENT_SCHEMA_VERSION` plus `isVersionField` handling on generated views.

## Why v1 cannot absorb it

- v1 has **no code generator** (tickets 02, 04): there are no generated views, so
  `isVersionField` has nothing to attach to and `VehicleTelemetry` is hand-written.
- A stream version prefix is a **breaking wire-format change**; v1 frames already
  exist (see `references/` and ADR-0001's BLE cycle) with no upgrade/compat story.
- `UnsupportedSchemaVersion` without a version prefix is **dead public ABI** — it
  would never be produced, yet it lands in both `kompact.api` and `kompact.klib.api`
  (forcing a macOS-only golden regen: klib goldens cannot be regenerated on Linux,
  `strictValidation=true`). See `docs/ci.md` → "Regen Goldens (macOS, manual)".

## v1 invariants preserved

- `KompactDecodeError` stays the four ticket-06 kinds:
  `BoundsError`, `BadLengthPrefix`, `TruncatedNested`, `UnknownEnumCode`.
- `readLengthPrefix` / `writeLengthPrefix` remain length-only prefixes (no version
  tag); `INVALID_LENGTH_PREFIX` (`-1`) is the sole prefix-failure sentinel (Q9,
  named in `KompactFraming`).
- `readNested` classifies prefix overflow/overrun as `BadLengthPrefix` (not
  `TruncatedNested`) per the ticket-06 invariant table — the closest v1 gets to
  "a length-prefix that exceeds remaining bytes": see ADR-0002 and
  `KompactFraming.kt`.

## v2 open design (to resolve before implementing)

- **Prefix bit-width**: 8 bits (one byte) is enough for the foreseeable version
  count; reuse the existing `KompactRuntime.readBits`/`writeBits` 8-bit path.
- **Placement**: at the very head of the frame (bit offset 0), so `VehicleTelemetry`
  and all generated views can read it before decoding fields.
- **Default / unknown**: a version byte not equal to `CURRENT_SCHEMA_VERSION`
  → `UnsupportedSchemaVersion` on every checked accessor
  (`readScalar`/`readBool`/`readFloat`/`readDouble`/`readNested`). Match the
  error-mapping in `08 runtime error model`.
- **v1→v2 reader**: v2 must read v1 frames (version byte absent) — either a v1
  framing reader shim, or "no version byte = v1". Decide in v2 codegen (ticket 02).

## Tests required in v2

- valid version prefix round-trips (write the v2 header ↔ read it back).
- unknown version byte → `UnsupportedSchemaVersion` on every checked accessor.
- truncated version prefix (region shorter than 1 byte) → bounds/`TruncatedNested`
  error (not `UnsupportedSchemaVersion`) — the version read precedes the version
  compare, mirroring the `readLengthPrefix` overrun→`BadLengthPrefix` ordering.
- boundary: version `0` vs `CURRENT_SCHEMA_VERSION`; version `0xFF`.
- T10 compat matrix: v1-frame read by v2 reader, v2-frame read by v2 reader.

## Status

Deferred. Blocked on v2 codegen (tickets 02, 04) and the v1 framing prerequisites
(tickets 05, 07). See `docs/adr/0002-defer-versioning-surface-to-v2.md`.
