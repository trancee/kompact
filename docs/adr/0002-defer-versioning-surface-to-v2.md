# ADR-0002: Defer schema versioning to v2

- Status: Accepted
- Date: 2026-09-09
- Tags: api, versioning, v2
- Related: [ADR-0008](0008-framed-generated-views.md) adds generated framed
  views. It does not add stream versioning.
- Contradicts: none. This is a deliberate deferral, not a reversal of an
  existing rule.

## Context

kompact-spec ticket 09 (`versioning-schema-evolution`) asks for a versioning
API so that consumers can detect and reject frames they cannot parse. It
names two parts: an `UnsupportedSchemaVersion` error kind, and a stream version
prefix at the head of each frame. The T10 compatibility matrix depends on both.

When this ADR was accepted, v1 had no code generator. ADR-0008 later added
generated framed views. Neither those views nor the manual framing helpers
write a version tag into the wire format. As a result:

- `KompactDecodeError` has no `UnsupportedSchemaVersion` case, and no decoder
  can recognize an unsupported schema version.
- `writeLengthPrefix` and `readLengthPrefix` write and read only the byte count
  of one field's payload. No byte in the frame encodes a schema or frame
  version.
- `VehicleTelemetry`, the reference fixed-layout view, has no version field
  and no `isVersionField` handling in its accessors. Since
  [ADR-0010](0010-minimal-checked-result-surface.md), `VehicleTelemetry` is a
  test fixture.

Two reasons favor deferring the versioning API to v2 instead of shipping it in
v1:

1. A stream version prefix is a breaking wire change. It changes the bytes of
   every frame. v1 frames already exist (see `references/` and the BLE usage in
   ADR-0001), and a prefix at the head of the frame breaks them. The existing
   code generation offers no versioned wire format and no migration strategy
   for those frames.
2. The new error kind would be public ABI with no producer. Adding the
   `UnsupportedSchemaVersion` enum constant expands the public ABI in both
   `kompact.api` and `kompact.klib.api`. Without a version prefix, no code path
   ever returns it. The dead constant would still force a golden update on
   macOS. Linux cannot regenerate klib goldens, and with
   `strictValidation=true`, `checkKotlinAbi` fails on hosts that are not Apple.

## Decision

Do not add the versioning API until a compatible wire format and a migration
plan are decided. Keep `UnsupportedSchemaVersion` out of the API.
`readLengthPrefix` and `writeLengthPrefix` stay length-only prefixes, and
`VehicleTelemetry` stays a positional model without a version. Framed
generated views do not change this decision.

The versioning API is deferred to v2. It consists of
`UnsupportedSchemaVersion`, a stream version prefix, a
`CURRENT_SCHEMA_VERSION`, and the `isVersionField` accessor path on generated
views. In v2, the code generator (ticket 02/04) can emit version-aware views,
and the project can design a real upgrade and compatibility plan.

## Alternatives considered

1. Implement the full versioning API now: a 1-byte stream version prefix at
   the head of the frame, `CURRENT_SCHEMA_VERSION`, `UnsupportedSchemaVersion`,
   and `isVersionField` handling. Rejected: it is a breaking wire change that
   existing frames without a version cannot read. The scope is large, and it
   needs a versioning strategy (prefix bit width, placement, number of
   versions, upgrade path) that is out of scope for a v1 feature freeze.
2. Add only the `UnsupportedSchemaVersion` enum constant, with no wire change.
   Rejected: nothing produces the constant, because there is no version
   prefix. It would be dead public ABI that forces a macOS-only klib golden
   update for no behavior. That goes against the rule of shipping no public
   API that nothing can exercise.
3. Version silently by treating unknown bytes as payload. Rejected: this
   removes the only safe way to fail on a frame that cannot be read. It also
   contradicts the "fail fast, never silent" invariant of ticket 06, the same
   invariant that the reclassification of `readNested` followed.

## Consequences

- The T10 compatibility matrix stays unbuilt. Without a version prefix there
  is nothing to test across versions. Version-compatibility tests need a
  separately approved versioned wire format and migration path.
- The future v2 wire change is breaking. v1 consumers, such as the BLE
  receive, change, and send cycle in ADR-0001, need a migration path when v2
  adds a version prefix. Two measures reduce the risk. First, v1 freezes its
  format now, so the v1 contract is precise. Second, v2 can design the prefix
  as a trailing or extensible field where possible, or ship a v1-to-v2 reader
  in the code generator.
- Consumers might read "no version" as "stable forever". They must not assume
  that v1 frames stay valid across the move from v1 to v2. The documentation
  states that the version prefix is the planned v2 change, not a v1 guarantee.

## Migration

Existing consumers without a version need no change. The public ABI, the wire
format, and the manual `KompactFraming` prefix contract stay the same. A future
versioned wire format must add the version prefix and
`UnsupportedSchemaVersion` together, with a migration strategy for v1 frames and
compatibility tests.
