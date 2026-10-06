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

The caller-owned API is implemented. Linux CI linked and uploaded the Android
Native `test.kexe` for commit `856284ce4aa6261ebdd1a1d25975cbf5febcd615`
(run `37507658565`). The artifact SHA-256 was
`d32408f65a719d58ca5fd20cf3bdff2b0f6a71dec144c1d173fb957d02e9d31e`.
`scripts/test-android-native-device.sh` verified the ELF and transfer
checksum, then executed all 387 tests in 35 test cases successfully on the
connected Android 15 `arm64-v8a` device (model `A063`, API 35, build
`AQ3A.240929.001`). The runner reported successful removal of its unique
temporary device directory. Its documented download-and-run procedure now
provides reproducible Android Native behavior evidence.

Physical iOS behavior remains unverified. `:kompact:iosArm64TestBinaries`
links successfully but produces a standalone `test.kexe`; there is no
repository Gradle device-test task or signed iOS test host to execute it on an
iPhone. The local Android Native link still fails at
`linkDebugTestAndroidNativeArm64` because the cached Kotlin/Native toolchain
invokes an x86_64 `clang` on this arm64 Mac (`Bad CPU type in executable`).
Linux CI builds the Android test binary and avoids that host limitation.

No per-target allocation harness, validated counter, positive control, or
retained allocation measurement report has been produced. Android Native
allocation measurement and iOS device behavior/allocation evidence remain
open release blockers. Do not claim zero allocations for any target until
the required per-target evidence is retained.
