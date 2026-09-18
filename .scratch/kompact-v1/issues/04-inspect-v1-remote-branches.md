---
Type: research
Status: resolved
Labels:
  - wayfinder:research
  - scope:build
Blocked by:
  - none
Decides:
  - "remote-branch classification (v1-WIP vs experiment vs stale-abandoned)"
Resolved by:
  - "research/04-remote-branch-inspection.md"
---

## Question

What is the state of the remote branches, and which (if any) are the Kompact v1.0
implementation in progress to **align this map to** vs. ignore as unrelated
experiments?

Branches inspected:
- `origin/feat/kompact-v1`
- `origin/prototype/c99-interface`
- `origin/prototype/kotlin-interface`
- `origin/feat/minimax`
- `origin/feat/laguna`

## Resolution

Findings are in [`../research/04-remote-branch-inspection.md`](../research/04-remote-branch-inspection.md)
(read-only; no remote mutation, no fetch required). TL;DR:

- All five branches are stale (last commit Sep 13; `main` is Sep 17).
- `feat/laguna` is the v1 implementation — but it has **already been
  squash-merged into `main`** (commit `2571014`) and released as v0.1.0–v0.1.7;
  `main` is now at `0.2.0-SNAPSHOT`.
- `feat/kompact-v1` is a superseded earlier attempt (not in `main`).
- `feat/minimax` and both `prototype/*` are planning/docs experiments (not in `main`).

**Recommendation:** align the v1 scaffold to **`main`** (where laguna's v1 is
already released, ABI-locked, and CI-gated). `feat/laguna` is the development
record only.

**Fog graduated + ticket 05 reframed.** Because the v1 implementation already
lives in `main`, ticket 05 (scaffold) is **not** greenfield scaffolding — it is
verifying/locking the *existing* v1.0 baseline (module layout + committed ABI
goldens + green gates). The "remote-branch states" fog item is graduated off the
map; "v1.0 version / release date" remains in fog.
