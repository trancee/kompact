---
Type: research
Status: needs-triage
Labels:
  - wayfinder:research
  - scope:build
Blocked by:
  - none
Decides:
  - "remote-branch classification (v1-WIP vs experiment vs stale-abandoned)"
Source of truth:
  - "remote `origin` (https://github.com/trancee/kompact.git)"
  - "local remote-tracking refs refs/remotes/origin/* (already present — no fetch required)"
---

# Remote Branch Inspection — Kompact v1.0 Wayfinder

> **Scope:** `feat/kompact-v1`, `prototype/c99-interface`, `prototype/kotlin-interface`, `feat/minimax`, `feat/laguna`
> **Reference base:** `origin/main` (5a78cc4d0f1cd199388205305ffe6b7ff755f2e4 — `chore(release): bump to next SNAPSHOT 0.2.0-SNAPSHOT`, 2026-09-17)

## Methodology

All data gathered read-only from pre-existing `origin/*` remote-tracking refs
(no objects were missing locally; **no `git fetch` was required**). For each
branch the following commands were run:

- `git ls-remote --heads https://github.com/trancee/kompact.git` — confirms branch existence on the remote
- `git log -1` on `origin/<branch>` — HEAD commit SHA, short date, subject
- `git rev-list --count origin/main..origin/<branch>` — commits **ahead** of main (main..branch)
- `git rev-list --count origin/<branch>..origin/main` — commits **behind** main (branch..main)
- `git merge-base origin/<branch> origin/main` — fork point with main
- `git merge-base --is-ancestor` — ancestry checks to determine integration status

**No push, no PR, no remote mutation.** No throwaway fetch refs were created because
all five remote-tracking refs were already present with full object history locally.

## Summary Table

