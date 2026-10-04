---
Type: research
Status: resolved
---

## Question

What must change in Kompact's tests, CI, documentation, security checks, and
release/publication flow to establish a credible first supported release?

Audit the repository's maintained tests and quality gates against the
Constitution, user-approved platform/allocation requirements, docs, workflows,
dependency/tooling declarations, and published-but-unused `0.6.1` context.
Identify missing proof, contradictory guidance, reproducibility or supply-chain
risks, API/release metadata gaps, and concrete decision questions. Return
evidence-linked findings; do not implement changes or treat coverage percentages
alone as behavior proof.

## Answer

The audit found published-version documentation drift (`0.7.0` published,
repository at `0.8.0-SNAPSHOT`, while docs describe `0.6.1`), no allocation
measurements/gate, no Native test tasks in CI, no runtime Portal bundle dry-run,
asymmetric module ABI/coverage gates, release automation that appears to
conflict with Constitution G1, and external required-status-check configuration
that source cannot prove. See
[`../research/quality-docs-release-audit.md`](../research/quality-docs-release-audit.md).
