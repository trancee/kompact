---
Type: grilling
Status: open
Labels:
  - scope:wire-format
  - kind:compatibility
Blocked by:
  - "05 sequential framing"
  - "06 validation model"
  - "07 write/builder interface"
  - "08 runtime error model"
---

# Ticket 09 — Versioning & schema evolution

## Question

Kompact's wire format is bit-packed, sequential, length-delimited, and **tagless** (05 + 06 + 08) — no per-field wire tags, because tags would break the compactness the framing commits to. How does schema evolution / forward-backward compatibility actually work?

The reader walks fields in declaration order via the `readBits…` surface (08). To skip a field it does **not** recognize, it must know how many bits to advance — which a length-prefix gives **only if the prefix width is known up front**. This is the crux of evolution over a tagless format.

Decide:
- **Skip / evolution model**: positional + additive-only with a **uniform** length-prefix width (any unknown trailing length-delimited field is skipped by consuming its prefix + payload; fixed-width scalar additions are *not* skippable and are therefore breaking) vs no forward compatibility (version each stream, migrate) vs per-field TLV tags (Protobuf-style — rejected, it breaks 05's compactness).
- **Version signaling**: a top-level fixed-width version prefix at stream start (fail-fast on an unknown version, per 06+08) vs a reserved-bits flag embedded in the first field.

Inherited constraints: 07 (the writer selects each field's length-prefix width at codegen — for skip to work, every length-delimited field must share one uniform prefix width); 08 (reads are typed results, never throw — so a skew yields a typed `BadLengthPrefix` / `UnknownSchemaVersion`, never a silent misread).
