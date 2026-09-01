---
Type: grilling
Status: resolved
Labels: wayfinder:grilling
Blocked by: 04-v1-type-set (resolved)
---

## Question

Ticket 04 committed v1 to **variable-length strings/blobs, nested composites, and repeated fields** — beyond `PROMPT.md`'s fixed 2-byte sketch. How must Kompact frame these in the bit-packed stream? Three coupled choices:

1. **Variable-length length-prefix**: varint vs fixed 1/2/4-byte vs per-field-declared.
2. **Nested layout**: self-delimiting length-delimited sub-region vs relative/absolute bit-offset.
3. **Repeated fields**: count-prefixed vs length-delimited.

This decides the envelope / framing contract that `KompactRuntime` and the generated getters must implement; it gates the write/builder interface, validation, error model, and versioning tickets. Resolve before any non-fixed-width runtime code is written.

## Answer

**Decision (user-resolved): sequential, length-delimited framing on the LSB-first bit stream (ticket 01's order).**

1. **Length-prefix — fixed-width little-endian, width declared per-field.** Variable-length fields carry an 8- or 16-bit LE byte-count (per-field, via an annotation). Varint is rejected: its loop-based decode and variable CPU conflict with the zero-allocation / predictable-read ethos, and Kompact's reader is fixed-width single-pass.
2. **Nested composites — length-delimited sub-regions, parse-forward.** A nested struct carries its total bit-length; reading it consumes those bits and siblings are reached by continuing to scan.
3. **Repeated fields — count-prefixed, sequential.** One fixed-width count, then `N` elements in order.

**Key tradeoff accepted (recorded so the spec does not over-promise):** reads are **sequential (parse-forward), not random-access.** This deliberately diverges from FlatBuffers-style offset-jump reads, because ticket 04's variable-length fields make stored offsets shift and break. `PROMPT.md`'s "zero-copy reads like FlatBuffers" is satisfied by the *view-class read path* (no allocation/copy to read a scalar), **not** by random access to every field; the cost of variable-length framing is sequential traversal.

This framing contract gates `KompactRuntime`'s length-prefix + nested-length + count helpers and the generator's envelope layout.
