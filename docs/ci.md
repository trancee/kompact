# Run the CI checks locally

Use these commands to reproduce the checks that protect a pull request. The
workflow files in [`.github/workflows/`](../.github/workflows/) are the source
of truth. This page lists the tasks they run today.

The `CI` workflow ([`ci.yml`](../.github/workflows/ci.yml)) runs on pushes to
`main`, `master`, and `feat/**`, and on pull requests that target `main` or
`master`. A newer push to the same pull request cancels the older run. The
jobs run in parallel, and each Gradle job uses Temurin JDK 21.

| Job | Runner | Check name |
|---|---|---|
| `docs` | `ubuntu-latest` | `Docs + release scripts + tool fixtures (Linux)` |
| `jvm-test` | `ubuntu-latest` | `JVM tests + Android assemble + Portal dry-run (Linux)` |
| `gradle-plugin-test` | `ubuntu-latest` | `Gradle plugin KMP consumer + coverage (Linux)` |
| `android-native` | `ubuntu-latest` | `Android Native device-test binaries (Linux)` |
| `jvm-benchmarks` | `ubuntu-latest` | `JVM benchmark fixtures + smoke results (Linux)` |
| `mutation-test` | `ubuntu-latest` | `Mutation testing (kompact)` and `Mutation testing (kompact-ksp)` |
| `api-check` | `macos-latest` | `ABI + iOS Simulator tests + Markdown + spotless (macOS)` |
| `native-benchmarks` | `macos-latest` | `Native benchmark fixtures + smoke results (macOS)` |
| `ios-holders` | `macos-latest` | `Generated-holder fixtures on iOS Simulator (macOS)` |

Branch protection on `main` requires the `jvm-test` and `api-check` checks.

Every job starts from a clean checkout and runs its Gradle steps on one
daemon. A later step reuses the task outputs of an earlier step in the same
job. Every Gradle command passes `--no-build-cache`, so no task output comes
from an earlier run.

## Linux checks

The `docs` job tests the release scripts, the device-runner tools, and the
benchmark report tools, and it checks the Markdown. It needs no JDK:

```bash
bash .github/scripts/release/version-bump-test.sh
bash .github/scripts/release/release-pr-test.sh
bash .github/scripts/docs/version-docs-check-test.sh
bash .github/scripts/docs/check-version-references.sh .
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 -m unittest discover -s benchmarks -p 'test_*.py'
lychee --offline --no-progress --include-fragments README.md SECURITY.md CONTRIBUTING.md CHANGELOG.md docs
codespell --count --ignore-words-list=OptIn README.md SECURITY.md CONTRIBUTING.md CHANGELOG.md docs
npm exec --yes --package=markdownlint-cli2@0.23.2 -- markdownlint-cli2 --config .markdownlint-cli2.jsonc README.md SECURITY.md CONTRIBUTING.md 'docs/**/*.md'
```

CI runs Lychee, Codespell, and Markdownlint through pinned GitHub Actions.
The `lychee`, `codespell`, and `npm exec` lines above run the same checks
when you have the tools installed.

The `jvm-test` job runs the JVM, KSP, and Gradle plugin checks, and then
builds Central Portal bundles without publishing them:

```bash
./gradlew \
  spotlessCheck \
  detekt \
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
  :build-logic:test \
  --no-build-cache --warning-mode all

./gradlew \
  :kompact:generateChecksums :kompact:assembleCentralBundle \
  :kompact-ksp:generateChecksums :kompact-ksp:assembleCentralBundle \
  :kompact-gradle-plugin:generateChecksums :kompact-gradle-plugin:assembleCentralBundle \
  --no-build-cache --console=plain
```

The `gradle-plugin-test`, `android-native`, and `jvm-benchmarks` jobs run
these commands. The `android-native` job uploads the debug and release
Android Native arm64 test binaries as artifacts and keeps them for 7 days:

```bash
./gradlew :kompact-gradle-plugin:test :kompact-gradle-plugin:koverVerify --no-build-cache --warning-mode all

./gradlew :kompact:androidNativeArm64TestBinaries --no-build-cache --warning-mode all

./gradlew :benchmarks:jvmTest :benchmarks:jvmSmokeBenchmark --no-build-cache --console=plain
python3 benchmarks/check_results.py --profile smoke benchmarks/build/reports/benchmarks/smoke/*/jvm.json
./gradlew -p benchmarks/holders jvmTest jvmSmokeBenchmark --no-build-cache --console=plain
python3 benchmarks/check_results.py --profile holders-smoke benchmarks/holders/build/reports/benchmarks/smoke/*/jvm.json
```

