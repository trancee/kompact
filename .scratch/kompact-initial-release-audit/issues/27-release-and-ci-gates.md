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
