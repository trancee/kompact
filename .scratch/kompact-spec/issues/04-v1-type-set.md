---
Type: grilling
Status: open
Labels: wayfinder:grilling
Blocked by: —
Depends on: 01-wire-format-bit-order (resolved)
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