| Branch | HEAD SHA | Date | HEAD Subject | Merge-base w/ main | Ahead (main..branch) | Behind (branch..main) | Classification |
|---|---|---|---|---|---|---|---|
| `feat/kompact-v1` | `dca9689b23f75bbba8f067c16190d41d90851e39` | 2026-08-31 | `docs: restructure user documentation` | `4d02cc48` (PR #22, 2026-08-30) | 9 | 64 | **stale-abandoned** |
| `prototype/c99-interface` | `2a065eefaec62718e1f1888d9a65615f1293af89` | 2026-08-30 | `docs: prototype C99 interface options` | `b7c1e39b` (PR #16, 2026-08-30) | 1 | 77 | **experiment** |
| `prototype/kotlin-interface` | `a1b8104800a13a6aacbf8769054d81d7f3d14803` | 2026-08-30 | `docs: prototype Kotlin interface options` | `8d929236` (PR #15, 2026-08-30) | 1 | 79 | **experiment** |
| `feat/minimax` | `0bde165049d12eb60a79725c89ffe2793c887edf` | 2026-09-03 | `docs: remove implementation-ticket refs, switch diagrams to ASCII` | `4d02cc48` (PR #22, 2026-08-30) | 22 | 64 | **experiment** |
| `feat/laguna` | `ac9927716f504c7d8919aff703257e4bb840c813` | 2026-09-13 | `fix(release): correct next-SNAPSHOT computation + clarify RELEASE_PAT scope` | `05bb6e66` (dependabot.yml, 2026-09-13) | 67 | 60 | **v1-implementation WIP** |

## Active v1 Work

> **No remote branch is currently being actively committed to.** All five remote
> branches are stale (last commit Sep 13 or earlier; `main` and the local
> `wayfinder/kompact-v1` scaffold are at Sep 17). However, **`feat/laguna`** is the
> branch that **contains** the v1 implementation — and its work has already been
> **squash-merged into `main`** (commit `2571014` — `feat: Kompact v1 — KMP
> bit-packing serializer, KSP processor, release automation, full docs` — is an
> ancestor of `origin/main`). The resulting releases v0.1.0–v0.1.7 are in `main`,
> which is now at `0.2.0-SNAPSHOT`.

The local `wayfinder/kompact-v1` branch (HEAD `edfaa885`, 2026-09-17,
`feat(wayfinder): chart Kompact v1.0 implementation map + frontier`) is the
**currently active wayfinder scaffold** — it is `main` + the wayfinder map/issue
files. It is **not** the same as `origin/feat/kompact-v1` (different tip commit).

## Per-Branch Notes

### 1. `feat/kompact-v1` → **stale-abandoned**

An earlier, standalone v1 foundation implementation (9 commits, Aug 30–31). Key
commits: `d13963f feat: implement Kompact v1 foundation`, KSP common-processing
fixes, generated schema-type validation, static nested schema accessors, nested
array accessors, and `feat: complete Kompact v1 repository gates`.

- Not an ancestor of `main` (neither branch-ancestry nor squash-merge).
- `git merge-base --is-ancestor origin/feat/kompact-v1 origin/main` → **NO**.
- Its 9 commits are absent from `main`; a later, more comprehensive v1 effort
  (`feat/laguna`) superseded it and was integrated instead.
- **Stale** (no commits since Aug 31; 17 days behind the Sep 17 `main` tip).
- **Verdict:** abandoned dead-end. The module/API shape it prototyped was
  superseded by laguna's approach.

### 2. `prototype/c99-interface` → **experiment**

Single-commit (Aug 30) exploratory documentation: `docs: prototype C99 interface
options`. A design-space exploration for a C99 FFI surface.

- Only 1 commit ahead of main; 77 behind.
- Merge-base `b7c1e39b` (PR #15 — `spec/kompact-v1-decisions`).
- Documentation-only; never produced code or a merged decision.
- C emission is explicitly **out of scope** per the wayfinder map
  (`.scratch/kompact-v1/map.md` → "Out of scope: C emission / C reference spec").
- **Verdict:** irrelevant to v1 implementation. Archive / ignore.

### 3. `prototype/kotlin-interface` → **experiment**

Single-commit (Aug 30) exploratory documentation: `docs: prototype Kotlin
interface options`. A design-space exploration for the generated Kotlin API
shape.

- Only 1 commit ahead of main; 79 behind.
- Merge-base `8d929236` (PR #15 — `spec/kompact-v1-decisions`).
- Documentation-only; informed the interface-shape decision but never merged as
  code.
- **Verdict:** exploratory input to the ratified decisions, not implementation.
  Archive / ignore.

### 4. `feat/minimax` → **experiment**

A wayfinder / planning branch (22 commits, Sep 1–3) that shares early commits with
`feat/laguna` but diverged into **planning documentation and Diátaxis docs/CI
work** rather than implementation. Key commits:

```
74fb05e  chore: cleaned up
0987840  feat(plan): chart wayfinder map for Kompact serialization framework
40140bc  feat(plan): resolve v1 type set, seed framing frontier ticket
d2c9b58  feat(plan): resolve framing, seed validation frontier
6879941  feat(plan): resolve validation model, seed write/builder ticket
a46577b  feat(plan): resolve runtime error model, seed versioning ticket
88d16a1  feat(plan): resolve versioning model, seed testing-model ticket
36da395  feat(plan): resolve testing model, seed performance-evidence ticket
a75037c  feat(plan): resolve testing model, fold perf-evidence, seed module-split ticket
83418be  feat(plan): resolve module split & publication, lock destination spec
177f220  docs: performance evidence plan
1f44876  docs: resolve KMP/KSP publication wiring (Ticket 13)
d0a7513  feat(impl): implement Kompact serialization framework per locked map
1349083  docs: add README, how-to, reference, explanation per diataxis
72946a7  fix(workflow): repair diataxis-pr-docs engine.model expression
18dc77c  ci: trigger Diátaxis PR Docs Auditor
0bde165  docs: remove implementation-ticket refs, switch diagrams to ASCII
```

- Merge-base `4d02cc48` (PR #22) — same fork point as `feat/kompact-v1`.
- 22 ahead / 64 behind `main`.
- Dominated by `feat(plan)` and `docs:` commits; only one `feat(impl)` commit.
- Pivot point: after the shared planning commits, minimap went into docs/CI
  cleanup while `feat/laguna` went into concrete KMP/KSP implementation.
- Not an ancestor of `main`; not merged.
- **Verdict:** planning / wayfinder experiment. The "minimax" codename and
  docs-heavy trajectory confirm exploratory intent. Ignore for scaffold base.

### 5. `feat/laguna` → **v1-implementation WIP** (integrated into main)

The definitive v1 implementation branch (67 commits, Sep 1–13). This is where the
actual Kompact v1.0 was built — KMP runtime, KSP processor, tests, CI/CD, full
docs, and release automation. Key implementation commits:

```
35158b2  feat(kmp): bootstrap Kompact runtime + VehicleTelemetry + publication gates
546df38  feat(kmp): v1 error model + checked reads + 64-bit bit primitives
9376278  feat(kompact): serialization framework foundation slice
f4ea95c  feat(ksp): KompactSymbolProcessor provider, ValueClassGenerator, 100% kover coverage
13f2f5b  feat: register Kompact.Result namespace + split checked-read tests
b4f478f  refactor(kompact): expose INVALID_LENGTH_PREFIX sentinel (Q9)
4ef6bc6  docs(versioning): defer versioning surface to v2 (ADR-0002 + issue 15)
50bfc11  fix(review): resolve code-review findings — Kotlin 2.4.20, S1, Q2, POM
ff35331  refactor(ksp): KompactSymbolProcessorProvider, map-based generator dispatch
cbfebc3  fix(ksp): eliminate KSP 2.3.12 deprecation warnings
ac99277  fix(release): correct next-SNAPSHOT computation + clarify RELEASE_PAT scope
```

- 67 ahead / 60 behind `main`.
- Merge-base `05bb6e66` (`Create dependabot.yml`, 2026-09-13) — lagged forked from
  a late point on `main`.
- **Squash-merged into `main`** as commit `2571014` (`feat: Kompact v1 — KMP
  bit-packing serializer, KSP processor, release automation, full docs`).
  `git merge-base --is-ancestor 2571014 origin/main` → **YES**.
- Released as **v0.1.0 → v0.1.7** (tags visible in the `wayfinder/kompact-v1`
  integration history); `main` is now at `0.2.0-SNAPSHOT` (5a78cc4, Sep 17).
- The branch is now stale as a development line (no commits since Sep 13), but
  it is the **authoritative source** of the v1 implementation that now lives in
  `main`.
- Local `feat/laguna` == `origin/feat/laguna` (same SHA `ac99277`).
- **Verdict:** This is the v1 implementation. Its work is done and integrated —
  align the scaffold to `main`, not to the branch tip.

## Pre-existing v1 Artifacts in `main` (relevant to ticket 05)

The laguna work integrated into `main` already produced the structures ticket 05
asks the scaffold to create:

| Artifact | Path in `main` |
|---|---|
| `:kompact` KMP runtime (JVM + iosArm64 + iosSimulatorArm64) | `kompact/` |
| `:kompact-ksp` JVM processor | `kompact-ksp/` |
| ABI golden (JVM) | `kompact/api/jvm/kompact.api` |
| ABI golden (klib) | `kompact/api/kompact.klib.api` |
| ABI golden (Android) | `kompact/api/android/kompact.api` |
| ABI golden (KSP) | `kompact-ksp/api/kompact-ksp.api` |
| Release automation | `.github/workflows/release-publish.yml` |
| CHANGELOG (v0.1.0–v0.1.7) | `CHANGELOG.md` |

## Recommendation for the v1 Scaffold (Wayfinder Ticket 05)

> **Align the v1 scaffold to `main`, not to `feat/laguna` (or any stale branch).**

The Kompact v1.0 implementation is **already consolidated in `main`** — squash-merged
from `feat/laguna`, released as v0.1.0–v0.1.7, with committed ABI goldens (`./kompact/api/*.api`,
`./kompact-ksp/api/kompact-ksp.api`) and the `:kompact` + `:kompact-ksp` module
structure already in place. The five remote branches are all stale historical
artifacts:

- **`feat/laguna`** is the v1 implementation (reference it for the development
  narrative, but build the scaffold on `main` which contains its integrated,
  released, ABI-locked result).
- **`feat/kompact-v1`** is a superseded earlier foundation attempt — explicitly
  **not** merged into `main`; do not align to it.
- **`feat/minimax`** and the two **`prototype/*`** branches are planning / docs
  experiments with no integration into `main` — ignore for scaffold base.

**One-line recommendation:** Build ticket 05's v1 module scaffold and ABI golden on
`origin/main` (where laguna's v1 is already released, ABI-locked, and CI-gated),
treating `feat/laguna` as the superseded development record only.

## Fog Graduated

This research resolves the "State of the remote branches" fog item noted in
`.scratch/kompact-v1/map.md` ("Not yet specified"). Ticket 05 (scaffold) should now
reference `main` as the authoritative v1 base and extract the module layout +
ABI golden state from there.

<!--
  File: .scratch/kompact-v1/research/04-remote-branch-inspection.md
  No remote refs were mutated. No fetches were required (all origin/* refs present).
  No stray local tracking refs created.
-->
