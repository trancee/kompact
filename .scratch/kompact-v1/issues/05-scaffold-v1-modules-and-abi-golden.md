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
  - "v1 module/target scaffolding + v1.0 ABI golden"
---

## Question

Once the three blocking ratifications are decided, scaffold the v1.0 module and
target structure and generate the v1.0 ABI golden — so the locked spec can be
implemented against a stable, checked-in ABI.

## Acceptance

- Modules configured per kompact-spec [ticket 12](../../kompact-spec/issues/12-module-split-and-publication.md):
  `:kompact` (KMP runtime, JVM + `iosArm64` + `iosSimulatorArm64`) + `:kompact-ksp`
  (JVM-only processor).
- `binary-compatibility-validator` `api/` golden committed for v1.0
  (`checkKotlinAbi` / `apiCheck` green) across JVM + klibs.
- The scaffold matches whichever side of 01/02/03 the team ratified (result-type
  API + view mutability + framing format), so the golden is final for v1.

## Notes

- This ticket is **blocked** by 01, 02, and 03 — do **not** claim until those are
  resolved. The ABI golden is meaningless until the read-API / view-shape /
  framing-format decisions are locked.
