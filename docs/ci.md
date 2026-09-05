# Continuous integration

This is a how-to for the CI workflow. If you want to know **what the
gates check and why**, read this end-to-end. If you just want to
re-run a gate locally, jump to [Re-running gates locally](#re-running-gates-locally).

---

## The gates

Two workflow files live in [`.github/workflows/`](../.github/workflows/):

| Workflow | File | Runner | Gates |
| --- | --- | --- | --- |
| **CI** | `ci.yml` | `api-check` on `macos-latest`; `jvm-test` on `ubuntu-latest` | Public ABI goldens (JVM + merged iOS klib); `commonTest` on the JVM |
| **Regen Goldens** | `regen-goldens.yml` | `macos-latest` | Regenerate the BCV goldens (manual `workflow_dispatch`) |

The CI workflow triggers on pushes to `main`, `master`, and `feat/**`,
and on pull requests into `main` or `master`. The regen workflow is
`workflow_dispatch` only — it does not auto-trigger on push.

### `api-check` (macOS)

Runs `./gradlew :kompact:apiCheck --no-daemon` on the latest macOS
runner with JDK 21 (Temurin). `apiCheck` compares the committed BCV
goldens against the freshly-inferred ABIs:

- `kompact/api/kompact.api` — the JVM bytecode ABI (compiled from the
  current source).
- `kompact/api/kompact.klib.api` — the merged iOS klib ABI (the union
  of `iosArm64` and `iosSimulatorArm64`, inferred only on Apple hosts).

**Why this gate exists.** The golden files pin the public ABI of the
runtime. A change that accidentally narrows or widens a public
signature — or that adds a new public declaration without a deliberate
golden bump — breaks this gate and forces a review. The JVM golden is
host-independent; the iOS klib golden is **only meaningfully
validated on a macOS runner** (the Linux CI job cannot infer the iOS
klib ABI). That is why the API check is split across runners.

### `jvm-test` (Linux)

Runs `./gradlew :kompact:jvmTest --no-daemon` on Ubuntu with JDK 21
(Temurin). `jvmTest` runs the `commonTest` suite on the JVM target.
The suite covers round-trip unit tests, property-based tests, the
allocation discipline, and the long-form framing tests
(strings / blobs / nested / repeated).

**Why this gate exists.** A pure ABI check is not enough — a source
change can pass the goldens (no public-surface drift) and still break
the runtime behaviour. `jvmTest` is the behavioural safety net.

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

You do not need a CI runner to verify the gates — both `apiCheck`
and `jvmTest` are ordinary Gradle tasks. The difference is that on
Linux you can only verify the JVM side; the iOS klib inference needs
a macOS host.

```bash
# JVM tests (Linux + macOS)
./gradlew :kompact:jvmTest

# API check — JVM part runs anywhere; iOS klib part only on macOS
./gradlew :kompact:apiCheck

# Regenerate the goldens in place (macOS only)
./gradlew :kompact:apiDump
```

When the goldens drift on a non-Mac host, the iOS half of
`apiCheck` is a no-op and you'll see a false green. The
`Regen Goldens` workflow is the supported way to get the iOS
golden updated from a non-Mac host.

## Build environment

Both workflows pin to JDK 21 (Temurin) and enable the Gradle build
cache. The `:kompact` module builds with the Kotlin 2.4.10 Gradle
plugin and KMP targets `jvm` (JVM 21), `iosArm64`, and
`iosSimulatorArm64`. No hand-rolled `multiplatformPublication` DSL —
KGP auto-creates the per-target artifacts and
`com.vanniktech.maven.publish` 0.37.0 publishes to Maven Central.
