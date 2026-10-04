---
Type: grilling
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
