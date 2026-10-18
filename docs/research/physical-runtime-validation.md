# Physical runtime validation records

## Scope and result

On 2026-10-17, the runtime merged by
[#99](https://github.com/trancee/kompact/pull/99), commit `ed7218c`, passed
six physical Android Native arm64 executions. The aligned-prefix follow-up
also passed six Android executions. On 2026-10-18, merged `465cb86` passed
six physical iOS executions. Two fixes came first: the dedicated test-host
profile was renewed, and the runner's launch-watchdog failure was corrected.
No other app's profile was ever reused. The runner installs the bundle that
its profile selects, so it could replace that app.

These are runtime correctness results and bounded Native allocation-probe
results. They are not physical-device timing benchmarks or an Instruments
allocation trace.

## Android evidence

The test binaries came from the successful
[CI run 39061939916](https://github.com/trancee/kompact/actions/runs/39061939916)
at revision `5eb6832`. The runtime and test tree and the device-runner tree
were verified identical to merged `ed7218c`:

```bash
git diff 5eb6832 ed7218c --exit-code -- kompact scripts
```

The device was Android model `A063`, Android `15`, API `35`, build
`AQ3A.240929.001`, arm64. The host used macOS `27.0.1` and ADB
`37.0.1-15733141`. The records keep no serial number, personal device name, or
signing identity.

| Configuration | Artifact | Physical executions | Tests per execution | Evidence |
| --- | --- | ---: | ---: | --- |
| Debug | `kompact-android-native-arm64-test` | 3 | 403 passed | [redacted log](../../benchmarks/results/physical-runtime-2026-10-17/android-debug.txt) |
| Release | `kompact-android-native-arm64-release-test` | 3 | 403 passed | [redacted log](../../benchmarks/results/physical-runtime-2026-10-17/android-release.txt) |

Each execution used:

```bash
scripts/test-android-native-device.sh <downloaded-artifact>/test.kexe
```

The runner verified the SHA-256 both locally and on the device, ran the full
shared Native suite, and confirmed that the device's temporary directory was
cleaned up. The retained logs include both fixed-repeat checkpoint regression
tests by name. One checks exact checkpoints and that unused storage is
preserved across widths and boundaries. The other checks failure atomicity
after a one-bit truncation.

The debug binary SHA-256 is
`71f1d11efe65a68e22f74eedf86292dca1fbb3a381bb88593fea5cf60913456d`.
The release binary SHA-256 is
`ba59813b8d92cf66b27d736a57a558e25d5c7436c1334fc1585846fc7f8383ba`.

In every run, three samples of each probe reported `[0, 0, 0]` swept objects,
matching the primitive baselines. The probes cover generated scalar speed
reads and writes, cursor byte reads and writes, and unsigned validation. Each
intentional-allocation control reported `[4098, 4098, 4098]`. The tests run
4,096 operations per sample. The probe reads GC sweep statistics for those
call shapes. It is not an exact per-operation allocation counter. The physical
probes do not measure allocations for repeat indexing or for complete
framed-holder decoding.

## Aligned-prefix follow-up

The later [prefix-reader correction](aligned-count-prefix-benchmark.md), on
base `6115b87`, was validated with freshly linked local binaries, not with
the older CI artifacts above. The retained logs identify the exact SHA-256
values of the runtime source, uncommitted at the time, and of the binaries:

| Configuration | Physical executions | Tests per execution | Evidence |
| --- | ---: | ---: | --- |
| Debug | 3 | 404 passed | [redacted log](../../benchmarks/results/physical-prefix-2026-10-17/android-debug.txt) |
| Release | 3 | 404 passed | [redacted log](../../benchmarks/results/physical-prefix-2026-10-17/android-release.txt) |

Both configurations were linked locally with JDK `21.0.12.1`, Kotlin
`2.4.21`, and the existing `:kompact:androidNativeArm64TestBinaries` task,
forced with `--rerun-tasks --no-build-cache`. Each binary then ran three
times through `scripts/test-android-native-device.sh` on the same `A063`
Android 15, API 35 test device. Integrity verification and temporary-file
cleanup succeeded in all six executions.

All three checkpoint regression tests passed, including the new test that
checks unsigned interpretation of each count byte through capacity
diagnostics. The five scalar and cursor allocation probes again reported zero
swept objects in every primitive, read, and write sample. They reported
`[4098, 4098, 4098]` in every intentional-allocation control. These probes
still do not measure allocations for complete holders or repeat indexing, and
they do not set device timing guarantees.

## iOS provisioning history

After PR 99, fresh debug and release `iosArm64` binaries linked with Kotlin
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

A connected iOS device in developer mode was available. A check of the
prerequisites found that the profile for the designated test host had
expired. At that stage, nobody attempted a physical-iOS execution or
installation. Validation needed two things: an unexpired development profile
for the intended Kompact test host that covered the connected device, and a
matching local signing identity that worked.

The aligned-prefix follow-up also linked fresh iOS Arm64 debug and release
binaries and passed 404 tests on the iOS Simulator. Physical iOS was deferred
at the user's direction. Neither result replaced a physical execution.

Older physical-iOS results in the
[allocation methodology](allocation-boxing-measurement.md) are historical
evidence. They do not prove anything about merged `ed7218c`. In the same way,
the [generated-holder host comparison](generated-holder-benchmark.md) and the
iOS Simulator fixture tests did not replace the missing device execution.

## Physical iOS follow-up

On 2026-10-18, fresh debug and release `iosArm64` binaries from merged
`465cb86bc0eda247afc4be13928c04c044684cb4` ran on the original iPhone SE
(2nd generation), hardware `iPhone12,8`, iOS `18.7.8`. The runtime and test
sources had not changed. The forced build used JDK 21, Kotlin `2.4.21`,
Gradle `9.8.0`, and Xcode `27.1` (`27A9275`):

```bash
./gradlew :kompact:iosArm64TestBinaries \
  --no-daemon --rerun-tasks --no-build-cache --console=plain
```

The dedicated development profile was renewed with explicit authorization.
The renewal reused the existing signing identity and created no new local
signing identity. The renewed profile expires on 2026-10-25. Only the
existing `KompactNativeTests` host was replaced.

After the renewal, the full suite first failed with an iOS `FRONTBOARD`
`process-launch` watchdog termination, code `0x8badf00d` and signal
`SIGKILL`. Wrapping a standalone test executable in an app bundle does not
make it finish a UIKit app launch. A small control app built with Xcode
launched with the same signing assets, and the Native suite also passed under
LLDB. Generic launch-security messages alone did not show a trust failure.

The corrected `scripts/test-ios-device.sh` hands the launch to
`scripts/ios_device_tests.py`. That script runs these steps:

1. Find the installed executable from the installation metadata.
2. Start it suspended.
3. Attach LLDB to the exact new PID.
4. Wait for CoreDevice's asynchronous initial `SIGSTOP`.
5. Continue the process asynchronously, with a bounded wait.

The script does not resume a cached initial stop twice. During diagnosis, an
attempt to control the debugger synchronously timed out. The final six-run
evidence uses the asynchronous runner. Both the debuggee and the console must
exit successfully, and the shell wrapper still requires a passing
Kotlin/Native test summary. Unexpected stops, ambiguous processes, and
timeouts fail the run. The existing development entitlements and signature
verification did not change. In CI, hardware-free fixtures reproduce the old
watchdog failure and test the new debugger lifecycle.

| Configuration | Physical executions | Tests per execution | Evidence |
| --- | ---: | ---: | --- |
| Debug | 3 | 404 passed | [redacted log](../../benchmarks/results/physical-ios-2026-10-18/ios-debug.txt) |
| Release | 3 | 404 passed | [redacted log](../../benchmarks/results/physical-ios-2026-10-18/ios-release.txt) |

Each execution followed the
[device procedure](../ci.md#platform-behavior-and-allocation-evidence).
The logs record the exact SHA-256 values of the binary and of the runner
source, uncommitted at the time. They also record strict signature
verification, successful debugger and console exits, and local wrapper
cleanup. After the runs, the real Native test host stayed installed so that
the device keeps trusting the developer. No diagnostic C control app was left
installed.

All three checkpoint regression tests passed in every execution. All five
scalar and cursor GC probes reported `[0, 0, 0]` swept objects for the
primitive and measured samples, and `[4098, 4098, 4098]` for the
intentional-allocation controls. These are bounded correctness and GC-probe
results under a debugger. They are not physical-device timing, complete
holder-allocation measurements, or an Instruments allocation trace.
