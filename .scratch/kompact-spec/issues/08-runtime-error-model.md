---
Type: grilling
Status: resolved
Labels:
  - scope:runtime
  - kind:error-model
Blocked by:
  - "06 validation model"
Decides:
  - "09 versioning & schema evolution"
---

# Ticket 08 — Runtime error model (representation)

## Question

Ticket 06 fixed the **typed-result-not-throw** fork and the runtime error *types* (`BoundsError`, `BadLengthPrefix`, `TruncatedNested`, `UnknownEnumCode`). What remained was the **representation** of `KompactDecodeResult` — i.e. how those typed failures are carried on the ticket-03 zero-alloc read path. Informed by 06 + 05 (what readers can hit) + 04 (enum codes).

How is `KompactDecodeResult` — and the `readBits` / `readBitsBoolean` / `readBool` surface from ticket 03 — shaped?

- **Representation**: a flat sealed-class hierarchy vs a value-class over `(ok, value, error)`. Must not allocate on the success / fast-path (ticket 03 zero-alloc read contract).
- **Propagation across nested decodes**: fail-fast at the first bad length-prefix / nested / bounds, or collect multiple errors?
- **Error detail**: does the error carry the byte/bit offset of failure — and if so, is the offset non-allocating (ticket 03)?
- **Unknown enum code** (ticket 04): how is the raw ordinal preserved for recovery without allocating?
- **Read API signature**: do the read functions return a typed result directly, an out-param, or a throwing checked variant?

Consequence for 07 (write/builder): the writer never produces these errors — only readers see them on untrusted input — so this model is **read-path only**. The decision must not regress ticket 03's zero-allocation / zero-copy read contract.

## Answer

User decided: adopt the recommended option on both forks.

**Representation & public read contract — specialized per-type result value classes.**

There is no single generic `KompactDecodeResult<T>`. Each scalar kind has its own result value class — `ByteResult`, `ShortResult`, `IntResult`, `LongResult`, `FloatResult`, `DoubleResult`, `BooleanResult` — declared as `expect value class` in commonMain (**no `@JvmInline`** per the §1 rule) with `@JvmInline actual` on the JVM and a plain `actual` on `iosArm64` / `iosSimulatorArm64` (ticket 03 representation rule). Each wraps a single `Long` that packs: the value bits + an ok-flag + a compact error-code (+ the raw enum code, when the kind is an enum).

On the JVM, `@JvmInline` over a primitive `Long` is zero-alloc on **both** success and failure (the Long is stored inline); on Kotlin/Native, a value class over a primitive `Long` is likewise zero-alloc (inline value). Therefore the public checked accessor `readInt8(): ByteResult` is zero-alloc on the success hot path (satisfies **03**), is a typed result (satisfies **06**), and **never throws**.

- The low-level `readBits(offset, bitWidth): Int` / `readBitsBoolean(offset): Boolean` remain the raw zero-alloc scalar primitives (ticket 03's "direct concrete scalar reads") — used by the generated view accessors and perf-critical inner loops, with the caller responsible for bounds (which ticket 06's validated layout guarantees for in-format reads). They are the primitive *under* the checked accessors, not the public error-safety boundary.
- A checked accessor (`readInt8(): ByteResult`) bounds-checks first; on success it reads via `readBits` and returns `ByteResult.success(value)` (zero-alloc); on a `BoundsError`/`BadLengthPrefix` it returns `ByteResult.failure(error)` (still a packed-Long, zero-alloc). It never throws.
- `BooleanResult` / `ByteResult` / enum results additionally pack the raw code in the Long bits → `UnknownEnumCode(code)` from ticket 04 is preserved without allocation.
- JVM Java interop: a checked `readInt8OrThrow()`-style wrapper is provided (Java callers see the result class); the common / public Kotlin API is the typed result value class.

Rejected:
- A generic `KompactDecodeResult<T>` (sealed class *or* `Result<T>`) over a boxed scalar — allocates on the success path (the JVM boxes the primitive), violating ticket 03's zero-alloc read contract.
- Throwing reads (`throw` on out-of-bounds/malformed) — allocates the exception object and violates ticket 06's "never throw on the read path."

**Propagation + error detail — fail-fast; no byte-offset on the fast path.**

- **Fail-fast**: the checked accessor short-circuits at the first bad length-prefix / nested / bounds / enum code — parse-forward friendly (ticket 05) and matching ticket 06's "only buffer-bounds checks, typed result."
- **Error carried as a compact code** in the result `Long` (zero-alloc) — enough to discriminate `BoundsError` / `BadLengthPrefix` / `TruncatedNested` / `UnknownEnumCode` on the fast path.
- **Byte/bit offset is NOT stored on the fast path.** A 64-bit `Long` cannot also hold the value + ok-flag + a 32-bit offset for 32/64-bit scalar results without allocating, and a uniform result shape keeps the hot path zero-alloc. Full diagnostic detail (byte offset, error kind, raw enum code, offending-field id) is available only on an **explicit opt-in checked diagnostics path** — e.g. `decodeFull(): DetailedResult<T>` carrying a `DecodeError(value, offset, kind, rawCode)` allocated only on the rare failure path — and on the generated view's `at(offset)` debug accessor. So 03's zero-alloc guarantee is preserved for the common scalar read, and rich diagnostics remain available when needed.

Rejected: "collect multiple errors + always carry a full offset on the common result" — requires a boxed/sealed result representation and allocates on the failure path, violating ticket 03's zero-alloc read contract.

**Tradeoff accepted.** Eight small specialized result value classes (vs one generic `Result<T>`) is the price of stacking three obligations on the same read path: zero-alloc (03) **and** typed-result (06) **and** never-throws. On the failure path, the compact-code result trades a stored byte offset for zero-allocation; the offset is recoverable from the opt-in diagnostics path. This is the deliberate, documented cost of a zero-alloc, never-throwing, typed read API.

**Consequences.**
- 09 versioning & schema evolution: now the only remaining read-side concern — a length-prefix / bounds violation surfaces as a fail-fast typed `BadLengthPrefix` / `BoundsError` (06 + 08), never a silent misread, so evolution can trust the framing's length integrity. Informed by 03+04+05+06+07+08.
- 07 write/builder: confirmed read-path-only — the writer never produces these errors. Already resolved.
- 05 framing: `readBits` is the parse-forward cursor; the checked accessors layer the zero-alloc typed results over it.

## References
- ticket 03 (zero-alloc `readBits` over a caller-owned `ByteArray`; value-class representation) 
- ticket 04 (enum code width; `UnknownEnumCode` raw code) 
- ticket 05 (parse-forward sequential framing) 
- ticket 06 (runtime error types; typed-result-not-throw; fail-fast bounds) 
- ticket 07 (writer selects length-prefix widths at codegen) 
