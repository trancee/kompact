# Continuous integration

This is a how-to for the CI workflow. If you want to know **what the
gates check and why**, read this end-to-end. If you just want to
re-run a gate locally, jump to [Re-running gates locally](#re-running-gates-locally).

---

## The gates

Two workflow files live in [`.github/workflows/`](../.github/workflows/):

| Workflow | File | Runner | Gates |
| --- | --- | --- | --- |
| **CI** | `ci.yml` | `api-check` on `macos-latest`; `jvmTest + jvmApiCheck` on `ubuntu-latest` | Public ABI goldens (JVM + merged iOS klib) on macOS; `jvmApiCheck` on JVM (fast gate); `commonTest` on the JVM |
| **Regen Goldens** | `regen-goldens.yml` | `macos-latest` | Regenerate the BCV goldens (manual `workflow_dispatch`) |

The CI workflow triggers on pushes to `main`, `master`, and `feat/**`,
and on pull requests into `main` or `master`. The regen workflow is
`workflow_dispatch` only — it does not auto-trigger on push.

### `api-check` (macOS)

Runs `./gradlew :kompact:apiCheck --no-daemon` on the latest macOS
runner with JDK 21 (Temurin). `apiCheck` compares the committed BCV
goldens against the freshly-inferred ABIs with `strictValidation = true`:

- `kompact/api/kompact.api` — the JVM bytecode ABI (compiled from the
  current source).
- `kompact/api/kompact.klib.api` — the merged iOS klib ABI (the union
  of `iosArm64` and `iosSimulatorArm64`, compiled and dumped only on
  Apple hosts).

**Why this gate is the final gate.** The golden files pin the public ABI
of the runtime. A change that accidentally narrows or widens a public
signature — or that adds a new public declaration without a deliberate
golden bump — breaks this gate and forces a review. The JVM golden is
host-independent; the iOS klib golden is **only meaningfully validated on
a macOS runner** because iOS klibs can only be compiled there.
`strictValidation = true` makes `klibApiCheck` fail rather than silently
infer on a non-Apple host, so this macOS job is the only one that
catches klib drift — it remains the final gate.

### `jvmTest + jvmApiCheck (Linux)`

Runs `./gradlew :kompact:jvmTest :kompact:jvmApiCheck --no-daemon` on
Ubuntu with JDK 21 (Temurin).

- `jvmTest` runs the `commonTest` suite on the JVM target. The suite
  covers round-trip unit tests, property-based tests, the allocation
  discipline, and the long-form framing tests (strings / blobs / nested /
  repeated).
- `jvmApiCheck` compares the committed `kompact/api/kompact.api` JVM
  golden against the freshly-inferred JVM ABI. It is a JVM task with no
  native toolchain dependency, so it runs on any host.

**Why this gate exists.** The macOS `api-check` job is the final gate, but
it carries a ~6 min queue. Folding `jvmApiCheck` into the Linux job gives
contributors fast feedback — a JVM-API drift fails the PR on Linux before
the macOS runner is even scheduled. The iOS klib half is deliberately
**not** run here: `strictValidation = true` makes `klibApiCheck` fail on
non-Apple hosts (iOS klibs can't be compiled on Linux), so this job runs
`jvmApiCheck` only and leaves the klib check to the macOS job.

### `Regen Goldens` (macOS, manual)

Runs `./gradlew :kompact:apiDump --no-daemon` on macOS and uploads
`kompact/api/` as a downloadable artifact (`api-goldens`, 3-day
retention). Use this workflow when:

- You have intentionally added, removed, or changed a public
  declaration and the committed golden needs to be updated.
- You are on a non-Mac host and need the canonical iOS klib golden
  (only macOS can infer it).

The workflow is `workflow_dispatch` only because regenerating the
goldens on every push would make the iOS golden churn — and it requires
a macOS runner (slow + metered). Trigger it from the GitHub Actions
tab → **Regen Goldens** → **Run workflow** → pick the branch, then
download the `api-goldens` artifact and replace the files under
`kompact/api/`.

> **Note.** Because this workflow lives on a feature branch in the
> current development setup, the GitHub UI is the only place to
> dispatch it (`gh workflow run` resolves the workflow on the default
> branch and returns 404 for feature-branch-only workflows).

---

## Re-running gates locally

You do not need a CI runner to verify the gates — `apiCheck`,
`jvmApiCheck`, `jvmTest`, and `apiDump` are all ordinary Gradle
tasks. The split follows the host:

- **On macOS** (all targets supported): run the full `apiCheck`
  (JVM + iOS klib, with `strictValidation = true`).
- **On Linux / Windows** (iOS klibs can't be compiled): run
  `jvmApiCheck` for the JVM golden. `apiCheck` will fail on the
  klib part — `strictValidation = true` makes `klibApiCheck` fail
  on unsupported targets instead of silently inferring — so run
  the two tasks separately on a non-Apple host.

```bash
# JVM tests + JVM API check (Linux + macOS)
./gradlew :kompact:jvmTest :kompact:jvmApiCheck

# Full ABI check — macOS only (iOS klibs can only be compiled here)
./gradlew :kompact:apiCheck

# Regenerate the goldens in place (macOS only)
./gradlew :kompact:apiDump
```

When you change a public JVM declaration, update the golden
locally with `:kompact:jvmApiDump`. When you change a public iOS
declaration, regenerate the klib golden on macOS — Linux cannot
produce it. The `Regen Goldens` workflow is the supported way to
get the iOS golden updated from a non-Mac host.

## Build environment

Both workflows pin to JDK 21 (Temurin) and enable the Gradle build
cache. The `:kompact` module builds with the Kotlin 2.4.10 Gradle
plugin and KMP targets `jvm` (JVM 21), `iosArm64`, and
`iosSimulatorArm64`. KGP auto-creates the per-target publications via
`maven-publish`; `ch.trancee.kompact:kompact` is staged for Maven
Central Portal via a custom Portal Publisher API task (no third-party
publishing plugin). See `.scratch/kompact-spec/issues/14-maven-central-publishing.md`
for the release contract and remaining authorization-gated steps.
