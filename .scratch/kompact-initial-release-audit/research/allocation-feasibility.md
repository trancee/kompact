# Allocation-contract feasibility

## Decision outcome

The user requires zero **library** allocations per read/write/encoding
operation after caller setup, using preallocated caller-owned buffers, across
the current targets. The guaranteed path may not construct fresh view/result
wrappers; it must return primitives/status or fill caller-reused state.
Explicit copies/snapshots and convenience wrapper APIs may allocate outside
the guarantee. The user further clarified:

- Exclude UTF-8 `String` creation/consumption; provide a byte-oriented guaranteed
  path.
- Add primitive/reusable low-allocation APIs; existing wrapper APIs may remain
  as convenience APIs outside the guarantee.
- Include variable-width repeats using caller-owned reusable index/workspace.
- Keep every current target, but do not claim the guarantee for Android Native
  arm64 until a validation spike proves a trustworthy method.

## Current source allocation sites

| Operation | Current behavior | Consequence |
| --- | --- | --- |
| `KompactWriter()` | Allocates a 16-byte `ByteArray` | No caller-buffer writer path exists. |
| Scalar write | Does not allocate per call if capacity suffices; buffer growth copies | Caller must control storage and capacity for the contract. |
| `writeString` / frame string read | Encode/decode creates `String`/`ByteArray` objects | Excluded; guaranteed path is byte-oriented. |
| Lambda `writeNested` | Creates child writer/buffer and `build()` snapshot | Cannot be on the guaranteed path. |
| `build()` / `toByteArray()` | Copies output into owned storage | Explicit ownership conversion, outside guarantee. |
| `KompactFrame.decode` | Creates a frame object | Guaranteed API needs primitive/caller-state entry points. |
| `readBlob` / `readNested` | Creates `KompactByteSlice` | Existing view path is convenience-only. |
| `readRepeated` | Creates `KompactRepeatedView`; variable-width form also creates checkpoints | Workspace must be caller-supplied/reused. |
| Repeated element access | Current view path may create frame/slice wrappers | Guaranteed accessor must avoid fresh wrappers. |
| `readScalarAsLong` | Returns regular `LongResult` class | Guaranteed path cannot return it. |
| Generated framed nested access | Constructs generated view objects | Generated convenience API must be excluded or complemented. |

Relevant files: `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactWriter.kt`,
`KompactFrame.kt`, `KompactByteSlice.kt`, `KompactRepeatedView.kt`,
`KompactRuntime.kt`, and KSP framed generators.

## Measurement feasibility by target

| Target | Candidate evidence | Status |
| --- | --- | --- |
| JVM | JMH / kotlinx-benchmark JVM with GC allocation profiling | Useful measurement, but do not generalize JVM results to other runtimes. |
| Android JVM (ART) | AndroidX Microbenchmark allocation counts on-device | Candidate acceptance method; requires non-debuggable/profileable release-like APK and controlled device conditions. |
| iOS Arm64 | Xcode Instruments Allocations on device, paired with an intentional-allocation positive control | Candidate acceptance method; physical-device validation is needed. |
| iOS Simulator Arm64 | Instruments on simulator | Smoke/diagnostic evidence only; not a substitute for device evidence. |
| Android Native Arm64 | Perfetto `heapprofd` may observe native allocation call stacks | **Unproven for Kotlin/Native in this project.** A focused spike must demonstrate detection and an intentional-allocation positive control before accepting it. |

Kotlin/Native `GC.lastGCInfo()` describes retained heap after collection, not
per-operation allocations. JVM/ART profiling does not prove Native behavior.
No benchmark/allocation gate currently exists in CI.

The repository's `docs/research/allocation-boxing-measurement.md` is a plan,
not executed evidence, and its cited benchmark versions need revalidation
before adoption. Candidate external references checked by research:

- kotlinx-benchmark: <https://github.com/Kotlin/kotlinx-benchmark>
- AndroidX Microbenchmark: <https://developer.android.com/topic/performance/benchmarking/microbenchmark-overview>
- AndroidX Benchmark releases: <https://developer.android.com/jetpack/androidx/releases/benchmark>
- Kotlin/Native memory manager: <https://kotlinlang.org/docs/native-memory-manager.html>
- Perfetto native heap profiler: <https://perfetto.dev/docs/data-sources/native-heap-profiler>

## Remaining design work

The contract is fixed, but the concrete caller-buffer API and testable
workspace shape remain open. Decide how to represent read/write cursor and
failure without allocation, how repeated variable-width indexing receives
adequate caller storage and reports insufficient capacity, and how generated
code exposes this primitive path alongside allocating convenience views.
Android Native allocation-proof feasibility is a gate before claiming the
guarantee there.
