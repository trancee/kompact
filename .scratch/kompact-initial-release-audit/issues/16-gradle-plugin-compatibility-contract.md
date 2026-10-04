---
Type: grilling
---

## Question

What KGP/KSP version compatibility and failure-diagnostic contract should the
reflection-based Gradle integration promise? Decide the supported version
range, upgrade gate, and expected observable failure if reflected properties
or KSP2's internal `KSPLoader` change. The existing TestKit suite covers a
pinned happy path but not incompatible getters/entry points; prefer a tested
compatibility boundary over an unbounded promise.
