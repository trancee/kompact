---
Type: research
Status: needs-triage
Labels:
  - wayfinder:research
  - scope:build
Blocked by:
  - none
Decides:
  - "remote-branch classification (v1-WIP vs experiment)"
---

## Question

What is the state of the remote branches, and which (if any) are the Kompact v1.0
implementation in progress to **align this map to** vs. ignore as unrelated
experiments?

Branches to inspect:
- `origin/feat/kompact-v1`
- `origin/prototype/c99-interface`
- `origin/prototype/kotlin-interface`
- `origin/feat/minimax`
- `origin/feat/laguna`

## Acceptance

- For each branch: HEAD commit + last activity, how far ahead/behind `main`,
  and a one-line classification (v1-WIP / experiment / stale-abandoned).
- Recommendation: which to align the v1 scaffold to (if any), which are irrelevant
  to v1.
- Findings graduate the "remote-branch states" fog off the map; if `feat/kompact-v1`
  is active v1 WIP, link its contents (module layout, ABI golden state) into
  ticket 05 (scaffold).
