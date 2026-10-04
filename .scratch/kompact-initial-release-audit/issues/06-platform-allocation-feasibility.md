---
Type: research
Status: resolved
---

## Question

Can Kompact prove zero per-operation allocations for all reads, writes, and
encoding after setup, on every current target, and what API/measuring approach
would make that claim trustworthy?

Inspect allocation-producing and ownership-sensitive paths, including
framing, nested/repeated data, strings, caller buffers, result/view creation,
and output snapshots. Research official/current Kotlin/JVM/Native measurement
capabilities for the repository's toolchain. Report feasible boundaries,
target limitations, candidate primitives/reusable state, and repeatable
validation options. Do not implement code or silently narrow the user-approved
contract; identify blockers and exact choices it forces.

## Answer

The current API cannot meet the contract. `KompactWriter` owns and allocates
its initial buffer; string APIs encode/decode `String` objects; lambda nested
writes create child writers and snapshots; frame, slice, repeated-view, and
`LongResult` APIs create wrapper objects; variable-width repeats allocate an
index. The caller-buffer/no-wrapper path therefore requires a distinct
primitive/reusable API, workspace supplied for variable-width repeats, and
byte-oriented string handling. String object construction is explicitly
excluded; convenience wrappers and owned copies/snapshots may allocate outside
the guarantee.

JVM/Android JVM and iOS have candidate measurement tools, but Android Native
arm64 lacks a proven per-operation allocation method. The user requires a
separate validation spike before making an allocation claim on that target.
Detailed source evidence and tool limitations are recorded in
[`../research/allocation-feasibility.md`](../research/allocation-feasibility.md).
