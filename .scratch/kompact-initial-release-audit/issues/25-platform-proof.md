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
Native `test.kexe` for commit `f3bb4aeeacd325905c8589e3a856a88599758090`
(run `37508992609`). The artifact SHA-256 was
`d32408f65a719d58ca5fd20cf3bdff2b0f6a71dec144c1d173fb957d02e9d31e`.
`scripts/test-android-native-device.sh` verified the ELF and transfer
checksum, then executed all 387 tests in 35 test cases successfully on the
connected Android 15 `arm64-v8a` device (model `A063`, API 35, build
`AQ3A.240929.001`). The artifact was built with Kotlin `2.4.20`; execution
used macOS `27.0.0` arm64 and ADB `37.0.1-15733141`. The runner reported
successful removal of its unique temporary device directory. Its documented
download-and-run procedure now provides reproducible Android Native behavior
evidence.

Physical iOS behavior remains unverified. `:kompact:iosArm64TestBinaries`
links successfully but produces an unsigned standalone `test.kexe`, not an
installable app. The connected iPhones are ready, but neither local
provisioning profile contains a developer certificate matching an available
signing identity; the profile that includes a connected device cannot sign a
test host with the available identity. Using Xcode-managed provisioning would
require approval before making changes to the Apple Developer account. No
signed iOS device-test host or execution procedure has been validated.

The local Android Native link still fails at
`linkDebugTestAndroidNativeArm64` because the cached Kotlin/Native toolchain
invokes an x86_64 `clang` on this arm64 Mac (`Bad CPU type in executable`).
Linux CI builds the Android test binary and avoids that host limitation.

The Linux CI artifact for commit `a948f1a2f79d61a08e6869e6229ca62bf35df2d7`
(run `37519625779`, SHA-256
`c1e84a6f1e326b5686db9ec5352f78c51bd7ac6c8411cd1ec9dffac6d959c723`) was run
twice on the Android 15 arm64 device (model `A063`, API 35, build
`AQ3A.240929.001`). Both runs passed all 390 tests in 36 test cases and
reported `keptCount=6031`, `sweptCount=2288` for the known-object positive
control. In each run, three samples of 4,096 direct generated speed reads and
three samples of 4,096 direct generated speed writes each reported zero
swept-object delta, matching a zero-object primitive baseline; the
4,096-instance allocation control reported 4,098 swept objects in every
sample. The device runner verified the artifact checksum and removed its
temporary files.

This is bounded evidence for the direct getter/writer loops in the debug test
binary. Kotlin/Native GC statistics are testing/debugging data, so this does
not establish release-optimized behavior, other call shapes, a timing budget,
or a universal zero-allocation guarantee. The Android Native release-grade
allocation claim remains open. Physical iOS behavior and allocation evidence
also remain open; the unsigned iOS test executable still requires a usable
signing profile, and changing Apple Developer account provisioning remains
deferred.
