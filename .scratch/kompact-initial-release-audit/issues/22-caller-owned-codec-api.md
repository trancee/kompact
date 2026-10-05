---
Type: task
---

## Objective

Implement the checked byte-oriented caller-buffer API that satisfies the
resolved steady-state allocation contract and supports generated reusable
holders.

## Decisions

- Follow [the allocation contract](02-steady-state-zero-allocation-contract.md)
  and [the agreed API shape](11-low-allocation-api-shape.md).
- Use bounded reusable cursors with primitive status/error detail, caller-owned
  buffers, generated reusable mutable holders, and caller-owned workspace for
  variable-width repeat indexes.
- Checked operations are failure-atomic. Nested writes use known-length bounded
  regions. UTF-8 `String` object conversion and explicit owned snapshots/copies
  are outside the allocation guarantee.
- Probe-taking generated framed-holder operations, including scalar-only
  schemas, require distinct cursors and preflight capacity and scalar value
  constraints before mutating the destination or holder.

## Acceptance criteria

- Add behavioral tests first for cursor bounds, successful encode/decode,
  malformed/truncated input, failure atomicity, nested bounded regions, and
  repeat-workspace capacity boundaries.
- Implement the smallest complete runtime and generated API needed for
  caller-owned-buffer operations; do not allocate result wrappers on that
  path.
- Generated reusable holders support `decodeInto`/`encodeFrom` without hidden
  per-operation wrappers or internal buffers.
- Caller-owned workspace is reused for variable-width repeat indexes; its
  capacity/error behavior is explicit and tested.
- Preserve existing allocating conveniences outside the guaranteed path and
  label their copy/ownership semantics clearly.
- Run the targeted tests, full applicable test suite, formatter/static
  analysis, and allocation checks specified in
  [the platform proof task](25-platform-proof.md).
