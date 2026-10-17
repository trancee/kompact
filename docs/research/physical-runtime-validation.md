# Physical runtime validation after PR 99

## Scope and result

On 2026-10-17, the runtime merged by
[#99](https://github.com/trancee/kompact/pull/99), commit `ed7218c`, passed
six physical Android Native arm64 executions. Current-revision physical iOS
execution remains blocked by the expired designated test-host provisioning
profile. A valid profile for an unrelated app was not reused: the runner
installs the bundle selected by its profile and could replace that app.

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

## Current iOS blocker

Fresh debug and release `iosArm64` binaries linked successfully with Kotlin
`2.4.21` and Xcode `27.0` (`27A266a`):

```bash
./gradlew :kompact:iosArm64TestBinaries \
  --no-daemon --rerun-tasks --no-build-cache --console=plain
```

Their SHA-256 values are:

| Binary | SHA-256 |
| --- | --- |
| `debugTest/test.kexe` | `715b5dd925c6fd5f498e8ef6ff38e55dc0115c8b6e4bdca73fbb6a3d46f93fa4` |
| `releaseTest/test.kexe` | `4599e2828d7dbfb53eec64d5ace4d9cc1c7fcd1a6bc9fe75f6b02470318c7915` |

A connected, developer-mode iOS device was available, but prerequisite
inspection found the designated test-host profile expired. No current
physical-iOS execution or installation was attempted. Completion requires
an unexpired development profile for the intended Kompact test host,
covering the connected device, and a matching usable local signing identity.
Then execute the existing runner with that profile for both configurations;
see the [device procedure](../ci.md#platform-behavior-and-allocation-evidence).

Older physical-iOS results in the
[allocation methodology](allocation-boxing-measurement.md) are historical
evidence, not proof for merged `ed7218c`. Likewise, the new
[generated-holder host comparison](generated-holder-benchmark.md) and iOS
Simulator fixture tests do not substitute for this missing device execution.
