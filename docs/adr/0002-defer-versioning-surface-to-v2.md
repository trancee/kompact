# ADR-0002 — Defer the versioning/schema-evolution surface to v2

- **Status:** accepted (2026-09-09)
- **Tags:** api, versioning, v2
- **Related:** [ADR-0008](0008-framed-generated-views.md) adds generated framed views;
  it does not add stream versioning.
- **Contradicts:** none (intentional deferral, not a contradiction of an existing
  rule)

## Context

kompact-spec ticket 09 (`versioning-schema-evolution`) requires a versioning
surface so consumers can detect and reject frames they cannot parse — in
particular a `UnsupportedSchemaVersion` error kind and a stream *version prefix*
at the head of a frame, so the T10 compatibility matrix can be authored.

At the time this ADR was accepted, v1 had no code generator. ADR-0008 later
added generated framed views, but neither those views nor the manual framing
helpers add a version tag to the wire format. Consequently:

- `KompactDecodeError` has no `UnsupportedSchemaVersion` case, and no decoder
  can distinguish an unsupported schema version.
- `writeLengthPrefix` / `readLengthPrefix` write and read only the per-field
  payload byte-count; no byte in the frame encodes a schema/frame version.
- `VehicleTelemetry` (the reference generated view) has no version field and no
  `isVersionField` handling in its accessors.

Two concrete reasons to **defer** the versioning surface to v2 rather than ship
it in v1:

1. **Breaking wire change.** A stream version prefix is a byte-level frame change.
   v1 frames already exist (see `references/` and the BLE usage in ADR-0001);
   inserting a prefix at the head of the frame is a breaking format change.
   Existing code generation does not provide a versioned wire format or a
   migration strategy for those frames.
2. **ABI churn for a dead producer.** Adding the `UnsupportedSchemaVersion` enum
   constant is a public ABI expansion (it lands in both `kompact.api` and
   `kompact.klib.api`), yet with no version prefix there is **no code path** that
   ever returns it — dead public surface that nonetheless forces a golden regen on
   macOS (klib goldens cannot be regenerated on Linux; strictValidation=true
   makes `checkKotlinAbi` fail on non-Apple hosts.

## Decision

Do **not** implement the versioning surface without a compatible wire-format
and migration decision. Keep `UnsupportedSchemaVersion` absent;
`readLengthPrefix` / `writeLengthPrefix` remain length-only prefixes, and
`VehicleTelemetry` remains a positional, version-less model. Framed generated
views do not alter this decision.

The versioning surface — `UnsupportedSchemaVersion`, a stream version prefix, a
`CURRENT_SCHEMA_VERSION`, and the `isVersionField` accessor path on generated
views — is deferred to v2, where the codegen (ticket 02/04) can emit version-aware
views and a real upgrade/compat story can be designed.

## Alternatives considered

1. **Implement the full versioning surface now.** Add a 1-byte stream version
   prefix at the frame head, a `CURRENT_SCHEMA_VERSION`, `UnsupportedSchemaVersion`,
   and `isVersionField` handling. Rejected: breaking wire-format change that
   existing version-less frames cannot consume; high scope; needs a versioning strategy
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
  matrix against; version-compatibility tests require a separately approved
  versioned wire format and migration path.
- **Future v2 wire change is breaking.** v1 consumers (e.g. the BLE
  receive/modify/retransmit cycle in ADR-0001) will need a migration path when v2
  introduces a version prefix. Mitigated by: (a) freezing the v1 format now so the
  v1 contract is precise, and (b) v2 designing the prefix as a trailing/extensible
  field where possible, or shipping a v1→v2 reader shim in the codegen.
- **False sense of "version-less = forever".** Consumers must not assume v1 frames
  are stable across the v1→v2 transition; document that the version prefix is the
  v2 escape hatch, not a v1 guarantee.

## Migration

No change for existing version-less consumers: the public ABI, wire format, and
manual `KompactFraming` prefix contract remain unchanged. A future versioned
wire-format revision must introduce the version prefix and
`UnsupportedSchemaVersion` together with a v1 migration strategy and compatibility
tests.
