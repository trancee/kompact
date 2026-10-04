---
Type: grilling
Status: resolved
---

## Question

What does the hard zero-allocation requirement cover?

## Answer

After caller setup, byte-oriented library reads, writes, and encoding
operations must perform zero per-operation allocations using preallocated
caller-owned buffers. This includes fixed/framed, nested, and repeated
operations. The guaranteed path must not create fresh result/view wrappers;
return primitive positions/status or populate caller-reused state. UTF-8
`String` creation/consumption, convenience wrappers, buffer setup, and
explicitly requested owned copies or snapshots may allocate, but remain
outside the guarantee and must not be described as zero-allocation.
