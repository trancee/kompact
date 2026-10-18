# Mutation testing disposition

This record holds the current MutFlow `1.7.0` results for the runtime and the
KSP generators. It also explains how every earlier survivor and timeout was
resolved. Git history keeps the earlier per-adapter audits. To reproduce the
results, use the commands in
[`.omp/AGENT-USAGE.md`](../.omp/AGENT-USAGE.md). For the adapter design, see
[ADR-0015](adr/0015-generated-mutation-adapters.md).

## Current results

Each run uses the generated adapter, which wraps every behavior test of the
module. The budget is unlimited (`maxMutationRuns = Int.MAX_VALUE`). Both
Gradle tasks pass, and the evidence is JVM only.

| Module | Wrapped tests | Discovered / evaluated | Killed | Survived | Timed out | Gaps | Score |
|---|---:|---:|---:|---:|---:|---:|---:|
| `:kompact` | 400 | 753 / 753 | 753 | 0 | 0 | 0 | 100% |
| `:kompact-ksp` | 183 | 391 / 391 | 391 | 0 | 0 | 0 | 100% |

"Wrapped tests" equals the number of `@Test` methods in the adapter sources.

### Mutations per production target

The runtime targets the whole `ch.trancee.kompact.runtime.**` package, and
the processor targets the whole `ch.trancee.kompact.ksp.**` package. Files
without mutable logic do not appear.

| Runtime target | Mutations |
|---|---:|
| `KompactByteRange.kt` | 19 |
| `KompactByteSlice.kt` | 1 |
| `KompactCursor.kt` | 87 |
| `KompactCursorByteRanges.kt` | 116 |
| `KompactCursorRepeats.kt` | 148 |
| `KompactFrame.kt` | 61 |
| `KompactFraming.kt` | 26 |
| `KompactRepeatWorkspace.kt` | 2 |
| `KompactRepeatedView.kt` | 15 |
| `KompactResult.kt` | 40 |
| `KompactResultExtensions.kt` | 12 |
| `KompactRuntime.kt` | 75 |
| `KompactUtf8Validator.kt` | 73 |
| `KompactWriter.kt` | 62 |
| `NestedRegionResult.kt` | 14 |
| `ScalarType.kt` | 2 |

| Processor target | Mutations |
|---|---:|
| `FieldCodeGenerator.kt` | 13 |
| `FramedClassGenerator.kt` | 8 |
| `FramedFieldCodeGenerator.kt` | 36 |
| `FramedHolderGenerator.kt` | 44 |
| `FramedLayoutValidator.kt` | 41 |
| `FramedRepeatHolderGenerator.kt` | 1 |
| `FramedScalarHolderGenerator.kt` | 49 |
| `KompactFieldInfo.kt` | 1 |
| `KompactFieldType.kt` | 21 |
| `KompactModelParser.kt` | 24 |
| `KompactSymbolProcessor.kt` | 8 |
| `LayoutValidator.kt` | 54 |
| `ModelSpec.kt` | 2 |
| `ValueClassGenerator.kt` | 25 |
| `ValueHolderGenerator.kt` | 64 |

## Resolved survivors and timeouts

The last hand-written union adapters left 12 runtime survivors, 8 runtime
timeouts and 4 KSP survivors. The generated adapter also found new survivors.
None of them was suppressed:

