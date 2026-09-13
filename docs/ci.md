# Continuous integration

This is a how-to for the CI workflow. If you want to know **what the
gates check and why**, read this end-to-end. If you just want to
re-run a gate locally, jump to [Re-running gates locally](#re-running-gates-locally).

---

## The gates

Two workflow files live in [`.github/workflows/`](../.github/workflows/):

| Workflow | File | Runner | Gates |
| --- | --- | --- | --- |
| **CI** | `ci.yml` | `checkKotlinAbi + spotlessCheck + Markdown (macOS)` on `macos-latest`; `checkKotlinAbi + jvmTest + koverVerify + Android assemble + KSP Portal dry-run (Linux)` on `ubuntu-latest` | `spotlessCheck` + `checkKotlinAbi` (all KMP targets) on macOS; `spotlessCheck` + `checkKotlinAbi` (JVM + Android, klib inferred) + `koverVerify` + `jvmTest` + `bundleAndroidMainAar` + `kompact-ksp:` gates on Linux |
| **Regen Goldens** | `regen-goldens.yml` | `macos-latest` | Regenerate the KGP built-in ABI goldens (manual `workflow_dispatch`) |

Both CI jobs run with `--no-daemon --rerun-tasks --no-build-cache --warning-mode all`
to force re-execution, disable the build cache, and surface all
deprecation/compiler warnings. The CI workflow triggers on pushes to
`main`, `master`, and `feat/**`, and on pull requests into `main` or
`master`. The regen workflow is `workflow_dispatch` only — it does not
auto-trigger on push.

### `checkKotlinAbi + spotlessCheck + Markdown` (macOS)

Runs `./gradlew spotlessCheck :kompact:checkKotlinAbi :kompact-ksp:checkKotlinAbi`
on the latest macOS runner with JDK 21 (Temurin) + Android SDK (API 36).
`checkKotlinAbi` compares the committed KGP built-in ABI goldens against
the freshly-inferred ABIs with `keepLocallyUnsupportedTargets = true`:

- `kompact/api/kompact.api` — the JVM bytecode ABI (compiled from the
  current source for the JVM + Android targets).
- `kompact/api/kompact.klib.api` — the merged iOS klib ABI (the union
  of `iosArm64` and `iosSimulatorArm64`, compiled and dumped only on
  Apple hosts).
- `kompact/api/jvm/kompact.api` — per-variant ABI dump (built-in ABI
  validation creates this for each target).
- `kompact-ksp/api/kompact-ksp.api` — the KSP processor JVM ABI.

`spotlessCheck` enforces ktlint formatting across all Kotlin and
Gradle files (including `build-logic/`) before the ABI is inferred.

The **macOS job** also regenerates the Markdown reference and fails the build if
the committed tree drifts from the KDoc in `commonMain` (it runs
`dokkaGeneratePublicationMarkdown`, then `git diff --exit-code -- kompact/docs/api/`).
This runs only on macOS: Dokka analyses the iOS klibs, which can't be compiled
on Linux. The KDoc comments in `commonMain` are the source of truth for the API
surface; `docs/api/` is a rendered convenience copy committed so the in-repo
link is always live. `docs/api/` is tracked (no `.gitignore` rule applies to it).

**Why this gate is important.** The golden files pin the public ABI
of the runtime + KSP processor. A change that accidentally narrows or widens a
public signature — or that adds a new public declaration without a deliberate
golden bump — breaks this gate and forces a review. The JVM golden is
host-independent; the iOS klib golden is **only meaningfully validated on
a macOS runner** because iOS klibs can only be compiled there.
`keepLocallyUnsupportedTargets = true` allows Linux to infer iOS klib while
validating JVM + Android for real, so this macOS job is the only one that
catches klib drift — it remains the final ABI gate.

### `checkKotlinAbi + jvmTest + koverVerify + Android + KSP Portal` (Linux)

Runs `./gradlew spotlessCheck :kompact:checkKotlinAbi :kompact:jvmTest :kompact:koverVerify :kompact:bundleAndroidMainAar :kompact-ksp:checkKotlinAbi :kompact-ksp:test :kompact-ksp:koverVerify`
on Ubuntu with JDK 21 (Temurin). Ubuntu runners ship the Android SDK
(compileSdk 36, build-tools 36.0.0) pre-installed, so Android target
compilation and ABI validation work here.

- `spotlessCheck` — enforces ktlint formatting (read-only check;
  `spotlessApply` is for local developer formatting).
- `checkKotlinAbi` — validates the JVM + Android ABI goldens
  (`kompact/api/kompact.api`, `kompact/api/jvm/kompact.api`). iOS klib
  is inferred on Linux (not compiled).
- `koverVerify` — enforces **100% line + 100% branch coverage** on the
  JVM target (per the Kover verify rules in `build.gradle.kts`). The
  coverage gate is a quality bar: uncovered branches fail the build.
- `jvmTest` runs the `jvmTest` suite (JVM-only coverage-pinning tests)
  plus the `commonTest` suite on the JVM target. The suite covers
  round-trip unit tests, property-based tests, the allocation
  discipline, edge-case/bounds-hardening tests, the long-form framing
  tests (strings / blobs / nested / repeated), and the JVM coverage-
  pinning tests.
- `bundleAndroidMainAar` — compiles the Android target and produces
  `kompact/build/outputs/aar/kompact.aar`. This is the task that
  validates the Android KMP library plugin works end-to-end.
- `kompact-ksp:test` runs the KSP processor's JVM test suite
  (unit tests for `ValueClassGenerator`, `LayoutValidator`,
  `KompactSymbolProcessor`, and `ModelSpec`).
- `kompact-ksp:koverVerify` enforces 100% line + branch coverage on
  the `kompact-ksp` module (same bar as `:kompact:koverVerify`).
- `kompact-ksp:checkKotlinAbi` validates the KSP module's public ABI
  golden (`kompact-ksp/api/kompact-ksp.api`) against the inferred ABI.

**KSP Portal dry-run.** After the test/coverage/ABI gates pass, the Linux
job generates an ephemeral PGP key and runs `:kompact-ksp:generateChecksums`
+ `:kompact-ksp:assembleCentralBundle` to verify the full Maven Central Portal
bundle pipeline works end-to-end without publishing to Central. The ephemeral
key is never stored — it exists only for the duration of the CI job.

**Why this gate exists.** The macOS job carries a ~6 min queue and
compiles iOS klibs (slow). Folding `jvmTest`, `checkKotlinAbi` (JVM + Android),
`koverVerify`, `bundleAndroidMainAar`, and `spotlessCheck` into the Linux job
gives contributors fast feedback — a test failure, coverage regression, ABI
drift, Android compile failure, or format violation fails the PR on Linux
before the macOS runner is even scheduled. The kompact-ksp tasks are folded in
here too for the same fast-feedback reason.

### `Regen Goldens` (macOS, manual)

Runs `./gradlew :kompact:updateKotlinAbi` on macOS and uploads
`kompact/api/` as a downloadable artifact (`api-goldens`, 3-day
retention). Use this workflow when:

- You have intentionally added, removed, or changed a public
  declaration and the committed golden needs to be updated.
- You are on a non-Mac host and need the canonical iOS klib golden
  (only macOS can infer it).

The workflow is `workflow_dispatch` only because regenerating the
goldens on every push would cause iOS golden churn — and it requires
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

You do not need a CI runner to verify the gates — `checkKotlinAbi`,
`updateKotlinAbi`, `jvmTest`, `koverVerify`, `spotlessCheck`, and
`bundleAndroidMainAar` are all ordinary Gradle tasks. The split
follows the host:

- **On macOS** (all targets supported): run the full `checkKotlinAbi`
  (JVM + Android + iOS klib), plus `spotlessCheck`, `koverVerify`, and
  `jvmTest` for the quality bars, plus `bundleAndroidMainAar` for the
  Android target.
- **On Linux / Windows** (iOS klibs can't be compiled): run the
  Linux gate set (`spotlessCheck`, `koverVerify`, `jvmTest`,
  `checkKotlinAbi`). `checkKotlinAbi` infers the iOS klib golden on
  non-Apple hosts (via `keepLocallyUnsupportedTargets = true`) but
  validates JVM + Android ABI for real — it does **not** fail.

```bash
# macOS — full gate set (all quality bars + full ABI check + KSP tests + Android AAR)
./gradlew spotlessCheck :kompact:checkKotlinAbi :kompact:koverVerify :kompact:jvmTest \
  :kompact:bundleAndroidMainAar :kompact:dokkaGeneratePublicationMarkdown \
  :kompact-ksp:checkKotlinAbi :kompact-ksp:test :kompact-ksp:koverVerify \
  --no-daemon --rerun-tasks --no-build-cache --warning-mode all

# Linux / Windows — all gates that work without iOS klib compilation
./gradlew spotlessCheck :kompact:checkKotlinAbi :kompact:koverVerify :kompact:jvmTest \
  :kompact:bundleAndroidMainAar :kompact-ksp:test :kompact-ksp:checkKotlinAbi \
  :kompact-ksp:koverVerify \
  --no-daemon --rerun-tasks --no-build-cache --warning-mode all

# Regenerate the goldens in place (macOS only)
./gradlew :kompact:updateKotlinAbi

# KSP Portal dry-run (checksums + bundle, no real publishing)
./gradlew :kompact-ksp:generateChecksums :kompact-ksp:assembleCentralBundle
```

When you change a public JVM declaration, update the golden
locally with `:kompact:updateKotlinAbi` (regenerates all targets' goldens).
When you change a public iOS declaration, regenerate the klib golden on
macOS — Linux cannot produce it. The `Regen Goldens` workflow is the
supported way to get the iOS golden updated from a non-Mac host.

---

## Build environment

The CI uses JDK 21 (Temurin) and Kotlin 2.4.20. The `:kompact` module
is a Kotlin Multiplatform project targeting `jvm` (JVM 21), `android`
(Android library via AGP 9.4.0), `iosArm64`, and `iosSimulatorArm64`.
KGP auto-creates the per-target publications via `maven-publish`;
`ch.trancee.kompact:kompact` is staged for Maven Central Portal via a
custom Portal Publisher API task (`centralPortalDeploy`, no
third-party publishing plugin). No release has been cut yet — the
Portal namespace, PGP key, and user token still require user
authorization. The `:kompact-ksp` processor is a JVM-only module
targeting JVM 17, published as a standard Maven JAR with sources +
javadoc stubs.
