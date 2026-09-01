---
Type: grilling
Status: open
Labels:
  - scope:api
  - scope:codegen
  - kind:serialization
Blocked by:
  - "03 value-class representation"
  - "05 sequential framing"
  - "06 validation model"
---

# Ticket 07 — Write/builder interface

## Question

`PROMPT.md` §3 says the writer "writes values into the array" — the framing suggests mutating a caller-owned `ByteArray` directly. Now that ticket 05 established **sequential length-delimited framing** (fixed-width LE length prefix per field, parse-forward nested sub-regions, count-prefixed repeats) and ticket 06 established that **the writer cannot emit a structurally invalid stream** (compile-time validation covers structure; only buffer bounds are checked at runtime), how is the write/builder API shaped?

- Is writing done by mutating a caller-owned `ByteArray`/`ByteBuffer` in place (mirroring the `readBits` read path), or by constructing an immutable in-memory tree that is then serialized?
- How are length-prefixed fields written under a **parse-forward** contract that has no random access — i.e. no backpatch into a forward-only buffer: reserve-and-fill (two passes), or buffer each nested payload then emit with its length, or build-then-serialize?
- How are nested composites and count-prefixed repeats framed on the write side to be byte-identical to what `readBits` (ticket 05) consumes?
- Does the builder mirror the generated value-class view API (symmetric read/write surface), and does it carry type-checked overloads for the v1 type set (ticket 04)?

Informed by 03 (value-class representation / zero-alloc read contract), 05 (sequential framing), and 06 (validation: writer output is structurally valid by construction — the only runtime-checked condition on the reader side is buffer exhaustion).
