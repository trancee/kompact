---
Type: task
Blocked by: 22, 23
---

## Objective

Prove cross-platform behavior and measure allocation per target before
publishing platform support or making a zero-allocation claim.

## Decisions

- Follow [the supported target matrix](04-supported-target-matrix.md) and
  [the cross-platform release gates](18-cross-platform-release-gates.md).
- PR behavioral tests cover JVM, Android JVM, and iOS Simulator. Physical iOS
  Arm64 and Android Native Arm64 tests are required before release.
- Allocation evidence uses validated per-target counters and positive
  controls, retaining exact environment metadata. A sampled estimate is not
  sufficient. Do not claim Android Native zero allocation until its proof
  method passes the validation spike.

## Acceptance criteria

- Add deterministic tests for the shared checked runtime behavior on all PR
  targets and a documented/reproducible device-test procedure for release-only
  targets.
- Validate each allocation counter/method with a positive control that
  reliably detects a known allocation; retain target, toolchain, runtime,
  workload, and measurement metadata.
- Measure the caller-owned paths from tasks 22–23 independently on every
  target before asserting a target-specific zero-allocation guarantee.
- Treat failure to establish trustworthy Android Native measurement as an
  explicit release blocker for that target's allocation claim, not as evidence
  of zero allocation.
- Keep representative performance measurements reproducible and tied to the
  exact workload and environment.

## Execution status (2026-10-05)

The caller-owned API is implemented, but this task's measurement acceptance
criteria are not yet met. The available host is Linux; `adb devices` returned
no connected devices, and `xcrun`/`xcodebuild` are unavailable. No target
allocation harness or retained measurement report has been produced, and the
Android Native Arm64 positive-control spike remains unvalidated. Do not claim
zero allocations for any target until the required evidence is retained.
