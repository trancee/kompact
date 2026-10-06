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

## Execution status (2026-10-06)

The caller-owned API is implemented, but this task's device-execution and
allocation-measurement acceptance criteria are not yet met. This macOS host
has an Android 15 `arm64-v8a` device and connected iPhone 12 mini and iPhone SE
(2020) devices. `:kompact:iosArm64TestBinaries` links successfully, but it
produces a standalone `test.kexe`; there is no repository Gradle task or signed
device-test host to execute it on an iPhone. The Android Native link task
fails before producing a binary because the cached Kotlin/Native toolchain
invokes an x86_64 `clang` on this arm64 host (`Bad CPU type in executable`).

No physical-device behavior test, per-target allocation harness, positive
control, or retained measurement report has been produced. Android Native
device execution and its allocation-counter positive-control spike remain
unvalidated. Do not claim zero allocations for any target until the required
per-target evidence is retained.
