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

The Linux CI artifact for commit `ef97610b39caf6380210c288dcce190d49abcb14`
(run `37596537995`, SHA-256
`f84d76040d63a4410bd393fbc3a68f2e9ea58f96df0ccf8bd2d616ba087b9c32`) was run
three times on the Android 15 arm64 device (model `A063`, API 35, build
`AQ3A.240929.001`). All three runs passed 390 tests in 36 test cases. Each
reported three samples of 4,096 direct generated speed reads and writes with
zero swept objects, exactly matching the zero-object primitive baseline; the
4,096-instance intentional-allocation control reported 4,098 swept objects
for every operation sample. The known-object retention/release probe reported
`keptCount=6031`, `sweptCount=2288`. The device runner verified the artifact
checksum and removed its temporary files. The test now requires each measured
maximum to be no greater than the observed primitive baseline; it no longer
allows a percentage of the positive-control count as a margin.

An Android Native caller-owned cursor probe then found one swept object per
`writeUnsigned(ULong)` operation. Isolation traced this to the narrow-width
unsigned right-shift used in both cursor validation and writing; the first
inline-validation attempt did not fix it. Commit `5989ac2` changed the range
check to use `value.toLong() ushr bitWidth`. Its CI artifact (run
`37612656077`, SHA-256
`7d26ba4543dddbead1b72e77e761c59cd8bfb672d79f870bfa295f0426139551`) passed
all 396 tests on three consecutive runs on the same Android 15 arm64 device.
Each run reported `[0, 0, 0]` for 4,096 unsigned validations and cursor byte
writes, matching the primitive baselines, while intentional-allocation
controls reported `[4098, 4098, 4098]`. The checksum was verified and device
temporary files were removed each run.

These are bounded results for the measured operations in the debug test
binary. Kotlin/Native GC statistics are testing/debugging data, so they do not
establish release-optimized behavior, other call shapes, a timing budget, or a
universal zero-allocation guarantee. The Android Native release-grade
allocation claim remains open. Physical iOS behavior and allocation evidence
also remain open; the unsigned iOS test executable still requires a usable
signing profile, and changing Apple Developer account provisioning remains
deferred.
