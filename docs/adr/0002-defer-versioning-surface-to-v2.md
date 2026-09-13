# ADR-0002 — Defer the versioning/schema-evolution surface to v2

- **Status:** accepted (2026-09-09)
- **Tags:** api, versioning, v2
- **Superseded by:** none
- **Contradicts:** none (intentional deferral, not a contradiction of an existing
  rule)

## Context

kompact-spec ticket 09 (`versioning-schema-evolution`) requires a versioning
surface so consumers can detect and reject frames they cannot parse — in
particular a `UnsupportedSchemaVersion` error kind and a stream *version prefix*
at the head of a frame, so the T10 compatibility matrix can be authored.

v1 has **no code generator** (kompact-spec ticket 02: "generation strategy";
ticket 04: "v1 type set"). The wire format is therefore fixed and final for v1:
fields are positional, framed by the length-prefix scheme in
ticket 05, and there is no version tag anywhere in `KompactFraming` /
`KompactWriter`. Consequently:

- `KompactDecodeError` exposes only the four ticket-06 kinds —
  `BoundsError`, `BadLengthPrefix`, `TruncatedNested`, `UnknownEnumCode` — and
  nowhere produces an `UnsupportedSchemaVersion`.
- `writeLengthPrefix` / `readLengthPrefix` write and read only the per-field
  payload byte-count; no byte in the frame encodes a schema/frame version.
- `VehicleTelemetry` (the reference generated view) has no version field and no
  `isVersionField` handling in its accessors.

Two concrete reasons to **defer** the versioning surface to v2 rather than ship
it in v1:

1. **Breaking wire change.** A stream version prefix is a byte-level frame change.
   v1 frames already exist (see `references/` and the BLE usage in ADR-0001);
   inserting a prefix at the head of the frame is a breaking format change with no
   backwards-compat story in v1 (there is no codegen to emit versioned views).
2. **ABI churn for a dead producer.** Adding the `UnsupportedSchemaVersion` enum
   constant is a public ABI expansion (it lands in both `kompact.api` and
   `kompact.klib.api`), yet with no version prefix there is **no code path** that
   ever returns it — dead public surface that nonetheless forces a golden regen on
   macOS (klib goldens cannot be regenerated on Linux; strictValidation=true
   makes `checkKotlinAbi` fail on non-Apple hosts.

## Decision

Do **not** implement the versioning surface in v1. Keep `KompactDecodeError` at
its four ticket-06 kinds; keep `readLengthPrefix` / `writeLengthPrefix` as
length-only prefixes (no stream version tag); keep `VehicleTelemetry` as a
positional, version-less model. The v1 wire format is frozen.

The versioning surface — `UnsupportedSchemaVersion`, a stream version prefix, a
`CURRENT_SCHEMA_VERSION`, and the `isVersionField` accessor path on generated
views — is deferred to v2, where the codegen (ticket 02/04) can emit version-aware
views and a real upgrade/compat story can be designed.

## Alternatives considered

1. **Implement the full versioning surface now.** Add a 1-byte stream version
   prefix at the frame head, a `CURRENT_SCHEMA_VERSION`, `UnsupportedSchemaVersion`,
   and `isVersionField` handling. Rejected: breaking wire-format change for a v1
   that has no codegen to consume it; high scope; needs a versioning strategy
   (prefix bit-width, placement, how many versions, upgrade path) that is out of
   scope for a v1 feature-freeze.
2. **Add only `UnsupportedSchemaVersion` (enum constant, no wire change).** Rejected:
   the constant has no producer (no version prefix) → dead public ABI → forces a
   macOS-only klib golden regen for zero behaviour. Violates the zero-dead-surface
   intent and the "don't ship ABI you can't exercise" spirit.
3. **Silent versioning (treat unknown bytes as payload).** Rejected: it removes the
   only safe failure mode for an unreadable frame and contradicts ticket 06's
   "fail-fast, never silent" invariant (the same invariant that the
   `readNested` reclassification honoured).

## Risks

- **T10 compat matrix unbuilt.** Without a version prefix there is nothing to
  matrix against; the T10 "version compatibility" tests cannot be authored. This
  is accepted: T10 is gated on the v2 codegen.
- **Future v2 wire change is breaking.** v1 consumers (e.g. the BLE
  receive/modify/retransmit cycle in ADR-0001) will need a migration path when v2
  introduces a version prefix. Mitigated by: (a) freezing the v1 format now so the
  v1 contract is precise, and (b) v2 designing the prefix as a trailing/extensible
  field where possible, or shipping a v1→v2 reader shim in the codegen.
- **False sense of "version-less = forever".** Consumers must not assume v1 frames
  are stable across the v1→v2 transition; document that the version prefix is the
  v2 escape hatch, not a v1 guarantee.

## Migration

No change for v1 consumers: the public ABI (`KompactDecodeError` kinds), the wire
format, and the `KompactFraming` API surface are unchanged. v2 (when the codegen
lands) will introduce the version prefix and `UnsupportedSchemaVersion` together,
at which point a v1→v2 reader strategy and T10 compat tests become possible.
