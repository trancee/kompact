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

## Latest verification (2026-10-07)

- PR #89 (`feat/release-readiness-gates`) was merged at
  `2506dfdb41165b0645878d46098529df247576cb`. Its required Linux and macOS
  checks passed on head `472b3b502293a1be78de99618c41cea68ed4c152`; CodeQL
  also passed. The Linux checks include the release-version and documentation
  fixtures, plugin ABI/coverage/TestKit gates, Android host tests, and
  Central Portal bundle dry-runs. The macOS checks include iOS Simulator tests,
  ABI validation, Dokka Markdown generation, and formatting.
- `main` branch protection is strict and requires exactly
  `JVM tests + Android assemble + Portal dry-run (Linux)` and
  `ABI + iOS Simulator tests + Markdown + spotless (macOS)`.
- Physical iOS Arm64 and Android Native Arm64 behavior plus debug/release
  allocation-probe evidence is recorded in [platform proof](25-platform-proof.md).
  Both targets passed their release test binaries three times with positive
  allocation controls; these bounded GC statistics do not constitute a
  universal zero-allocation guarantee or an independent allocation trace.
- Consumer versions and the application-owned envelope example are recorded
  in [consumer docs](26-consumer-docs-and-version-drift.md). The automated
  version-reference, offline relative-link, spelling, and maintained-guide
  markup checks are configured in this change's Linux CI job. The envelope
  example is executable common-test code.
- On this branch, `:kompact:jvmTest`, `:kompact:testAndroidHostTest`, and
  `:kompact:iosSimulatorArm64Test` passed. Lychee reported zero broken local
  links, Codespell reported zero spelling issues, Markdownlint reported zero
  issues across 13 maintained guides, and `actionlint` passed for the workflow.
- The first supported `0.8.0` release has not been prepared or published.
  Per [ADR-0004](../../../docs/adr/0004-release-pr-automation.md), the stable
  version, changelog, and consumer docs must be reviewed together in the
  managed release PR; publication remains a separate manually approved step.
  Do not publish until all release-specific gates pass.
