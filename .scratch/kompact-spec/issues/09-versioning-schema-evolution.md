---
Type: grilling
Status: resolved
Labels:
  - scope:wire-format
  - kind:compatibility
Blocked by:
  - "05 sequential framing"
  - "06 validation model"
  - "07 write/builder interface"
  - "08 runtime error model"
Decides:
  - "10 cross-platform testing model"
---

# Ticket 09 — Versioning & schema evolution

## Question

Kompact's wire format is bit-packed, sequential, length-delimited, and **tagless** (05 + 06 + 08) — no per-field wire tags, because tags would break the compactness the framing commits to. How does schema evolution / forward-backward compatibility actually work?

The reader walks fields in declaration order via the `readBits…` surface (08). To skip a field it does **not** recognize, it must know how many bits to advance — which a length-prefix gives **only if the prefix width is known up front**. This is the crux of evolution over a tagless format.

Decide:
- **Skip / evolution model**: positional + additive-only with a **uniform** length-prefix width (any unknown trailing length-delimited field is skipped by consuming its prefix + payload; fixed-width scalar additions are *not* skippable and are therefore breaking) vs no forward compatibility (version each stream, migrate) vs per-field TLV tags (Protobuf-style — rejected, it breaks 05's compactness).
- **Version signaling**: a top-level fixed-width version prefix at stream start (fail-fast on an unknown version, per 06+08) vs a reserved-bits flag embedded in the first field.

Inherited constraints: 07 (the writer selects each field's length-prefix width at codegen — for skip to work, every length-delimited field must share one uniform prefix width); 08 (reads are typed results, never throw — so a skew yields a typed `BadLengthPrefix` / `UnknownSchemaVersion`, never a silent misread).

## Answer

User decided: adopt the recommended option on both forks.

**1. Skip / evolution model — positional + additive-only, uniform length-prefix width.**

Fields are read in declaration order (ticket 05); the wire is a flat sequence, not TLV. For an older reader to **skip** a field it does not recognize, it must consume `prefix + payload` — which works only if the prefix **width** is known without consulting the schema of the unknown field. Therefore **all length-delimited fields in a stream share one uniform prefix width** (e.g. 16-bit LE, chosen once per stream/struct at codegen by the writer, ticket 07). Then:

- **Forward compatibility** (older reader, newer stream): unknown *trailing* length-delimited fields are skipped by reading the uniform-width prefix + payload. (Fixed-width scalar fields cannot be added backward-compatibly — an older reader can't size an unknown fixed-width field — so appending a fixed-width field is a **breaking** change.)
- **Backward compatibility** (newer reader, older stream): fewer fields present → missing trailing fields are read as their **declared default value**.
- **Breaking changes** (documented in the spec's evolution section): reorder fields, insert a fixed-width scalar field, change a field's bit-width, or change the stream's uniform prefix width. Adding a length-delimited field at the end is non-breaking.
- **Skew is fail-fast, never silent** (06 + 08): a length-prefix that exceeds remaining bytes is a typed `BadLengthPrefix`; an unsupported stream version is a typed `UnsupportedSchemaVersion`. No silent truncation / misread.

Rejected:
- "No forward compatibility — migrate each stream version." The length-delimited framing already enables skip via uniform prefixes; migration-only is weak for a framework and discards the framing's natural skip.
- "Per-field TLV tags (Protobuf-style)." A tag per field breaks the compactness (05) the bit-packed format commits to.

**2. Version signaling — top-level fixed-width version prefix at stream start.**

The stream begins with a fixed-width (e.g. 16-bit LE) version number. The reader checks it first; an unknown version → fail-fast typed `UnsupportedSchemaVersion` (06 + 08), never a silent decode. The version prefix is decoupled from any field layout, so it is stable across schema evolution.

Rejected: a reserved-bits flag in the first field — couples version detection to field 0's layout, so any change to field 0 breaks version detection.

**Tradeoff accepted.** The **uniform length-prefix width** is a real restriction: you cannot mix 8-bit prefixes for short fields with 16-bit prefixes for long fields if you want forward-compat skip. The more-compact alternative (heterogeneous prefix widths) is forbidden by the combination of 05 (compactness) + forward-compatibility. This is the necessary bridge between Kompact's compactness and its evolvability — a deliberate, documented constraint. The version prefix costs a fixed 2 bytes per stream (or 1, if 8-bit is chosen).

**Consequences.**
- 10 cross-platform testing model: the compatibility matrix **must** exercise both directions of the additive model — newer-writer/old-reader (trailing-field skip) and old-writer/newer-reader (defaults for missing fields) — plus version-skew (`UnsupportedSchemaVersion`) and malformed-prefix (`BadLengthPrefix`) paths (06 + 08). This is the correctness surface the testing model (10) locks.
- The spec's evolution section will enumerate the breaking-change rules above so downstream authors can evolve without silent breakage.
- 11 (performance-evidence) and 12 (module split) are unaffected by this decision (wire-format level).

## References
- ticket 05 (sequential length-delimited framing; parse-forward) 
- ticket 06 (fail-fast typed errors on bad length-prefix / bounds) 
- ticket 07 (writer selects length-prefix widths at codegen — must be uniform) 
- ticket 08 (typed results, never throw) 
