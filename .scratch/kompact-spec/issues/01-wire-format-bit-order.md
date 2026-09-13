---
Type: research
Status: resolved
Labels: wayfinder:research
Blocked by: —
Findings: ../research/bit-order.md
---

## Question

`PROMPT.md` requires a bit-packed, zero-padding, sequential stream in which multi-bit values may cross byte boundaries, with identical read/write behavior on Android/JVM and Kotlin/Native iOS using `shl` / `shr` / `and` / `or` over common Kotlin `Byte` boundaries.

What is the canonical bit-ordering convention for comparable zero-copy bit-serial formats, how is a multi-bit integer that crosses a byte boundary assembled (which byte's bits are the low bits vs the high bits), and what is the idiomatic multiplatform Kotlin implementation? The answer locks the one decision without which `KompactRuntime.readBits` / `writeBits` cannot be tested for cross-platform equivalence.

## Answer

**Decision: LSB-first (little-endian) bit packing.** Multi-bit integers assemble least-significant-bit first: byte 0 holds the field's low bits (bits 0–7), byte 1 holds bits 8–15, and bit 0 is the LSB of the value. A cross-boundary read such as `readBits(raw, 4, 10)` takes the low 4 bits of byte 0 and the low 6 bits of byte 1.

**Runtime rule:** Kotlin `Byte` is signed, so every byte must be masked with `and 0xFF` (`byte.toInt() and 0xFF`) before `shl` / `or`; that masking makes the `shl` / `shr` / `and` / `or` sequence produce identical results on JVM and Kotlin/Native. Signed fields are sign-extended after assembly (two's complement on the assembled unsigned magnitude).

**Rejected:** MSB-first (ASN.1 PER) — a valid convention but not the dominant one; Cap'n Proto and SLAC both use LSB-first, and little-endian matches the x86/ARM native bit order Kompact targets.

Findings: [../research/bit-order.md](../research/bit-order.md).
