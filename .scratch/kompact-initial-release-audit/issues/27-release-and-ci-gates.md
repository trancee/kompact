---
Type: task
Blocked by: 24, 25, 26
---

## Objective

Make the agreed artifact, plugin, platform, and release-governance checks
required gates, then release the first supported version only through the
compliant reviewed workflow.

## Decisions

- Follow [artifact/plugin quality gates](19-release-artifact-gates.md),
  [cross-platform gates](18-cross-platform-release-gates.md), and
  [release-policy alignment](20-release-policy-alignment.md).
- Preserve Constitution G1: version/changelog changes use a human-reviewed
  release PR; automation must not push commits directly to protected `main`.
- Release `0.8.0` from the declared `0.8.0-SNAPSHOT` only after all required
  checks and platform evidence pass.

## Acceptance criteria

- Dry-run the `:kompact` publication bundle on every PR.
- Gate the published Gradle plugin with a Linux JVM ABI check and 100% line
  and branch coverage for production plugin code, with no production
  exclusions.
- Run required JVM and macOS CI checks, and record the exact external GitHub
  required-status configuration that maintainers must enforce.
- Keep release-only iOS Arm64 and Android Native Arm64 device tests mandatory
  before publication; do not substitute simulator or mock results for
  hardware evidence.
- Align version-bump/changelog automation with the declared Gradle candidate
  and test the `0.8.0-SNAPSHOT` -> `0.8.0` first-supported-release case.
- Remove automated direct pushes/version commits to protected `main`; route
  every release-version change through the human-reviewed release PR required
  by Constitution G1.
- Ensure release documentation and changelog are reviewed and current in the
  release PR; publish only after those updates and every required gate pass.
- Run a clean-checkout CI-equivalent verification and record any external
  prerequisite that cannot be verified from the repository.

## Latest verification

- Forced `:kompact-gradle-plugin:koverVerify` passes its required 100% line
  and branch coverage thresholds after the plugin contract tests were expanded.
  Production plugin code has no coverage exclusions.
- The forced Linux CI-quality task set passes locally, including runtime/JVM
  and Android host tests, KSP and Gradle-plugin suites, ABI checks, coverage,
  plugin validation, and Android AAR assembly. The macOS
  `:kompact:iosSimulatorArm64Test` and ABI checks also pass.
- `spotlessCheck`, Dokka Markdown generation, workflow linting, release-script
  fixtures, documentation-version checks, and `git diff --check` pass.
- The inspected CI/Gradle configuration has no relative-link or spelling/markup
  checker; those documentation checks have not been verified.
- Android-KMP host-side runtime tests are now configured and included in Linux
  CI via `:kompact:testAndroidHostTest`; a forced local run passed. This is not
  physical Android Native device evidence.
- Physical iOS/Android Native execution and allocation evidence remain open
  release blockers as recorded in [platform proof](25-platform-proof.md).
- GitHub CI passed both required Linux and macOS jobs on PR #89 commit
  `f3bb4ae`; the Linux run also linked and uploaded the Android Native arm64
  test executable. The exact artifact passed all 387 tests on the physical
  Android 15 arm64 device; evidence and the reproducible runner are recorded
  in [platform proof](25-platform-proof.md). CodeQL passed after the release
  workflow switched to checking out only the trusted event SHA. The PR remains
  a draft.
- On 2026-10-06, `main` branch protection was verified in strict mode and
  updated to require exactly `JVM tests + Android assemble + Portal dry-run
  (Linux)` and `ABI + iOS Simulator tests + Markdown + spotless (macOS)`.
- Physical iOS execution is still blocked: the linked Kotlin test executable
  is unsigned and no local provisioning profile matches an available signing
  identity. Xcode-managed profile creation needs explicit Apple Developer
  account approval. The Android Native GC sweep-statistics test probe now
  passes physical positive controls and detects no swept-object delta in the
  direct speed getter/writer loops versus a primitive baseline in the debug
  test binary. This is bounded test/debug evidence, not release-optimized or
  universal zero-allocation proof; release-grade Android Native and physical
  iOS allocation evidence remain open.
- A general documentation link/spelling/markup checker is not configured in
  the repository, so those checks remain unverified. Do not publish until
  every required gate has passing evidence.
