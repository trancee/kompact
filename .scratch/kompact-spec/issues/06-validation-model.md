---
Type: grilling
Status: open
Labels: wayfinder:grilling
Blocked by: 02-generation-strategy (resolved), 04-v1-type-set (resolved), 05-variable-length-framing (resolved)
---

## Question

Now that generation (KSP), the type set, and framing are decided, where does field-layout validation live, and what does `@KompactField` actually validate?

1. **Compile-time vs runtime**: does the annotation processor validate layouts — bit-offset overlaps, per-struct width-sum, length-prefix bounds, nested sub-region consistency, repeated-count sanity — at compile time? Or is validation a runtime check in `KompactRuntime`?
2. **What is validated**: which invariants are checked (offset overlap, width-sum ≤ struct bit-length, length-prefix ≤ remaining buffer, nested total-length consistency, enum code within the declared width)?
3. **Failure mode**: compile-time violations are hard errors that halt processing with symbol-located diagnostics (matching the diagnostics discipline from the generation research); runtime validation yields a typed result per the error-model ticket.

This gates the processor's validation pass, the runtime error contract, and the conformance test surface. Resolve before the error-model ticket.
