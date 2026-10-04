---
Type: grilling
Status: resolved
Blocked by: 06
---

## Question

Design the guaranteed byte-oriented API for caller-preallocated buffers:
primitive/caller-reused state instead of fresh wrappers; reusable cursor and
failure reporting; direct nested/slice operations; and repeat indexing backed
by caller-owned workspace with explicit insufficient-capacity behavior.
Separate that contract from allocating convenience APIs and owned snapshots.
UTF-8 `String` object creation is out of scope, but byte-oriented payload
operations remain in scope. The API must meet the guarantee for the full
current target matrix; Android Native arm64 cannot be claimed until its
validation spike succeeds. Use the feasibility evidence in
[Assessing allocation-contract feasibility](06-platform-allocation-feasibility.md)
and do not silently narrow the approved contract.

## Answer

The guaranteed byte-oriented API uses a caller-created, resettable mutable
cursor bound to a caller-owned buffer and bit region. Reset validates
`0 <= startBit <= position <= endBit <= buffer.size * 8`. Each cursor stores
primitive `valueBits`, documented integer status code, error bit offset, and
primitive error detail. Separate cursors are caller-supplied for concurrently
active or nested regions; cursors are not thread-safe.

Checked and generated writes reject values outside the declared signed or
unsigned width through the primitive status channel, without allocating or
mutating on failure. Clearly separate unchecked raw bit operations may retain
low-bit truncation. Each checked primitive is failure-atomic. Generated
`decodeInto` and `encodeFrom` operations preflight the complete bounded input
or model and required capacity, then commit: on failure, bytes, holder fields,
and cursor position remain unchanged. Callers must keep borrowed input/model
stable for the duration of an operation.

Codegen provides caller-created reusable mutable model holders plus
`decodeInto(cursor, holder)` / `encodeFrom(cursor, holder)` paths. Framed blob
and string payloads are represented as borrowed bounded byte ranges; nested
holders and repeat workspaces are caller-owned. The byte-oriented string path
validates UTF-8 without creating `String` objects; conversion to `String`,
convenience views, and explicit owned copies/snapshots remain separate
allocating APIs.

Variable-width repeated fields use a caller-supplied reusable sparse-index
workspace, report insufficient capacity explicitly, and decode one element at
a time into a reusable element holder. The borrowed source bytes must remain
stable while the index is used. The guaranteed nested writer accepts a known
payload length/bounded byte region and does not use a child writer or callback.

Keep all five current targets. The no-allocation claim for Android Native
arm64 is gated on a successful measurement validation spike; no claim should
precede that evidence. The measured guarantee covers the byte-oriented
low-allocation paths, not string-object conversion or allocating convenience
APIs.

This requires a substantial runtime/codegen redesign. It also unblocks
[generated write validation](14-generated-write-validation.md) and
[nested-region API surface](15-nested-region-api-surface.md).
