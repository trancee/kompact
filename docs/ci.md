# Run the CI checks locally

Use these commands to reproduce the checks that protect a pull request. The
workflow definitions in [`.github/workflows/`](../.github/workflows/) are the
source of truth; this page summarizes their current task lists.

CI runs on pushes to `main`, `master`, and `feat/**`, and on pull requests
targeting `main` or `master`. It uses JDK 21.

## Linux checks

The Linux job tests release-version calculation, runs the JVM/KSP/plugin
checks, links an Android Native arm64 device-test binary, and assembles
Central Portal bundles without publishing them:

```bash
bash .github/scripts/release/version-bump-test.sh
bash .github/scripts/release/release-pr-test.sh
bash .github/scripts/docs/version-docs-check-test.sh
bash .github/scripts/docs/check-version-references.sh .

./gradlew \
  spotlessCheck \
  :kompact:checkKotlinAbi \
  :kompact:jvmTest \
  :kompact:testAndroidHostTest \
  :kompact:koverVerify \
  :kompact:bundleAndroidMainAar \
  :kompact-ksp:checkKotlinAbi \
  :kompact-ksp:test \
  :kompact-ksp:koverVerify \
  :kompact-ksp-integration:test \
  :kompact-gradle-plugin:validatePlugins \
  :kompact-gradle-plugin:checkKotlinAbi \
  :kompact-gradle-plugin:koverVerify \
  :kompact-gradle-plugin:test \
  --no-daemon --rerun-tasks --no-build-cache --warning-mode all

./gradlew \
  :kompact:androidNativeArm64TestBinaries \
  --no-daemon --rerun-tasks --no-build-cache --warning-mode all

./gradlew \
  :kompact:generateChecksums :kompact:assembleCentralBundle \
  :kompact-ksp:generateChecksums :kompact-ksp:assembleCentralBundle \
  :kompact-gradle-plugin:generateChecksums :kompact-gradle-plugin:assembleCentralBundle \
  --no-daemon --console=plain
```

`jvmTest` runs the runtime's common tests on the JVM, and
`testAndroidHostTest` runs them on the Android-KMP host test target without
requiring a connected device. The KSP integration and Gradle TestKit tests
compile generated consumers and exercise the code generation plugin. Kover
enforces line and branch thresholds for the runtime,
KSP processor, and Gradle plugin; the plugin's required threshold is 100% with
no production exclusions. The Portal tasks use an ephemeral signing key in CI
and only build bundles; they do not upload or publish artifacts. The release
fixture also verifies that the managed release PR contains the stable version,
changelog, and synchronized consumer documentation.

On Linux, ABI validation checks the JVM and Android targets. The iOS klib
golden is inferred rather than compiled; use the macOS job for the authoritative
iOS check.

## macOS checks

The macOS job checks formatting and ABI baselines, runs the shared runtime
behavioral tests on iOS Simulator Arm64, then regenerates the committed API
Markdown and fails if the generated tree changes:

```bash
./gradlew \
  spotlessCheck \
  :kompact:checkKotlinAbi \
  :kompact:iosSimulatorArm64Test \
  :kompact-ksp:checkKotlinAbi \
  --no-daemon --rerun-tasks --no-build-cache --warning-mode all

./gradlew :kompact:dokkaGeneratePublicationMarkdown --no-daemon --console=plain
git diff --exit-code -- kompact/docs/api/
```

The iOS Simulator task executes the common runtime test suite against the
Kotlin/Native implementation. The iOS klib ABI check and Dokka generation run
on macOS because they analyze Apple target outputs. When KDoc changes,
regenerate the API pages with the Dokka task above; do not edit files under
`kompact/docs/api/` directly.

## Platform behavior and allocation evidence

The Linux and macOS CI checks compile or validate target artifacts where the
host toolchain permits, but they do not substitute for runtime behavior tests
on physical iOS Arm64 or Android Native Arm64 devices. The shared runtime suite
executes on both the JVM and iOS Simulator in CI; simulator execution is not
physical-device evidence.

Android-KMP host-side tests exercise runtime behavior on the Android JVM
target in CI. The separate `:kompact:androidConnectedCheck` task currently has
no configured device-test work; its success is not physical-device runtime
evidence.

The Kotlin/Native binary-link tasks are:

```bash
./gradlew :kompact:iosArm64TestBinaries
./gradlew :kompact:androidNativeArm64TestBinaries
```

CI links and uploads the Android Native debug `test.kexe` as the
`kompact-android-native-arm64-test` artifact. It uploads the
release-optimized `releaseTest/test.kexe` as
`kompact-android-native-arm64-release-test`. After downloading either artifact
from a successful Linux CI run, execute the test on a host with exactly one
authorized USB Android arm64 device:

```bash
run_id=<successful-linux-run-id>
artifact_dir="$(mktemp -d)"
gh run download "$run_id" \
  --name kompact-android-native-arm64-test \
  --dir "$artifact_dir"
scripts/test-android-native-device.sh "$artifact_dir/test.kexe"
```

The script validates the ELF architecture and device ABI, requires exactly one
ready USB device, verifies the copied binary's SHA-256, runs it with a
10-minute timeout, and removes its unique temporary directory under
`/data/local/tmp`.

On 2026-10-06, the Linux CI artifact from commit `f3bb4ae` (run
`37508992609`) ran on an Android 15 arm64 device (API 35, build
`AQ3A.240929.001`): all 387 tests in 35 test cases passed using Kotlin
`2.4.20`. The device runner ran on macOS `27.0.0` arm64 with ADB
`37.0.1-15733141`, verified artifact integrity, and confirmed cleanup. The
artifact SHA-256 was
`d32408f65a719d58ca5fd20cf3bdff2b0f6a71dec144c1d173fb957d02e9d31e`.

