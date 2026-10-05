---
Type: grilling
Status: resolved
---

## Question

What equality and hashing semantics should generated fixed-layout value
classes and framed views expose? Decide whether they compare schema field
values, bounded wire bytes, or backing-array identity, and whether `toString`
should follow the same model. The current fixed views wrap `ByteArray`, while
framed views do not generate value equality; document and test the chosen
contract consistently.

## Answer

Generated models make no schema-value/content-equality promise and add no
content-comparison helper. Preserve Kotlin's existing identity mechanics:
fixed-layout value classes compare/hash their wrapped `ByteArray` reference;
framed regular view classes compare/hash by view-object identity. Mutating
borrowed bytes changes the observed data but not the identity hash.

Document that consumers must not use these borrowed views as content keys;
they should compare/copy declared values or bytes explicitly at the
application boundary. Add tests pinning the identity behavior for fixed and
framed generated models.

Do not generate field-value `toString()` or add an opt-in formatter in this
scope. Retain default formatting to avoid implicit logging of potentially
sensitive payloads and avoid unneeded API surface. This preserves current
runtime behavior while keeping content equality and formatting outside the
no-allocation contract.
