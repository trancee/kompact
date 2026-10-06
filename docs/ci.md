# Run the CI checks locally

Use these commands to reproduce the checks that protect a pull request. The
workflow definitions in [`.github/workflows/`](../.github/workflows/) are the
source of truth; this page summarizes their current task lists.

CI runs on pushes to `main`, `master`, and `feat/**`, and on pull requests
targeting `main` or `master`. It uses JDK 21.

## Linux checks

The Linux job tests release-version calculation, runs the JVM/KSP/plugin
checks, and assembles Central Portal bundles without publishing them:

```bash
bash .github/scripts/release/version-bump-test.sh
bash .github/scripts/release/release-pr-test.sh
bash .github/scripts/docs/version-docs-check-test.sh
bash .github/scripts/docs/check-version-references.sh .

./gradlew \
  spotlessCheck \
  :kompact:checkKotlinAbi \
  :kompact:jvmTest \
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
  :kompact:generateChecksums :kompact:assembleCentralBundle \
  :kompact-ksp:generateChecksums :kompact-ksp:assembleCentralBundle \
  :kompact-gradle-plugin:generateChecksums :kompact-gradle-plugin:assembleCentralBundle \
  --no-daemon --console=plain
```

`jvmTest` runs the runtime's common tests on the JVM. The KSP integration and
Gradle TestKit tests compile generated consumers and exercise the code
generation plugin. Kover enforces line and branch thresholds for the runtime,
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

The Android JVM target is currently compiled and bundled on Linux, but CI does
not yet run its behavioral tests on an Android host. The local
`:kompact:androidConnectedCheck` task currently has no configured Android
device-test work, so its success is not Android runtime evidence.

The Kotlin/Native binary-link tasks are:

```bash
./gradlew :kompact:iosArm64TestBinaries
./gradlew :kompact:androidNativeArm64TestBinaries
```

These tasks build test binaries, not device test runs. The repository does not
yet provide a signed iOS device-test host or an Android Native device runner.
On the inspected Apple Silicon Mac, iOS Arm64 test binaries linked, but the
Android Native link failed because Kotlin/Native invoked an x86_64 `clang`
toolchain (`Bad CPU type in executable`). Consequently, no physical-device
behavior result is available yet. Both device executions remain mandatory
release blockers until an execution procedure is implemented and the tests
are run on the target hardware.

Allocation measurements are not part of either CI job. Do not infer a
zero-allocation guarantee from compilation, unit tests, or coverage. The
allocation methodology and current proof status are tracked in
[allocation and boxing measurement](research/allocation-boxing-measurement.md);
the proof gate remains open until each claimed target has a validated
allocation counter, positive control, and retained environment metadata.

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

The last inspected external branch-protection configuration enabled strict
status checks but required only the `CI` context. That configuration is outside
this repository and has not been changed; maintainers should verify that the
macOS and Linux CI jobs are both required before release.

Before `1.0.0`, a breaking change increments the minor version. From `1.0.0`,
it increments the major version. Features increment minor, compatible fixes
increment patch, and breaking changes take precedence over features. See
[ADR-0004](adr/0004-release-pr-automation.md) for the release design and
[ADR-0007](adr/0007-pre-1-release-versioning.md) for the pre-`1.0` rule.
