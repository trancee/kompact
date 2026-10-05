---
Type: grilling
Status: resolved
Blocked by: 05
---

## Question

For the initial supported wire contract, should Kompact add a version header,
should each application own versioning in its envelope, or should Kompact
explicitly defer versioning? Resolve the conflict between the old locked spec
map and ADR-0002 using the evidence and scenarios in
[Reconciling schema-versioning evidence](05-schema-evolution-evidence.md), and
record the chosen contract, compatibility behavior, and migration implications.

## Answer

Kompact's initial supported frame format remains versionless. Applications
that need mixed-version communication must define an outer envelope, version
identifiers, decoder selection/rejection, and migration policy. Kompact does
not guarantee compatibility between differing schema versions and will not
add a version header or runtime envelope helper as part of this decision.

Add a concise documentation example of a caller-owned versioned envelope to
make the boundary actionable without standardizing application-specific wire
IDs or migration behavior. This reaffirms accepted
[`ADR-0002`](../../../docs/adr/0002-defer-versioning-surface-to-v2.md);
the old `.scratch/kompact-spec` version-prefix decision remains superseded.
