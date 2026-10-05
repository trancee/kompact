# Schema evolution research

## Finding

The old `.scratch/kompact-spec/map.md` records a top-level version prefix as
decided, but it is a historical planning artifact. Accepted
[`ADR-0002`](../../../docs/adr/0002-defer-versioning-surface-to-v2.md)
explicitly refers to that earlier plan and defers versioning to v2. Current
code and [`docs/architecture.md`](../../../docs/architecture.md) match the
ADR: frames have no Kompact-owned schema-version header; applications needing
mixed versions must define their own discriminator and migration policy.

`KompactDecodeError` has no `UnsupportedSchemaVersion` case.
`KompactFraming` uses per-field 8-, 16-, or 32-bit little-endian byte-count
prefixes; these are not a stream version. ADR-0008 adds sequential framed
views but does not change this decision. Prefix width is selected per field;
the old map's uniform-prefix/additive-skip mechanism is not an enforced
current contract.

The current framed format is positional and strict. Appending a field is not
automatically compatible: old readers may reject trailing bytes, and new
readers may require a field old senders omit. The concrete receive/modify/
retransmit use case makes a future format transition consequential. ADR-0002
identifies a v1-to-v2 migration strategy as unresolved, not as implemented
behavior.

## Decision options for ticket 10

1. Keep versioning outside Kompact: callers define an envelope and migration
   policy when mixed-version communication is needed.
2. Add a Kompact-owned versioned frame contract before the supported release,
   defining header encoding, unknown-version failure, and migration behavior
   together.
3. Explicitly keep the initial contract versionless and defer the decision,
   documenting that the wire format does not promise cross-version
   compatibility.

These are decision alternatives, not a recommendation or a claim that the
legacy spec map remains binding.

## Evidence

- `.scratch/kompact-spec/map.md` and
  `.scratch/kompact-spec/issues/09-versioning-schema-evolution.md`: historical
  planning decision for a version prefix.
- `docs/adr/0002-defer-versioning-surface-to-v2.md`: accepted decision
  deferring the versioning surface.
- `docs/adr/0008-framed-generated-views.md`: accepted sequential framing
  decision; it preserves the existing wire format.
- `docs/architecture.md`: current positional, strict schema-evolution behavior
  and caller-owned versioning policy.
- `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactDecodeError.kt`:
  current typed decode errors; no unsupported-version error.
- `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactFraming.kt`:
  per-field length/count prefix implementation.