| Former finding | Resolution |
|---|---|
| Chunk loops in `KompactRuntime` (`remaining > 0` → `>=`, `0` → `-1`) timed out, because each extra iteration rescanned large buffers. | Each loop now runs `byteSpan(bitOffset, bitWidth)` times. `byteSpan` uses `shr`, so even arithmetic mutants keep a small trip count. |
| `KompactCursor.writeZeros` loop (`remaining > 0`) timed out. | It writes `repeat(bitCount / 64)` full words plus one tail, and always records `valueBits = 0`. |
| `reset` detail branch `endBit < position -> endBit` survived. | Dead branch: the `else` already reported `endBit`. Removed. |
| `resetByteRange` `endByte * 8L > Int.MAX_VALUE` (`>` → `>=`) survived. | `Int.MAX_VALUE` is not a multiple of 8, so the boundary was unobservable. Rewritten as `endByte > Int.MAX_VALUE / 8`, and a test now pins the largest encodable end. |
| `validateSigned` `bitWidth < 64` survived. | Redundant: a 64-bit shift of 0 accepts the full `Long` range. Removed. |
| `validateUnsigned(Long)` `bitWidth < 63` survived. | Redundant for non-negative values. It is replaced by the overflow-free `(value ushr 1) ushr (bitWidth - 1)`. |
| `writeUnsigned(ULong)` `bitWidth < 64` (`64` → `63`) survived. | Test gap. A test now rejects a 63-bit write with the sign bit set. |
| `reset` detail ordering (`position < startBit` → `<=`/`>`) survived. | Test gap. Diagnostic cases were added for `startBit == position` and `endBit < position`. |
| `length > Int.MAX_VALUE` checks in byte ranges and repeat lookup survived. | Prefix widths are 8, 16 or 32 bits, so `length * 8` never overflows a `Long`, and the end-bit check rejects every such length. Removed. |
| `readScalar` `bitWidth <= 31` split survived. | Both branches decode the same value. `readScalar` now always uses `readBitsLong`, which is also faster in the JVM benchmark. |
| `readScalarAsLong` `signed && bitWidth < 64` survived. | A shift of 0 leaves 64-bit values unchanged. The width test was removed. |
| Widening the runtime to the whole package exposed 42 survivors in the writer, frame, framing, repeated view, `LongResult` and UTF-8 validator. | Equivalent code was removed: the `writeScalar` width split, the writer's duplicate `checkPrefix` (`KompactFraming.writeLengthPrefix` validates before writing), the redundant per-append capacity check, the frame's `remaining > 0` guard (a zero-width read returns 0) and the repeated view's `Int` clamp (the arithmetic is `Long`). `LongResult.hashCode` hashes the error or the value, with no arithmetic. |
| Four `Int.MAX_VALUE / 8` guards in the cursor, frame and framing were unobservable without 256 MiB inputs. | One `KompactRuntime.isBitAddressable` helper replaces them, and a direct test pins both boundaries. |
| Writer growth (`> buffer.size`, `* 2`, `/ 8`) and checkpoint sizing (`count / 64`) only changed allocation size. | Internal `capacity` and `checkpointCount` seams let tests pin the growth and sparse-index policies. |
| One-bit nested padding, empty regions at an offset, truncated prefixes after consumed bits, the largest 32-bit count, one-byte element overruns, trailing-bit limits, unaligned element bytes and UTF-8 sequences cut at the region end survived. | Test gaps. A behavior test was added for each. |
| `ValueHolderGenerator` `trailingBits > 0` survived. | Test gap. The `>=` mutant emits `skipBits(0)` and `writeZeros(0)`. A dense-holder test now rejects them. |
| Widening the processor to the whole package: `LayoutValidator.validateNoOverlaps` compared half-open ranges with an inclusive end, so the boundary mutant survived. | Real bug: one-bit overlaps, including two identical 1-bit fields, were accepted, and the message printed the wrong end. The check is now half-open (`a.bitOffset < b.endBit && b.bitOffset < a.endBit`), and a test pins a single-bit overlap. |
| `FramedFieldCodeGenerator` signed shifts for widths 31 and 63 and repeated-element signedness survived. | Test gaps. Generator tests now pin each width and the signedness of repeated scalar elements. |
| `FramedFieldCodeGenerator` `bitWidth <= 31` write split survived. | Both branches wrote the same bits. Generated `Int` writes now always use `writeBitsLong`. |
| `FramedLayoutValidator` bit remainder `+` → `-` survived. | Equivalent modulo 8 for a yes/no check. The alignment error now reports how many bits into a byte the field starts, or that its start depends on a preceding repeat count. |

## Limitations

- The results cover JVM execution only. They provide no Android or Native
  evidence.
- MutFlow truncates long killer identities in the JSON report, so the
  redundancy groups are only candidates.
- Only the targets listed in each module's `mutflow { targets }` are mutated.
