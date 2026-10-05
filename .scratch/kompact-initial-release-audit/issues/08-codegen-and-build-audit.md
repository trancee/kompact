---
Type: research
Status: resolved
---

## Question

Which high-confidence architecture or delivery decisions are needed in
Kompact's KSP generation, Gradle plugin, and KMP platform integration before
the first supported release?

Audit parser/validation/generation boundaries, diagnostics and generated
contracts, KSP rounds, reflection or unstable plugin integration, cache and
incremental behavior, supported targets, and consumer setup. Compare source
and tests with accepted ADRs and the Constitution. Return evidence-linked
findings and concrete decision questions, not implementation advice without
demonstrated risk and not code changes.

## Answer

The parser/generator's round-safety and deterministic schema-error behavior
are strengths with focused tests. Decisions surfaced: minimum compatibility
and diagnostics behavior for the reflection-based KGP/KSP integration;
whether unsupported-target rejection is a stable tested contract; and whether
null-containing-file fallback and zero-field/unannotated-property diagnostics
are intentional. Gradle cache tests cover relocation, deletion, reuse, and
supported target routing; partial edits and reflection failure paths remain
unverified. See
[`../research/codegen-build-audit.md`](../research/codegen-build-audit.md).