On the iOS Arm64 target, `test.kexe` is an unsigned standalone executable.
`scripts/test-ios-device.sh` wraps it in a minimal app bundle and signs it
with a local Apple Development identity. It installs the app on a connected
iPhone, runs it with `devicectl --console`, and requires a passing test
summary. Pass `releaseTest/test.kexe` instead to run the
release-optimized binary:

```bash
./gradlew :kompact:iosArm64TestBinaries
scripts/test-ios-device.sh \
  kompact/build/bin/iosArm64/debugTest/test.kexe \
  path/to/development.mobileprovision
```

Prerequisites:

- Use an explicit-App-ID development profile that lists the device. Create it
  once with Xcode automatic signing for any app using the same bundle ID.
- The keychain must contain exactly one matching signing identity.
- The device must be connected, paired, and in Developer Mode.
- If several connected devices appear in the profile, set
  `KOMPACT_IOS_DEVICE_UDID`.

The first launch requires trusting the developer on the device in
**Settings → General → VPN & Device Management**. The script leaves the host
installed because removing a developer's last app revokes that trust. Signing
needs an unlocked login keychain. In a plain SSH session, `codesign` can fail
with `errSecInternalComponent`; run the script in the logged-in GUI session or
use a dedicated unlocked CI keychain. Personal Team profiles expire after
seven days.

On 2026-10-07, the script ran three times on an iPhone SE (2020) with iOS
`18.7.8`. Each run of the `iosArm64` debug test binary passed all 396 tests.
A deliberately failing test made the script exit with status 1.

The local Android Native link still fails because Kotlin/Native invokes an
x86_64 `clang` toolchain (`Bad CPU type in executable`); Linux CI builds the
Android device binary instead.

Allocation measurements are not part of either CI job. AndroidX Microbenchmark
measures ART rather than the Native executable, while Perfetto heapprofd
observes sampled system-allocator calls rather than per-object allocations
served from Kotlin/Native pages. A test-only Android Native GC sweep-statistics
probe passed physical-device positive controls and measured generated speed
reads/writes against a primitive baseline in the debug and release-optimized
test executables; these results do not establish universal zero-allocation
behavior. Do not infer a zero-allocation guarantee from compilation, unit
tests, or coverage. The GC probes in `nativeTest` also run on iOS Simulator
in the macOS CI job and on physical iOS through the device script. The
allocation methodology and current proof status are
tracked in [allocation and boxing measurement](research/allocation-boxing-measurement.md).

Mutation testing is an opt-in workflow, not a CI gate. Ordinary JVM test
runs in `:kompact:jvmTest`, `:kompact-ksp:test`,
`:kompact-ksp-integration:test`, and `:kompact-gradle-plugin:test` use JUnit 6.
The Kotlin test adapter retains its historical `kotlin-test-junit5` artifact
name, but its JUnit 5 transitive dependencies are excluded and JUnit 6 is used
at runtime. The mutation setup targets `KompactRuntime`, `KompactCursor`,
`KompactCursorByteRanges`, `KompactCursorRepeats`, and `KompactByteRange` in
the runtime module, plus the changed KSP generator classes in
`:kompact-ksp`. The runtime guarded `mutationTest.jvmOnly` model
removes iOS and Android Native variants only for its explicit mutation task;
ordinary builds keep their declared targets. MutFlow 1.6.1 does not publish the
required iOS or Android Native variants, so these runs provide neither Android
nor Native mutation evidence. Run the two qualified `mutationResults` tasks
sequentially with their module-specific adapter class filters, as documented in
the [mutation-testing execution guide](../.omp/AGENT-USAGE.md).
The KSP mutation task is opt-in with
`-PmutationTest.enabled=true`; ordinary `:kompact-ksp:test` remains a JUnit 6
unit-test run.

## Run a smaller check while developing

Start with the task for the module you changed:

```bash
./gradlew :kompact:jvmTest
./gradlew :kompact-ksp:test
./gradlew :kompact-ksp-integration:test
./gradlew :kompact-gradle-plugin:test
./gradlew spotlessCheck
```

These focused runs shorten the feedback loop, but they do not replace the
host-specific CI checks before merging. For the complete gate set, use the
Linux commands above and, when available, the macOS commands.

## Release checks

The release workflow is separate from pull-request CI. When `main` contains a
development `-SNAPSHOT`, a push creates or updates a human-reviewed
`release/ongoing` PR. That PR changes the canonical Gradle candidate to its
stable release version and includes the generated changelog and synchronized
consumer-document versions. Conventional Commits group changelog entries; they
do not calculate a competing release version.

After merge, the workflow validates the reviewed merge commit, reruns Linux
quality and artifact gates, and tags that commit. Publishing to Maven Central
requires approval through the `release` environment. Only after publication
does automation open a separate PR for the next `-SNAPSHOT` and its updated
development-version references. No release workflow pushes commits directly
to protected `main`; release-version, changelog, and next-snapshot changes are
all reviewed through PRs. See [ADR-0004](adr/0004-release-pr-automation.md).

As of 2026-10-06, `main` has strict required status checks for both
`JVM tests + Android assemble + Portal dry-run (Linux)` and
`ABI + iOS Simulator tests + Markdown + spotless (macOS)`. Branch protection is
configured outside this repository; verify both contexts remain required
before release.

Before `1.0.0`, a breaking change increments the minor version. From `1.0.0`,
it increments the major version. Features increment minor, compatible fixes
increment patch, and breaking changes take precedence over features. See
[ADR-0004](adr/0004-release-pr-automation.md) for the release design and
[ADR-0007](adr/0007-pre-1-release-versioning.md) for the pre-`1.0` rule.
