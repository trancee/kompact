---
Type: grilling
Status: resolved
---

## Question

What KGP/KSP version compatibility and failure-diagnostic contract should the
reflection-based Gradle integration promise? Decide the supported version
range, upgrade gate, and expected observable failure if reflected properties
or KSP2's internal `KSPLoader` change. The existing TestKit suite covers a
pinned happy path but not incompatible getters/entry points; prefer a tested
compatibility boundary over an unbounded promise.

## Answer

Support only the exact Kotlin Gradle Plugin/KSP version pair exercised by the
repository's consumer integration tests. A later version pair is unsupported
until it is added to and passes that test matrix.

Validate reflective KGP extension/source-set/compiler-option structure during
plugin configuration. Validate KSP2's internal `KSPLoader` immediately before
the generation task invokes it. At each earliest safe boundary, fail with an
actionable diagnostic naming the detected Kotlin/KSP versions, the broken
integration seam, and the supported tested pair; preserve the underlying
cause for Gradle `--stacktrace`. Do not invoke KSP during configuration, and
preserve configuration-cache and task-avoidance behavior.
