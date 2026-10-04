---
Type: grilling
Status: resolved
Blocked by: 11
---

## Question

How should generated factories, copies, and mutable setters handle values
outside a field's declared bit width? Decide between fail-fast validation,
typed status/error through the primitive API, or explicitly documented
truncation. Keep behavior consistent across generated and runtime writer
paths, and ensure the chosen failure mechanism does not allocate on the
guaranteed path.

## Answer

Checked cursor operations and generated `encodeFrom` reject values outside
the declared signed or unsigned field width before mutation. They also reject
enum codes not declared by the schema, with a distinct primitive status/detail
for `UnknownEnumCode`. On failure, output bytes and cursor position remain
unchanged.

Intentionally writing low bits or an undeclared enum wire code is possible
only through a clearly explicit unchecked/raw API, if that API permits it.
Allocating generated `create`/`copy` convenience methods throw a documented
validation exception for invalid values; they never silently truncate or
return a success-shaped invalid model.

This decision refines
[Designing the guaranteed byte-oriented API](11-low-allocation-api-shape.md)
and preserves the separation between checked schema writes and unchecked
bit-level encoding.
