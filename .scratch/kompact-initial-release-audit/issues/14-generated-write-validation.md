---
Type: grilling
Blocked by: 11
---

## Question

How should generated factories, copies, and mutable setters handle values
outside a field's declared bit width? Decide between fail-fast validation,
typed status/error through the primitive API, or explicitly documented
truncation. Keep behavior consistent across generated and runtime writer
paths, and ensure the chosen failure mechanism does not allocate on the
guaranteed path.