To reproduce a clean CI run on a machine that has built the project
before, add `--rerun-tasks` to each Gradle command.

### What the Gradle tasks check

`:kompact:jvmTest` runs the runtime's common tests on the JVM.
`:kompact:testAndroidHostTest` runs them on the Android-KMP host test target,
which needs no connected device. `:kompact-ksp-integration:test` compiles
generated consumers. `:kompact-gradle-plugin:test` is a Gradle TestKit suite
that compiles a consumer for JVM, Android, iOS, and Android Native. CI runs
it in its own job, together with the plugin's `koverVerify`, so that its
nested builds do not overlap the compiler daemons of the root build.
`koverVerify` depends on the module's tests, so the job runs the suite once.

`koverVerify` enforces line and branch thresholds for the runtime, the KSP
processor, and the Gradle plugin. The Gradle plugin requires 100% coverage
with no production exclusions.

The Portal tasks sign with a throwaway PGP key that the job generates. They
build the bundles `kompact/build/kompact-portal-bundle.zip`,
`kompact-ksp/build/kompact-ksp-portal-bundle.zip`, and
`kompact-gradle-plugin/build/kompact-gradle-plugin-portal-bundle.zip`, and
the job fails if one is missing. They do not upload or publish anything.

### Release scripts and version references

The release fixtures also check that the managed release PR contains the
stable version, the changelog, and the synchronized consumer documentation.

The version checker and the release synchronizer share one Python
implementation. It tells published references apart from development
references, even when both use stable-looking versions or share a wrapped
paragraph. Product coordinates under a development or snapshot heading
follow the checkout version. Other product coordinates follow the latest
release in `CHANGELOG.md`. To repair drift, run
`python3 .github/scripts/docs/sync-version-references.py .`. To report drift
without changing files, run
`bash .github/scripts/docs/check-version-references.sh .`.

The check also fails on a paragraph that names a Kompact version and says
"not published", "not on Maven Central", "unreleased", or "differs". A release
rewrites that version, so the sentence turns false in the tagged docs. Write
"needs version `0.10.0` or later" or "Earlier releases lack ..." instead.

The fixture tests cover release-to-snapshot transitions, idempotency,
preservation of unrelated tool versions, and the release-state claims.

### Device-runner tools and benchmarks

The internal device-runner Python tools are outside production Kover. Their
fixtures need no hardware. They test watchdog-safe launch, exact process
selection, and asynchronous debugger continuation, and they need no Apple
tools, signing assets, or connected devices.

The unpublished `benchmarks` module compiles the maintained runtime sources
for host measurements. It does not change the published target set. The
benchmark fixtures and report tools are outside production coverage. CI runs
their tests, and `benchmarks/check_results.py` rejects incomplete smoke
reports and reports with non-finite values.

The standalone `benchmarks/holders` consumer is also unpublished and outside
production coverage. It applies the real Gradle plugin and runtime, so it
tests the generated holder decoders instead of copies. It also runs a JVM
timing smoke check.

Shared CI does not enforce timing thresholds, because its machines differ
from run to run. For full reports on one controlled host, the optional
`--baseline` comparison enforces the 10% timing budget. See
[host measurements and comparison budgets](how-to/benchmark-runtime.md).

### Markdown checks

Lychee checks relative Markdown links and their heading anchors in offline
mode, and Codespell checks common spelling errors. CI pins the Lychee action
and binary versions and the Codespell action revision. Markdownlint checks
`README.md`, `SECURITY.md`, `CONTRIBUTING.md`, and every Markdown file under
`docs/`, but not `CHANGELOG.md`. The Markdownlint configuration keeps the default
rules, sets a 160-character limit for prose lines, and disables only
table-column alignment. Codespell allows `OptIn` because it is Kotlin
annotation syntax in the documentation examples.

### ABI checks on Linux

On Linux, `checkKotlinAbi` compiles and checks the JVM and Android targets.
It infers the iOS klib golden instead of compiling it. The macOS job runs the
authoritative iOS check.

## macOS checks

The `api-check` job installs the Android SDK `platform-tools` package. It
checks formatting and the ABI goldens and runs the shared runtime tests on
iOS Simulator Arm64. Then it regenerates the committed API Markdown and
fails if the generated tree changed:

```bash
./gradlew \
  spotlessCheck \
  :kompact:checkKotlinAbi \
  :kompact:iosSimulatorArm64Test \
  :kompact-ksp:checkKotlinAbi \
  --no-build-cache --warning-mode all

./gradlew :kompact:dokkaGeneratePublicationMarkdown --no-build-cache --console=plain
git diff --exit-code -- kompact/docs/api/
```

