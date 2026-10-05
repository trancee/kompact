---
Type: task
Blocked by: 22
---

## Objective

Make generated reads and writes honor the agreed checked/unchecked boundary,
remove the superseded nested-region API, and pin generated-view equality
semantics.

## Decisions

- Follow [generated write validation](14-generated-write-validation.md),
  [the nested-region API decision](15-nested-region-api-surface.md), and
  [the equality contract](13-generated-view-equality.md).
- Checked/generated writes reject values outside field ranges and unknown
  enum codes without mutation. Allocating `create`/`copy` conveniences throw
  documented validation errors. Only explicitly unchecked/raw APIs may
  truncate or accept raw codes.
- Remove `NestedRegion`, `NestedRegionResult`, and their unique APIs, extensions,
  tests, and coverage pins. Retain `KompactFrame` and slice conveniences outside
  the allocation guarantee.
- Preserve identity-based equality/hash behavior; views are not content keys
  and must not print field values.

## Acceptance criteria

- Add red-first tests for lower/upper range boundaries, invalid values,
  unknown enum codes, no-mutation-on-failure, and explicit unchecked
  truncation/raw behavior.
- Remove the legacy nested-region declarations and all consumers; use the
  bounded cursor path from task 22 for no-allocation nested reads.
- Add tests and public documentation for fixed-layout and framed identity
  equality/hash behavior; do not add content-equality helpers or field-value
  `toString()` implementations.
- Update all affected callers, tests, coverage configuration, API docs, and
  examples in the same change set.
- Run focused tests, all affected module tests, formatter/static analysis, and
  ABI/API checks applicable to the changed public surface.
