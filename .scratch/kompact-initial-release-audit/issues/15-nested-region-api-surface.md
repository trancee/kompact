---
Type: grilling
Blocked by: 11
---

## Question

Should the pre-ADR-0008 `KompactFraming.readNested` /
`NestedRegionResult` / `Pair` convenience surface be removed before first
supported release, retained as a distinct primitive API, or preserved only as
an allocating compatibility convenience? Compare it with the active
`KompactFrame`/slice path, codegen call sites, and the new low-allocation
contract. Specify the intended ownership and failure behavior.