The `native-benchmarks` job runs the benchmark fixtures on macOS Arm64, and
the `ios-holders` job runs the generated-holder fixtures on iOS Simulator:

```bash
./gradlew :benchmarks:macosArm64Test :benchmarks:macosArm64SmokeBenchmark --no-build-cache --console=plain
python3 benchmarks/check_results.py --profile smoke benchmarks/build/reports/benchmarks/smoke/*/macosArm64.json

./gradlew -p benchmarks/holders iosSimulatorArm64Test --no-build-cache --console=plain
```

`:kompact:iosSimulatorArm64Test` runs the common runtime test suite against
the Kotlin/Native implementation. The `benchmarks/holders` simulator test
checks the generated iOS code, not device timing. The iOS klib ABI check and
the Dokka generation run on macOS because they analyze Apple target outputs.
When KDoc changes, regenerate the API pages with the Dokka task above. Do not
edit files under `kompact/docs/api/` by hand.

## Mutation testing

The `mutation-test` job runs MutFlow against the whole runtime and KSP
processor packages, one matrix job per module. A job fails if any mutant
survives or the run has execution gaps. Each command must be the only task
in its Gradle invocation:

```bash
./gradlew -PmutationTest.jvmOnly=true :kompact:mutationResults --no-build-cache --no-configuration-cache --console=plain
./gradlew -PmutationTest.enabled=true :kompact-ksp:mutationResults --no-build-cache --no-configuration-cache --console=plain
```

The `mutationTest.jvmOnly` property removes the runtime's iOS and Android
Native targets, but only for the mutation task, because MutFlow 1.7.0
publishes no Native variants. The `mutationTest.enabled` property applies
MutFlow to the KSP processor, also only for the mutation task. An ordinary
`:kompact-ksp:test` run stays a plain JUnit 6 run. The evidence covers the
JVM only. MutFlow does not support the configuration cache, so both commands
pass `--no-configuration-cache`. CI also passes `--no-build-cache`, because
the restored Gradle cache can hold an earlier `mutflowJvmTest` result.

The two matrix jobs run independently, so a runtime failure does not stop
the KSP run. Each command writes `build/reports/mutation-results.md` and
`build/reports/mutation-results.json` in its module. The `mutationResults`
task adds the Markdown summary to the job page through
`GITHUB_STEP_SUMMARY`. Each job uploads its report files as the
`mutation-results-kompact` or `mutation-results-kompact-ksp` artifact and
keeps it for 14 days. See the
[mutation testing disposition](mutation-testing-disposition.md) for the
current results and [`.omp/AGENT-USAGE.md`](../.omp/AGENT-USAGE.md) for the
workflow.

## Platform behavior and allocation evidence

The Linux and macOS jobs compile or check target artifacts where the host
toolchain allows it. They do not replace runtime tests on physical iOS Arm64
or Android Native Arm64 devices. CI runs the shared runtime suite on the JVM
and on iOS Simulator. A simulator run is not physical-device evidence.

