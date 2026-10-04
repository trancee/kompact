---
Type: grilling
Status: resolved
---

## Question

Which schema declaration cases should produce a warning, an error, or be
silently ignored? Settle zero-field models, unannotated properties inside
annotated models, and unresolved/missing source-file fallback diagnostics.
Keep the policy consistent with fail-closed schema validation and pin it with
symbol-located processor tests.

## Answer

An explicitly `@KompactModel`-annotated declaration with zero recognized wire
fields is a compile-time error; no empty model is generated. Properties
without `@KompactField` are intentionally excluded from the wire schema and
this behavior is documented.

Defer only when a KSP symbol or type is genuinely unresolved and may resolve
in a later round. A resolved but invalid or unsupported annotated field is a
symbol-located error; the processor never silently drops it or emits
success-shaped partial output.

Any invalid field prevents every generated output for that model, while
independent valid models continue processing. Report all independent
diagnostics in the invalid model when safe. Add processor tests for each
decision, including per-model output suppression and continued generation of
valid peer models.
