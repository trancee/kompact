---
Type: grilling
Status: open
Labels:
  - scope:runtime
  - kind:error-model
Blocked by:
  - "06 validation model"
---

# Ticket 08 — Runtime error model (representation)

## Question

Ticket 06 fixed the **typed-result-not-throw** fork and the runtime error *types* (`BoundsError`, `BadLengthPrefix`, `TruncatedNested`, `UnknownEnumCode`). What remains is the **representation** of `KompactDecodeResult` — i.e. how those typed failures are carried on the ticket-03 zero-alloc read path. Informed by 06 + 05 (what readers can hit) + 04 (enum codes).

How is `KompactDecodeResult<T>` — and the `readBits`/`readBitsBoolean`/`readBool` surface from ticket 03 — shaped?

- **Representation**: a flat sealed-class hierarchy (`KompactDecodeResult<T> { data class Ok(T); sealed class Err : KompactDecodeResult<T> }`) vs a value-class over `(ok: Boolean, value: T, error: DecodeError)`. Must not allocate on the success / fast-path (ticket 03 zero-alloc read contract).
- **Propagation across nested decodes**: fail-fast at the first bad length-prefix / nested / bounds (one error, short-circuits up), or collect multiple errors? FlatBuffers collects; Protobuf returns the first. The parse-forward reader (ticket 05) suggests fail-fast.
- **Error detail**: does `DecodeError` carry the byte/bit offset of failure for diagnostics — and if so, is the offset itself a non-allocating value class (ticket 03)?
- **Unknown enum code** (ticket 04): how does the typed result preserve the raw ordinal for recovery (e.g. `UnknownEnumCode(code: Int)` carrying the raw value) vs raising / dropping — without allocating?
- **Read API signature**: do `readBits` etc. return `KompactDecodeResult<T>` directly, or `(value, error)` out-params / a throwing checked variant?

Consequence for 07 (write/builder): the writer never produces these errors — only readers see them on untrusted input — so this model is **read-path only**. The decision here must not regress ticket 03's zero-allocation / zero-copy read contract.