The [post-PR-99 validation record](research/physical-runtime-validation.md)
keeps six passing Android debug and release runs at merged `ed7218c`. It also
keeps six runs of fresh local builds of the aligned-prefix follow-up on base
`6115b87`. Each run executed 404 tests. The
[physical iOS follow-up](research/physical-runtime-validation.md#physical-ios-follow-up)
adds three debug and three release runs at merged `465cb86`, also with 404
tests per run. The older device results below do not validate the current
revision.

The Android-KMP host tests run runtime behavior on the Android JVM target in
CI. The separate `:kompact:androidConnectedCheck` task has no device-test work
configured, because the `kompact` Android target enables only host tests. A
passing `androidConnectedCheck` is not physical-device evidence.

### Link the Kotlin/Native test binaries

These tasks link the device test binaries:

```bash
./gradlew :kompact:iosArm64TestBinaries
./gradlew :kompact:androidNativeArm64TestBinaries
```

Each task links a debug binary at `debugTest/test.kexe` and a
release-optimized binary at `releaseTest/test.kexe` under
`kompact/build/bin/<target>/`. The release binaries carry the allocation
probes into optimized code for device evidence.

### Run the Android Native tests on a device

CI uploads the Android Native debug `test.kexe` as the
`kompact-android-native-arm64-test` artifact. It uploads the
release-optimized `releaseTest/test.kexe` as
`kompact-android-native-arm64-release-test`. Both artifacts expire after 7
days. Download either artifact from a successful Linux CI run. Then run the
test on a host that has exactly one authorized USB Android arm64 device:

```bash
run_id=<successful-linux-run-id>
artifact_dir="$(mktemp -d)"
gh run download "$run_id" \
  --name kompact-android-native-arm64-test \
  --dir "$artifact_dir"
scripts/test-android-native-device.sh "$artifact_dir/test.kexe"
```

The script checks the ELF architecture and the device ABI, and it requires
exactly one ready USB device. It verifies the SHA-256 of the copied binary,
runs it with a 10-minute timeout, and removes its own temporary directory
under `/data/local/tmp`.

On 2026-10-06, the Linux CI artifact from commit `f3bb4ae` (run
`37508992609`) ran on an Android 15 arm64 device (API 35, build
`AQ3A.240929.001`). All 387 tests in 35 test cases passed with Kotlin
`2.4.20`. The device runner ran on macOS `27.0.0` arm64 with ADB
`37.0.1-15733141`. It verified the artifact integrity and confirmed the
cleanup. The artifact SHA-256 was
`d32408f65a719d58ca5fd20cf3bdff2b0f6a71dec144c1d173fb957d02e9d31e`.

On 2026-10-17, fresh local Android Native debug and release linking
succeeded with JDK `21.0.12.1` and Kotlin `2.4.21`. The six physical runs
recorded above followed. The earlier local `Bad CPU type in executable`
failure no longer blocks local linking. Linux CI stays the authoritative
clean build.

### Run the iOS tests on a device

On the iOS Arm64 target, `test.kexe` is an unsigned standalone executable.
`scripts/test-ios-device.sh` wraps it in a minimal app bundle and signs it
with a local Apple Development identity. It installs the app on a connected
iPhone. Then `scripts/ios_device_tests.py` starts the app suspended with
`devicectl --console`, attaches LLDB to the exact new PID, and resumes the
initial debugger stop. Because the debugger holds the process, the iOS
app-launch watchdog does not stop the standalone test executable. The run
passes only if the debugger exits successfully, the console exits
successfully, and the test summary passes.

To run the debug binary, link it and pass it with a provisioning profile:

```bash
./gradlew :kompact:iosArm64TestBinaries
scripts/test-ios-device.sh \
  kompact/build/bin/iosArm64/debugTest/test.kexe \
  path/to/development.mobileprovision
```

To run the release-optimized binary, pass `releaseTest/test.kexe` instead.

The script needs the following setup:

- A development profile with an explicit App ID that lists the device. Create
  it once with Xcode automatic signing for any app that uses the same bundle
  ID.
- Exactly one matching signing identity in the keychain.
- Full Xcode with `devicectl`, LLDB device commands, and Python scripting,
  plus Python 3 on the host. The current physical runs used Xcode `27.1`.
- A device that is connected, paired, and in Developer Mode.
- If the profile lists several connected devices, the
  `KOMPACT_IOS_DEVICE_UDID` environment variable set to the target device.

On the first launch, trust the developer on the device in
**Settings → General → VPN & Device Management**. The script leaves the host
app installed, because removing the last app of a developer revokes that
trust. Signing needs an unlocked login keychain. In a plain SSH session,
`codesign` can fail with `errSecInternalComponent`. Run the script in the
logged-in GUI session, or use a dedicated unlocked CI keychain. Personal Team
profiles expire after seven days.

The helper waits for the initial asynchronous `SIGSTOP`. It does not resume
later, unexpected stops. The run fails on an ambiguous or missing process, an
unsuccessful exit, or a timeout. The helper stops its local console and
debugger processes, and the shell wrapper removes the temporary app bundle.
The script does not print raw CoreDevice and debugger diagnostics, because
they contain private identifiers. A generic launch-security error does not
prove that developer trust is the cause. Check signing, entitlements, and
device readiness as well.

On 2026-10-07, the script ran three times on an iPhone SE (2020) with iOS
`18.7.8`. Each run of the `iosArm64` debug test binary passed all 396 tests.
A deliberately failing test made the script exit with status 1.

### Allocation evidence

JMH allocation profiling and physical-device allocation measurements are not
CI gates. The macOS benchmark fixture test includes a controlled debug Native
probe that counts GC sweeps during repeat reads. AndroidX Microbenchmark
measures ART, not the Native executable. Perfetto heapprofd samples
system-allocator calls, not the per-object allocations that Kotlin/Native
serves from its own pages.

A test-only Android Native probe reads GC sweep statistics. It passed its
positive controls on a physical device. It measured generated speed reads and
writes against a baseline that uses raw `Int` values, in both the debug and
the release-optimized test executables. These results do not establish
zero-allocation behavior in general. Compilation, unit tests, and coverage do
not prove a zero-allocation guarantee either. The GC probes in `nativeTest`
also run on iOS Simulator in the macOS job and on physical iOS through the
device script. The
[allocation and boxing measurement](research/allocation-boxing-measurement.md)
note tracks the method and the current proof status.

### JUnit version

Ordinary JVM test runs in `:kompact:jvmTest`, `:kompact-ksp:test`,
`:kompact-ksp-integration:test`, and `:kompact-gradle-plugin:test` use JUnit 6.
The Kotlin test adapter keeps its historical `kotlin-test-junit5` artifact
name. The build excludes its JUnit 5 transitive dependencies and uses JUnit 6
at runtime.

## Run a smaller check while developing

Start with the task for the module you changed:

```bash
./gradlew :kompact:jvmTest
./gradlew :kompact-ksp:test
./gradlew :kompact-ksp-integration:test
./gradlew :kompact-gradle-plugin:test
./gradlew spotlessCheck detekt
```

These focused runs give faster feedback, but they do not replace the
host-specific CI checks before you merge. For the complete set of checks, run
the Linux commands above and, if you have a Mac, the macOS commands.

## Regenerate the ABI goldens

The `Regen Goldens` workflow
([`regen-goldens.yml`](../.github/workflows/regen-goldens.yml)) runs only when
you start it by hand (`workflow_dispatch`). Only a macOS host can produce
`kompact/api/kompact.klib.api`, so contributors without a Mac use this
workflow. It runs `./gradlew <args> --no-daemon` on `macos-latest`, where
`<args>` defaults to `:kompact:updateKotlinAbi`. It uploads `kompact/api/` as
the `api-goldens` artifact for 3 days. Download the artifact and commit the
changed goldens yourself. The workflow does not commit anything.

## Release checks

The release workflows are separate from pull-request CI.

The `Release PR` workflow
([`release-pr.yml`](../.github/workflows/release-pr.yml)) runs on every push
to `main`. When `main` holds a development `-SNAPSHOT` version, it creates or
updates a human-reviewed PR from `release/ongoing`. That PR changes the
Gradle version to the stable release version. It also contains the generated
changelog and the synchronized consumer-document versions. Conventional
Commits group the changelog entries. They do not calculate a competing
release version.

The `Release Publish` workflow
([`release-publish.yml`](../.github/workflows/release-publish.yml)) runs when
a PR with the `release` label merges into `main`. It has four jobs:

1. `prepare` checks that the workflow runs on the merge commit and that the
   merged version is stable. It reruns the release script tests and the
   version-reference check. It reruns the Linux Gradle checks except
   `detekt` and `:build-logic:test`, runs `:kompact-gradle-plugin:test`
   separately, and builds the Portal bundles with a throwaway key. Then it
   tags the merge commit as `v<version>`.
2. `deploy` runs in the `release` environment, which needs approval. It
   builds and signs the bundles from the tag with the real signing key,
   uploads them to the Central Portal, waits for validation, and publishes
   them to Maven Central.
3. `github-release` runs only after a successful publication. It creates the
   GitHub release for the tag, or updates it on a rerun. The release notes
   are the `CHANGELOG.md` section for the version, printed by
   `version-bump.sh release-notes <version>`.
4. `advance-snapshot` runs only after a successful publication. It opens or
   updates a PR on `release/next-snapshot-<version>` that sets the next
   `-SNAPSHOT` version and updates the development-version references.

No release workflow pushes commits to protected `main`. The release version,
the changelog, and the next snapshot all go through reviewed PRs. See
[ADR-0004](adr/0004-release-pr-automation.md).

On 2026-10-18, branch protection on `main` required strict status checks for
`JVM tests + Android assemble + Portal dry-run (Linux)` and
`ABI + iOS Simulator tests + Markdown + spotless (macOS)`. It did not require
the other CI jobs. Branch protection lives outside this repository. Before a
release, check which contexts are still required.

Before `1.0.0`, a breaking change increments the minor version. From `1.0.0`,
it increments the major version. Features increment the minor version, and
compatible fixes increment the patch version. A breaking change takes
precedence over a feature. See
[ADR-0004](adr/0004-release-pr-automation.md) for the release design and
[ADR-0007](adr/0007-pre-1-release-versioning.md) for the rule before `1.0`.
