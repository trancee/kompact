---
Type: grilling
Status: resolved
---

## Question

What should the initial-release audit cover, and should existing scratch
decisions be treated as binding?

## Answer

The audit covers every maintained product and delivery surface in this
repository, including runtime/wire behavior, public APIs, KSP and Gradle
integration, platform support/publication, tests, documentation, CI/build,
release, and security boundaries. It audits afresh: the Constitution, current
source, and accepted ADRs are operative evidence; older scratch maps are
historical inputs and may be challenged or superseded when inconsistent.

Optimize for correctness, safe and clear consumer APIs, cross-platform
consistency, and measured performance, with zero allocation as a hard product
requirement. The user states that Maven Central `0.6.1` has not been adopted or
used in production, so compatibility may be redesigned before supported
release; previously published coordinates or versions must not be assumed
rewritable.
