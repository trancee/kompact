# Physical runtime validation records

## Scope and result

On 2026-10-17, the runtime merged by
[#99](https://github.com/trancee/kompact/pull/99), commit `ed7218c`, passed
six physical Android Native arm64 executions. The aligned-prefix follow-up
also passed six Android executions. On 2026-10-18, merged `465cb86` passed
six physical iOS executions after renewal of the dedicated test-host profile
and correction of the runner's launch-watchdog failure. An unrelated app's
profile was never reused: the runner installs the bundle selected by its
profile and could replace that app.

These are runtime correctness and bounded Native allocation-probe results,
not physical-device timing benchmarks or an Instruments allocation trace.

## Android evidence

The test binaries came from successful
[CI run 39061939916](https://github.com/trancee/kompact/actions/runs/39061939916),
revision `5eb6832`. The runtime/test and device-runner trees were verified
identical to merged `ed7218c`:

```bash
git diff 5eb6832 ed7218c --exit-code -- kompact scripts
```

The device was Android model `A063`, Android `15`, API `35`, build
`AQ3A.240929.001`, arm64. The host used macOS `27.0.1` and ADB
`37.0.1-15733141`. No serial number, personal device name, or signing
identity is retained.

| Configuration | Artifact | Physical executions | Tests per execution | Evidence |
| --- | --- | ---: | ---: | --- |
| Debug | `kompact-android-native-arm64-test` | 3 | 403 passed | [redacted log](../../benchmarks/results/physical-runtime-2026-10-17/android-debug.txt) |
| Release | `kompact-android-native-arm64-release-test` | 3 | 403 passed | [redacted log](../../benchmarks/results/physical-runtime-2026-10-17/android-release.txt) |

Each execution used:

```bash
scripts/test-android-native-device.sh <downloaded-artifact>/test.kexe
```

The runner verified local/on-device SHA-256, ran the full shared Native
suite, and confirmed device temporary-directory cleanup. The retained logs
explicitly include both fixed-repeat checkpoint regressions: exact checkpoint
and unused-storage preservation across widths/boundaries, and failure
atomicity after one-bit truncation.

The debug binary SHA-256 is
`71f1d11efe65a68e22f74eedf86292dca1fbb3a381bb88593fea5cf60913456d`.
The release binary SHA-256 is
`ba59813b8d92cf66b27d736a57a558e25d5c7436c1334fc1585846fc7f8383ba`.

For every run, three samples of generated scalar speed reads/writes, cursor
byte reads/writes, and unsigned validation reported `[0, 0, 0]` swept objects,
matching primitive baselines. Each intentional-allocation control reported
`[4098, 4098, 4098]`. The tests exercise 4,096 operations per sample.
This is a GC sweep-statistics signal for those call shapes, not an exact
per-operation allocation counter. The physical probes do not measure repeat
indexing or complete framed-holder decoding allocations.

## Aligned-prefix follow-up

The subsequent [prefix-reader correction](aligned-count-prefix-benchmark.md)
on base `6115b87` was validated with freshly linked local binaries, not the
older CI artifacts above. The retained logs identify the exact uncommitted
runtime source and binary SHA-256 values:

| Configuration | Physical executions | Tests per execution | Evidence |
| --- | ---: | ---: | --- |
| Debug | 3 | 404 passed | [redacted log](../../benchmarks/results/physical-prefix-2026-10-17/android-debug.txt) |
| Release | 3 | 404 passed | [redacted log](../../benchmarks/results/physical-prefix-2026-10-17/android-release.txt) |

Both configurations linked locally using JDK `21.0.12.1`, Kotlin `2.4.21`,
and the existing `:kompact:androidNativeArm64TestBinaries` task, forced with
`--rerun-tasks --no-build-cache`. Each binary then ran three times through
`scripts/test-android-native-device.sh` on the same `A063` Android 15/API 35
test device. Integrity verification and temporary-file cleanup succeeded
in all six executions.

All three checkpoint regressions passed, including the new per-byte unsigned
count/capacity diagnostic test. The five scalar/cursor allocation probes
again reported zero swept objects in every primitive/read/write sample
and `[4098, 4098, 4098]` in every intentional-allocation control.
These probes still do not measure complete holder/repeat-index allocations
or establish device timing guarantees.

## iOS provisioning history

After PR 99, fresh debug and release `iosArm64` binaries linked successfully with Kotlin
`2.4.21` and Xcode `27.0` (`27A266a`):

```bash
./gradlew :kompact:iosArm64TestBinaries \
  --no-daemon --rerun-tasks --no-build-cache --console=plain
```

Those historical binaries have these SHA-256 values:

| Binary | SHA-256 |
| --- | --- |
| `debugTest/test.kexe` | `715b5dd925c6fd5f498e8ef6ff38e55dc0115c8b6e4bdca73fbb6a3d46f93fa4` |
| `releaseTest/test.kexe` | `4599e2828d7dbfb53eec64d5ace4d9cc1c7fcd1a6bc9fe75f6b02470318c7915` |

A connected, developer-mode iOS device was available, but prerequisite
inspection found the designated test-host profile expired. At that stage,
no current physical-iOS execution or installation was attempted. Validation
required an unexpired development profile for the intended Kompact test host,
covering the connected device, and a matching usable local signing identity.

The aligned-prefix follow-up also links fresh iOS Arm64 debug/release binaries
and passed 404 tests on iOS Simulator. Physical iOS was explicitly deferred
at the user's direction; neither result substituted for physical execution.

Older physical-iOS results in the
[allocation methodology](allocation-boxing-measurement.md) are historical
evidence, not proof for merged `ed7218c`. Likewise, the new
[generated-holder host comparison](generated-holder-benchmark.md) and iOS
Simulator fixture tests did not substitute for the missing device execution.

## Physical iOS follow-up

On 2026-10-18, fresh debug and release `iosArm64` binaries from merged
`465cb86bc0eda247afc4be13928c04c044684cb4` ran on the original iPhone SE
(2nd generation), hardware `iPhone12,8`, iOS `18.7.8`. Runtime and test
sources were unchanged. The forced build used JDK 21, Kotlin `2.4.21`,
Gradle `9.8.0`, and Xcode `27.1` (`27A9275`):

```bash
./gradlew :kompact:iosArm64TestBinaries \
  --no-daemon --rerun-tasks --no-build-cache --console=plain
```

The dedicated development profile was renewed with explicit authorization,
reusing the existing signing identity; no new valid local signing identity
was created. The renewed profile expires on 2026-10-25. Only the existing
`KompactNativeTests` host was replaced.

The post-renewal full-suite failure was an iOS `FRONTBOARD`
`process-launch` watchdog termination, code `0x8badf00d` and signal `SIGKILL`.
A standalone test executable does not complete a UIKit app launch merely
because it is wrapped in an app bundle. A small Xcode-built control launched
with the same signing assets; the Native suite also passed under LLDB.
Generic launch-security messages alone did not establish a trust failure.

The corrected `scripts/test-ios-device.sh` delegates launch to
`scripts/ios_device_tests.py`. It identifies the installed executable from
installation metadata, starts it suspended, attaches LLDB to the exact new
PID, waits for CoreDevice's asynchronous initial `SIGSTOP`, and then
continues it asynchronously with a bounded wait. Cached initial-stop state
is not resumed twice. A synchronous debugger-control attempt timed out during
diagnosis; the final six-run evidence uses the asynchronous runner.
Both the debuggee and console must exit successfully, and
the shell wrapper still requires a passing Kotlin/Native test summary.
Unexpected stops, ambiguous processes, and timeouts fail the run.
Existing development entitlements and signature verification are unchanged.
Hardware-free fixtures reproduce the old watchdog failure and test the
new debugger lifecycle in CI.

| Configuration | Physical executions | Tests per execution | Evidence |
| --- | ---: | ---: | --- |
| Debug | 3 | 404 passed | [redacted log](../../benchmarks/results/physical-ios-2026-10-18/ios-debug.txt) |
| Release | 3 | 404 passed | [redacted log](../../benchmarks/results/physical-ios-2026-10-18/ios-release.txt) |

Each execution used the [device procedure](../ci.md#platform-behavior-and-allocation-evidence).
The logs identify the exact binary and uncommitted runner source SHA-256
values, strict signature verification, successful debugger/console exits,
and local wrapper cleanup. The real Native test host remains installed to
preserve developer trust; no diagnostic C control is left installed.

All three checkpoint regressions passed in every execution. All five
scalar/cursor GC probes reported `[0, 0, 0]` swept objects for primitive and
measured samples, and `[4098, 4098, 4098]` for intentional-allocation controls.
These are bounded correctness and GC-probe results under a debugger, not
physical-device timing, complete holder-allocation measurements, or an
Instruments allocation trace.
