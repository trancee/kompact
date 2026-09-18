---
Type: task
Status: needs-triage
Labels:
  - wayfinder:task
  - scope:build
Blocked by:
  - "01 ratify fail-path zero-alloc (ADR-0005 vs ticket 08)"
  - "02 ratify immutable-by-default models (ADR-0006 vs ADR-0001)"
  - "03 arbitrate framing prefix widths (vs ticket 05)"
Decides:
  - "v1.0 baseline module layout + ABI golden locked in main"
---

## Question

**Reframed by [research 04](../research/04-remote-branch-inspection.md):** the
v1.0 implementation already exists in `main` (`:kompact` + `:kompact-ksp`,
committed ABI goldens, CI gates, released as 0.1.0–0.1.7). So this is **not**
greenfield scaffolding — it is **verifying and locking the existing v1.0
baseline** so the locked spec ships against a stable, checked-in ABI.

Once the three blocking ratifications are decided, confirm the v1.0 baseline in
`main` is locked and green:

## Acceptance

- Module layout present and matches kompact-spec
  [ticket 12](../../kompact-spec/issues/12-module-split-and-publication.md):
  `:kompact` (KMP runtime, JVM + `iosArm64` + `iosSimulatorArm64`) + `:kompact-ksp`
  (JVM-only processor).
- `binary-compatibility-validator` goldens are **committed** for v1.0 and
  **unchanged green** on `main`: `./gradlew :kompact:checkKotlinAbi
  :kompact-ksp:checkKotlinAbi` exits 0.
- v1 merge gates green on the baseline: `koverVerify*` 100%,
  `:kompact-ksp:test` (zero-alloc assertions, tickets 10/11), CI gate task.
- **Alignment:** verify against `main` (where `feat/laguna` was integrated and
  released) — **not** against stale branches `feat/kompact-v1` /
  `feat/minimax` / `prototype/*`.

## Notes

- **Blocked** by [01](01-ratify-fail-path-zero-alloc.md),
  [02](02-ratify-immutable-default-models.md),
  [03](03-arbitrate-framing-prefix-widths.md) — the ABI golden is meaningless
  until the read-API result shape, view mutability, and framing format are
  finalized. Do **not** claim this until those are resolved.
- If the ratifications change the shape (e.g. ADR-0005 collapses the result
  types), the ABI golden may need an intentional MAJOR bump — a ticket-05
  sub-decision, not a chart decision.
