---
Type: grilling
Status: open
Labels: wayfinder:grilling
Blocked by: 04-v1-type-set (resolved)
---

## Question

Ticket 04 committed v1 to **variable-length strings/blobs, nested composites, and repeated fields** — beyond `PROMPT.md`'s fixed 2-byte sketch. How must Kompact frame these in the bit-packed stream? Three coupled choices:

1. **Variable-length length-prefix**: varint (Protobuf-style — compact, variable CPU) vs a fixed 1/2/4-byte little-endian prefix (predictable decode) vs a per-field-declared prefix width.
2. **Nested composite layout**: bit-offset **relative to the parent's start** (nested fields re-base at the parent's first bit — local offset math, parent needs a base pointer / length) vs **absolute** bit-offset from the stream start (simpler reads, parent can't move without recomputation).
3. **Repeated fields**: **count-prefixed** (one `N` then `N` fixed-or-variable elements) vs **length-delimited** (one total length then the elements).

This decides the envelope / framing contract that `KompactRuntime` and the generated getters must implement; it gates the write/builder interface, validation, error model, and versioning tickets. Resolve before any non-fixed-width runtime code is written.

## Answer

_(pending — next frontier decision)_
