---
Type: grilling
Status: resolved
Labels:
  - scope:runtime
  - scope:codegen
  - kind:validation
  - kind:error-model
Blocked by:
  - "02 generation strategy"
  - "04 v1 type set"
  - "05 sequential framing"
Decides:
  - "07 write/builder interface"
  - "08 runtime error model"
---

# Ticket 06 — Validation model

## Question

Where does field-layout validation live in Kompact, and what fails (and how) when the schema or the wire input is malformed? Informed by tickets 02 (generation), 03 (zero-alloc value-class reads), 04 (type set), and 05 (framing).

Three sub-questions:
1. **Split:** compile-time (KSP) structural checks + runtime bounds checks? or one or the other?
2. **Compile-time failure mode:** what happens when `KompactProcessor` sees a violating schema?
3. **Runtime hot-path failure mode:** what happens when `KompactRuntime` reads a short / out-of-bounds buffer?

## Answer

User decided: adopt the recommended option on all three forks.

**1. Split — compile-time (KSP) structural + runtime typed-result bounds.**
- **`KompactProcessor` (compile-time)** validates structural/layout invariants during the KSP symbol-processing pass, before any value-class is generated. It builds an in-memory layout model of every `@Kompact`-annotated struct to compute bit offsets and then checks invariants (matrix below). These can never be checked at runtime, because they describe the *schema*, not the *buffer*.
- **`KompactRuntime` (runtime)** performs *only defensive buffer-bounds checks* on the read path — the invariants that are genuinely unknowable at compile time because they depend on the concrete `ByteArray` contents.

This is the FlatBuffers model (validate the layout at build) paired with the Protobuf model (return a `Result`/typed error at decode). It is the only split consistent with every prior decision: KSP generation (02) makes compile-time validation possible; the zero-alloc value-class read contract (03) forbids throwing on the hot path; the type set (04) and framing (05) define exactly which invariants are structural versus buffer-bound.

**Invariant matrix**

| Invariant | Checked | Where | Error type (if reached) |
|---|---|---|---|
| Bit-offset overlaps within a struct | compile-time | `KompactProcessor` layout pass | hard error (build fails) |
| Per-struct bit-width sum ≤ declared width | compile-time | `KompactProcessor` layout pass | hard error |
| Length-prefix field width ∈ {8,16,32} | compile-time | `KompactProcessor` | hard error |
| Nested total-length ≤ declared length field capacity | compile-time | `KompactProcessor` layout pass (static bound) | hard error |
| Repeated element layout uniformity + count width ∈ {8,16,32} | compile-time | `KompactProcessor` layout pass | hard error |
| Enum width ≥ ordinal bit-width; declared codes fit | compile-time | `KompactProcessor` | hard error |
| Short buffer / read past end | runtime | `KompactRuntime` | `BoundsError` |
| Length-prefix > remaining bytes in region | runtime | `KompactRuntime` | `BadLengthPrefix` |
| Nested declared length < actual nested payload | runtime | `KompactRuntime` | `TruncatedNested` |
| Enum wire code outside known ordinals | runtime | `KompactRuntime` | `UnknownEnumCode` |

**2. Compile-time failure mode — hard error, symbol-located, halt processing.**
Violations are reported via `KSPLogger.error(message, element)` attached to the offending `@KompactField`-annotated property (element = the KSP `KSDeclaration`/`KSPropertyDeclaration`), so the diagnostic points at the *declaration*, not an opaque offset. The processor returns a sentinel result from its round and halts generation for the offending symbol — the Gradle build fails until the schema is fixed. No warnings-as-proceed, because a "proceed" codegen would silently emit a structurally invalid reader the compiler could not otherwise catch. This matches ticket 02's KSP-diagnostic discipline (deterministic, symbol-located).

**3. Runtime hot-path failure mode — typed result, never throw.**
`readBits` / `readBitsBoolean` return `KompactDecodeResult<T>` — a value class over `(success: Boolean, value: T?, error: KompactDecodeError?)`, carrying either `success(value)` or `failure(error)`. The direct concrete read path (the one the 03 contract protects as zero-allocation) **never throws**: an out-of-bounds read is a value, not an exception. Throws allocate (stack trace capture) and would violate the zero-alloc read contract established in ticket 03. Optional checked wrappers (`readBitsOrThrow`) are provided for callers who prefer exceptions, but the direct view-class read API does not.

**Tradeoff accepted.** Compile-time validation shifts all structural error detection to build time (better DX, fail-fast on the device developer) at the cost of `KompactProcessor` complexity — a dedicated `LayoutModel` validation pass that fully models the bit layout before emission. Runtime keeps only the buffer-bounds checks that are genuinely impossible to compute at compile time, and carries them as typed results (satisfying the 03 zero-allocation read contract).

**Consequences.**
- 08 runtime error model: ticket 06 resolves the headline fork — runtime failures are **typed results, not throws** — and fixes the set of runtime error types above. The *representation* of `KompactDecodeResult` and its composition (propagation, wrapping, error-detail fields) remain fog for ticket 08, informed by 06.
- 07 write/builder interface: now constrained — the writer's output must be structurally valid per the compile-time rules, so the writer *cannot produce* an overlapping-offset or width-overflow stream; the reader only bounds-checks. This removes whole classes of write-side bugs.
- 09 versioning & schema evolution: a length-prefix that exceeds remaining bytes now yields a typed `BadLengthPrefix` rather than a silent misread (06), so forward-compat on a skewed stream is safe-by-construction.
- `KompactProcessor` MUST implement a `LayoutModel` validation pass that computes every field's `[bitOffset, bitWidth)` and checks invariants 1–5 before emitting any `expect/actual value class`. (No partial emission on a malformed schema.)

## References
- ticket 02 (KSP diagnostics discipline: deterministic, symbol-located) 
- ticket 03 (zero-alloc value-class read contract) 
- ticket 04 (type set: enum width, length-prefix widths) 
- ticket 05 (framing: length-prefix, nested total-length, count-prefixed repeats) 
