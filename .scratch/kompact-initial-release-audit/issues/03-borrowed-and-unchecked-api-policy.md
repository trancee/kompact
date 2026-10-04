---
Type: grilling
Status: resolved
---

## Question

What default should the consumer API use for ownership and unchecked
operations?

## Answer

Borrowed and lazy views remain the default to support the allocation
requirement. Provide an explicit copy-to-own path and make borrowed lifetime
and mutation behavior clear. Retain unchecked low-level primitives only as a
clearly separated, explicitly unchecked surface; checked APIs remain the
consumer-facing default.
