---
Type: research
Status: resolved
---

## Question

What schema-evolution and versioning needs should Kompact's initial supported
wire contract satisfy?

Review the conflict between `.scratch/kompact-spec/map.md`, which records a
top-level schema-version prefix as decided, and accepted
`docs/adr/0002-defer-versioning-surface-to-v2.md`, which defers it. Compare
both with the current wire implementation and docs. Identify concrete
consumer/evolution scenarios, compatibility and migration consequences, and
the minimum viable options (Kompact-owned header, caller-owned envelope, or
explicit deferral). Do not choose on the user's behalf; provide evidence,
tradeoffs, and sources sufficient for the dependent decision ticket.

## Answer

The old scratch map is historical; accepted ADR-0002 is the current decision.
The wire format is versionless, and the architecture guide assigns mixed
version handling to applications. Current framed schemas are positional and
strict, and the old map's uniform-prefix/additive-skip behavior is not an
implemented contract. The remaining decision is whether the initial supported
release keeps that model, adds a Kompact-owned version header with migration
behavior, or explicitly defers versioning. Evidence and details:
[schema evolution research](../research/schema-evolution-evidence.md).
