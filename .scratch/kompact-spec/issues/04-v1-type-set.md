---
Type: grilling
Status: resolved
Labels: wayfinder:grilling
Blocked by: —
---

## Question

`PROMPT.md`'s example fields are Enum (4 bits), Int (10 bits), and Boolean (1 bit). That is one sketch, not a type set. What is the complete scalar + composite type set the v1 wire format and `KompactRuntime` must support?

Specifically decide:
- Signed vs unsigned integers at which bit widths (the performance workload matrix names 1–64, signed 2/7/10/32/64, unsigned 1/5/8/10/16/32/64).
- Enum encoding: dense ordinal (the `0–15` sketch) vs explicit codes; gapped / unknown-code handling.
- Floats: 32- and 64-bit IEEE-754 with canonicalized NaN; in scope or deferred?
- Variable-length / strings / blobs: varint + length prefix, or fixed-width only?
- Nested composites (a field that is itself a bit-packed struct) and repeated fields: one layout, or offset/delimited?
- If variable-length is included, the bit-width of the field-length / envelope metadata.

This decision gates `KompactRuntime.readBits` / `writeBits` overloads, the `@KompactField` annotation surface, and the cross-platform test matrix's width coverage. Resolve before the validation, write/builder, or error-model tickets.

## Answer

**Decision (user-resolved): the full v1 type set, including variable-length.** Per the live exchange, Kompact v1 supports:

- **Unsigned integers** at declared bit widths 1–64.
- **Signed integers** at declared bit widths 1–64 (two's-complement on the assembled magnitude).
- **Booleans** — 1 bit.
- **Enums** — dense ordinal at a declared 1–8-bit width; an unknown code yields a typed error result (fail closed), not a silent default.
- **Floats** — IEEE-754 32-bit and 64-bit, with NaN canonicalized to a single canonical bit pattern.
- **Variable-length values** — strings and blobs, length-framed (NOT deferred).
- **Nested composites** — a bit-packed struct used as a field (NOT deferred).
- **Repeated fields** — ordered sequences (NOT deferred).

**Scope implication:** this is a deliberate expansion beyond `PROMPT.md`'s 2-byte `VehicleTelemetry` sketch (Enum + Int + Boolean only). v1 now requires an **envelope / framing contract** for length-prefixed, nested, and repeated fields — the substance of ticket 05. `readBits` / `writeBits` widen accordingly (length-prefix + nested base-offset + count handling); the `@KompactField` surface gains length / nesting / repeat annotations.

**Risk note:** v1 is now substantially larger than the PROMPT sketch. The framing (05), write/builder (fog), validation (fog), error model (fog), and versioning (fog) tickets must lock before implementation; each adds surface. The inclusion of variable-length / nested / repeated is intentional — flag if v1 should be trimmed back to the fixed-width sketch instead.
