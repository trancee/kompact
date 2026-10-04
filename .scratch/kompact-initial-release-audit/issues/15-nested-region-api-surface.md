---
Type: grilling
Status: resolved
Blocked by: 11
---

## Question

Should the pre-ADR-0008 `KompactFraming.readNested` /
`NestedRegionResult` / `Pair` convenience surface be removed before first
supported release, retained as a distinct primitive API, or preserved only as
an allocating compatibility convenience? Compare it with the active
`KompactFrame`/slice path, codegen call sites, and the new low-allocation
contract. Specify the intended ownership and failure behavior.

## Answer

Remove the legacy nested-region-only surface from the next unreleased
artifact, without a deprecation period: `NestedRegion`, `NestedRegionResult`,
`KompactFraming.readNested`/`readNestedOrThrow`, and the `getOrElse`/`map`
overloads specific to `NestedRegionResult`. Keep the generic result extensions
for the remaining result types. Remove their dedicated tests and coverage
pins, and regenerate API documentation using Dokka rather than editing
generated pages.

Keep `KompactFrame.readNested` and `KompactByteSlice` as allocating convenience
APIs outside the no-allocation guarantee. The new cursor path owns the
allocation-free nested-region contract. Already-published artifacts are
immutable; no production consumers were adopted, so the next source artifact
does not need a deprecation bridge. This removes duplicate semantics without
changing the wire format.
