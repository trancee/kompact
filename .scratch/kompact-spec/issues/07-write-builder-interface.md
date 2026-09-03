---
Type: grilling
Status: resolved
Labels:
  - scope:api
  - scope:codegen
  - kind:serialization
Blocked by:
  - "03 value-class representation"
  - "05 sequential framing"
  - "06 validation model"
Decides:
  - "08 runtime error model"
---

# Ticket 07 — Write/builder interface

## Question

`PROMPT.md` §3 says the writer "writes values into the array" — the framing suggests mutating a caller-owned `ByteArray` directly. Now that ticket 05 established **sequential length-delimited framing** (fixed-width LE length prefix per field, parse-forward nested sub-regions, count-prefixed repeats) and ticket 06 established that **the writer cannot emit a structurally invalid stream** (compile-time validation covers structure; only buffer bounds are checked at runtime), how is the write/builder API shaped?

- Is writing done by mutating a caller-owned `ByteArray`/`ByteBuffer` in place (mirroring the `readBits` read path), or by constructing an immutable in-memory tree that is then serialized?
- How are length-prefixed fields written under a **parse-forward** contract that has no random access — i.e. no backpatch into a forward-only buffer: reserve-and-fill (two passes), or buffer each nested payload then emit with its length, or build-then-serialize?
- How are nested composites and count-prefixed repeats framed on the write side to be byte-identical to what `readBits` (ticket 05) consumes?
- Does the builder mirror the generated value-class view API (symmetric read/write surface), and does it carry type-checked overloads for the v1 type set (ticket 04)?

Informed by 03 (value-class representation / zero-alloc read contract), 05 (sequential framing), and 06 (validation: writer output is structurally valid by construction — the only runtime-checked condition on the reader side is buffer exhaustion).

## Answer

User decided: adopt the recommended option on both forks.

**1. Surface — builder over a writer-owned growable buffer; `build(): ByteArray`.**
- `KompactWriter` is hand-written **common API** (no `@JvmInline`); it owns a growable internal byte buffer. Fields are appended sequentially, forward — the writer advances a cursor, never backtracks.
- `build(): ByteArray` snapshots the result. The reader then consumes that `ByteArray` via the ticket-03 caller-owned-`ByteArray` read path, so write → `ByteArray` → read is **symmetric**.
- `build()` returns a `ByteArray` (platform detail follows ticket-03 ABI rules: JVM may return the internal array directly or a defensive copy when shared; iOS copies across the ABI boundary so the consumer gets a Swift-value struct, not a Kotlin heap object). The 03 "boxing only at type-erasure / ABI boundaries" rule applies here, not on the scalar read hot path.
- Rejected: in-place mutation of a caller-owned, pre-sized `ByteArray` — length-delimited framing makes total size unknown until nested payloads are written, so pre-sizing forces the caller to do a size-computation pass and invites overflow. The writer owns its buffer.

**2. Nested / repeat mechanism — sub-writer per nested; typed API mirroring reads.**
- A child `KompactWriter` builds each nested composite and each repeated element batch; the child's fully-computed length is then emitted as the **fixed-width LE prefix** declared for that field (width fixed at compile time by ticket 06) followed by the bytes. This is forward-only — **no backpatch** — because the length is known before the prefix slot is written.
- Count-prefixed repeats emit `<count><elem₀><elem₁>…` (`count` = the field's validated count width; repeat the element writes).
- The writer API mirrors the read side: `writeInt1/8/16/32/64`, `writeUInt1/8/16/32/64` (two's-complement magnitude assembled per ticket 04), `writeBool`, `writeEnum(code, width)`, `writeString`/`writeBlob` (length-prefixed), `writeNested { w -> … }`, `writeRepeated(countWidth) { w -> … }`. Each typed write carries the field's compile-time-validated length-prefix width and value width — so a structurally-invalid stream is impossible to produce.
- Rejected: a thin `writeBits`/`writeBytes(len, bytes)` that lets the caller supply the length — it re-exposes raw length-prefix to the caller, re-introduces the structural-invalid-stream risk ticket 06 closed, and is asymmetric with the typed read path.

**Tradeoff accepted.** The writer-owned growable buffer allocates during the build (amortized growth); `build()` may copy on iOS. This is the **write path**, which is explicitly *not* bound by ticket 03's zero-alloc read contract (that contract protects the read hot path only). Nested sub-writers add transient allocation proportional to nesting depth × payload — acceptable, single-pass, and backpatch-free.

**Consequences.**
- The writer's output is **structurally valid by construction** (prefix widths fixed at compile time; nested lengths always computed before emission). The only runtime-checked condition the reader can hit on this stream is buffer exhaustion / bounds — exactly the `BoundsError`/`BadLengthPrefix`/`TruncatedNested`/`UnknownEnumCode` taxonomy ticket 06 reserved. No reader-side structural surprise.
- 08 runtime error model: informed — the writer never produces these errors; only readers see them on untrusted input.
- 09 versioning & schema evolution: informed by 07 (the writer picks each field's length-prefix width at codegen time; evolution = additive field IDs + reserved bits).
- Generation touchpoint: the writer is hand-written common API, **not** generated per struct; the generated value-class views (ticket 02/03) are read-only. Generating write-side views is a future 02-strategy follow-up, not 07.

## References
- ticket 03 (zero-alloc `readBits` over a caller-owned `ByteArray`; ABI-boundary boxing) 
- ticket 04 (v1 type set: ints/signed/enum widths/float NaN) 
- ticket 05 (sequential length-delimited framing; no random access) 
- ticket 06 (compile-time-validated length-prefix + value widths; symbol-located errors) 
