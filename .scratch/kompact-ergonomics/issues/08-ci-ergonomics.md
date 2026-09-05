Type: grilling
Status: resolved

# 08 — CI ergonomics: should Linux run `jvmApiCheck` too?

## Question

The CI workflow (`.github/workflows/ci.yml`) splits across runners:

- `api-check` on `macos-latest` — runs `:kompact:apiCheck`, which
  covers both the JVM ABI (`kompact/api/kompact.api`) and the
  merged iOS klib ABI (`kompact/api/kompact.klib.api`).
- `jvm-test` on `ubuntu-latest` — runs `:kompact:jvmTest`.

The split is necessary because only macOS can infer the iOS klib
ABI (per the v1 spec map's research note). But it has a
downside: a Linux contributor cannot run `apiCheck` locally —
`jvmApiCheck` runs on Linux (it's a JVM ABI check), but the
`klibApiCheck` part is a no-op on non-Apple hosts (a known
behaviour of BCV 0.18.0). A Linux contributor who changes the
public API gets a false green locally and only finds out about
the klib drift on the macOS CI run.

The decision: do we add a Linux-side `:kompact:jvmApiCheck` job
(sibling of `jvm-test`) that catches the JVM part of the API
drift on every PR, leaving the klib check to the macOS job? Or
is the current split the right shape (one macOS job does both
APIs, one Linux job does the JVM tests, the Linux-API gap is
accepted)?

## Context for the claiming session

- `.github/workflows/ci.yml` — the two-job shape.
- `kompact/build.gradle.kts` — `apiValidation { klib { enabled = true } }`
  (lines ~43–47) enables klib inference.
- `docs/ci.md` — the user-facing explanation of the current
  shape. The "Re-running gates locally" section currently
  documents that the iOS half of `apiCheck` is a no-op on
  non-Mac hosts.
- The v1 spec map's ticket 13 ("KMP/KSP publication wiring")
  resolved the BCV 0.18.0 / klib-enable wiring; the locked
  decision is the current shape, not the alternative.

## Open sub-questions

1. **What does "Linux catches JVM API drift" actually buy us?**
   The macOS `api-check` job already runs `jvmApiCheck` as part
   of the combined `apiCheck`; the only thing the new Linux
   job would do is *fail the PR earlier* (before the macOS
   runner is even scheduled). For a feature branch like
   `feat/laguna`, the macOS job is ~6 min and the Linux job
   is ~10s. The earlier-fail saves time only on a *failed*
   JVM-API check.
2. **Does the new job compose with the existing `jvm-test`?**
   The simplest shape is one job that runs both
   `jvmTest` and `jvmApiCheck` on Linux. The two tasks share
   the JVM compile (so the wall-clock cost is dominated by
   `jvmTest`), and a single job is easier to reason about
   than two Linux jobs.
3. **Does this change the gate semantics for a contributor?**
   Today, a Linux contributor who breaks the JVM API sees
   the red on the macOS `api-check` job only (with a
   ~6-min queue). With the new Linux job, they see the red
   on Linux (no queue). The change is a faster feedback loop
   for JVM API drift, not a new gate.
4. **The `regen-goldens.yml` workflow** is `workflow_dispatch`
   only and lives on the feature branch. Should it move to
   `main` so it's dispatchable as a public escape hatch, or
   stay on the feature branch until the first release?

## What "resolved" looks like

- The chosen CI shape is recorded under `## Answer` with a
  one-line rationale.
- The chosen `regen-goldens.yml` location (feature branch vs.
  main) is recorded if the question is in scope.
- `docs/ci.md` is updated (or scheduled for the implementation
  commit) to reflect the new shape.
- The implementation commit updates `.github/workflows/ci.yml`
  accordingly and re-runs the macOS `api-check` to confirm
  green.

## Answer

**Decision: fold `:kompact:jvmApiCheck` into the existing
`jvm-test` job (Ubuntu, JDK 21). The macOS `api-check` job
stays as the final gate. `regen-goldens.yml` stays on
`feat/laguna` (manual `workflow_dispatch`; GitHub UI path).**

### CI shape

`ci.yml`:
```yaml
jobs:
  api-check:
    name: apiCheck (macOS)
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21, cache: gradle }
      - run: ./gradlew :kompact:apiCheck --no-daemon   # unchanged

  jvm-test:
    name: jvmTest + jvmApiCheck (Linux)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21, cache: gradle }
      - run: ./gradlew :kompact:jvmTest :kompact:jvmApiCheck --no-daemon
```

The `jvm-test` job name updates to `jvmTest + jvmApiCheck (Linux)`
to advertise the two gates in the PR checks list. The two
tasks share the JVM compile (`:kompact:compileKotlinJvm` is
the common prerequisite), so the wall-clock cost of adding
`:kompact:jvmApiCheck` is dominated by the API comparison
(a few seconds). The macOS `api-check` job continues to run
the full `apiCheck`, which transitively includes
`jvmApiCheck` + `klibApiCheck` — so the macOS job is the
*final* gate (it catches everything), and the Linux job is
the *fast* gate (it catches JVM API drift before the macOS
queue).

### Why this option

The current split's only reason to exist is the macOS-only klib
inference. The JVM API check has no such constraint and
should run on every host that can run it (every host — it's
a JVM task with no native toolchain). A Linux contributor who
breaks the JVM API today gets a false green on the PR; the
macOS job catches it after a ~6-min queue. Folding `jvmApiCheck`
into the existing `jvm-test` job closes the gap with no extra
wall-clock cost (the JVM compile is shared) and no job sprawl
(one Linux job, one macOS job).

### What this does NOT change

- **The macOS `api-check` job stays as the final gate.** It
  continues to run the full `apiCheck` (which transitively
  includes `jvmApiCheck` + `klibApiCheck`). The macOS job is
  the only one that catches klib drift; the Linux job catches
  the JVM subset only.
- **No new job.** One Linux job, one macOS job (same as today).
- **No new runner type.** Still Ubuntu for the JVM gate,
  macOS for the klib gate.

### Regen-goldens location

`regen-goldens.yml` stays on `feat/laguna` only (current
state). The dispatch path is: GitHub UI → Actions → Regen
Goldens → Run workflow → pick `feat/laguna`. The `gh
workflow run` path is still 404 (default-branch lookup),
but the UI path works. Moving the workflow to `main` is a
merge-time concern, not this effort's blast radius.

### Propagation

- **`docs/ci.md`** — the "The gates" table updates: the
  `jvm-test` job is renamed to `jvmTest + jvmApiCheck
  (Linux)` and now runs both tasks. The "Re-running gates
  locally" section adds `:kompact:jvmApiCheck` to the local
  command. The "regen-goldens" section stays as-is (location
  unchanged).
- **The `GettingStartedTest` and `VehicleTelemetryTest` do not
  change.** The new `jvmApiCheck` task runs the existing
  `kompact/api/kompact.api` golden against the freshly-inferred
  JVM ABI; if the implementation commits in this effort
  (tickets 01–07) change the public JVM ABI, the golden
  needs to be regenerated *before* the Linux job turns
  green. The implementation order matters: the public-ABI
  changes (tickets 01, 04, 05) commit together with the
  regenerated `kompact.api` golden (the existing
  `regen-goldens.yml` workflow handles this; the
  implementation commit per ticket records "regen the
  JVM golden in the same commit" in the map's `Notes`).
- **Ticket 10** (Docs layer structure review) — the
  `docs/ci.md` update is a small doc edit; ticket 10's
  "is the layering right?" question is not affected.

## Comments
