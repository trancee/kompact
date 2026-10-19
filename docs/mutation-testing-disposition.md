# Mutation testing disposition

This record holds the current MutFlow `1.7.0` results for the runtime and the
KSP processor. It also records how each earlier survivor and timeout was
resolved. Git history keeps the earlier audits of each adapter. To reproduce
the results, run the commands in
[`.omp/AGENT-USAGE.md`](../.omp/AGENT-USAGE.md) or in the
[mutation testing section of the CI guide](ci.md#mutation-testing). For the
adapter design, see [ADR-0015](adr/0015-generated-mutation-adapters.md).

## Current results

Each module runs one generated adapter that wraps every behavior test of the
module. The budget is unlimited (`maxMutationRuns = Int.MAX_VALUE`). Both
Gradle tasks pass, and the evidence covers the JVM only.

| Module | Wrapped tests | Discovered / evaluated | Killed | Survived | Timed out | Gaps | Score |
|---|---:|---:|---:|---:|---:|---:|---:|
| `:kompact` | 401 | 748 / 748 | 748 | 0 | 0 | 0 | 100% |
| `:kompact-ksp` | 183 | 391 / 391 | 391 | 0 | 0 | 0 | 100% |

The "Wrapped tests" column is the number of `@Test` methods in the adapter
sources.

### Mutations per production target

The runtime run targets the whole `ch.trancee.kompact.runtime.**` package.
The processor run targets the whole `ch.trancee.kompact.ksp.**` package. The
tables leave out files that have no logic MutFlow can mutate.

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
timeouts, and 4 KSP survivors. The generated adapter found more survivors.
No finding was suppressed. The table lists each finding and its fix. In the
first column, "`a` to `b`" means that MutFlow replaced `a` with `b`.

| Former finding | Resolution |
|---|---|
| The chunk loops in `KompactRuntime` timed out under `remaining > 0` to `>=` and `0` to `-1`, because each extra iteration rescanned large buffers. | Each loop now runs `byteSpan(bitOffset, bitWidth)` times. `byteSpan` uses `shr`, so even arithmetic mutants keep the trip count small. |
| The `KompactCursor.writeZeros` loop (`remaining > 0`) timed out. | It writes `repeat(bitCount / 64)` full words plus one tail, and it always records `valueBits = 0`. |
| The `reset` detail branch `endBit < position -> endBit` survived. | The branch was dead, because the `else` branch already reported `endBit`. It was removed. |
| `resetByteRange` survived `endByte * 8L > Int.MAX_VALUE` with `>` to `>=`. | `Int.MAX_VALUE` is not a multiple of 8, so no test could observe the boundary. The check is now `endByte > Int.MAX_VALUE / 8`, and a test pins the largest end that the cursor can address. |
| `validateSigned` survived at `bitWidth < 64`. | The check was redundant, because a shift by 0 bits accepts the full `Long` range. It was removed. |
| `validateUnsigned(Long)` survived at `bitWidth < 63`. | The check was redundant for non-negative values. The function now uses `(value ushr 1) ushr (bitWidth - 1)`, which cannot overflow. |
| `writeUnsigned(ULong)` survived `bitWidth < 64` with `64` to `63`. | A test was missing. A test now rejects a 63-bit write that has the sign bit set. |
| The `reset` detail ordering survived `position < startBit` to `<=` and to `>`. | Tests were missing. New diagnostic cases cover `startBit == position` and `endBit < position`. |
| The `length > Int.MAX_VALUE` checks in byte ranges and in repeat lookup survived. | Prefix widths are 8, 16, or 32 bits, so `length * 8` never overflows a `Long`, and the end-bit check already rejects every such length. The checks were removed. |
| The `readScalar` split at `bitWidth <= 31` survived. | Both branches decoded the same value. `readScalar` now always uses `readBitsLong`, which is also faster in the JVM benchmark. |
| `readScalarAsLong` survived at `signed && bitWidth < 64`. | A shift by 0 bits leaves 64-bit values unchanged. The width test was removed. |
| Widening the runtime target to the whole package exposed 42 survivors in the writer, the frame, the framing code, the repeated view, `LongResult`, and the UTF-8 validator. | Equivalent code was removed: the width split in `writeScalar`, the writer's duplicate `checkPrefix` (because `KompactFraming.writeLengthPrefix` validates before it writes), the redundant capacity check on each append, the frame's `remaining > 0` guard (because a zero-width read returns 0), and the repeated view's `Int` clamp (because the arithmetic uses `Long`). `LongResult.hashCode` now hashes the error or the value, with no arithmetic. |
| Four `Int.MAX_VALUE / 8` guards in the cursor, the frame, and the framing code needed inputs of 256 MiB or more to observe. | One helper, `KompactRuntime.isBitAddressable`, replaces them, and a direct test pins both boundaries. |
| Writer growth (`> buffer.size`, `* 2`, `/ 8`) and checkpoint sizing (`count / 64`) mutants changed only the allocation size. | The internal `capacity` and `checkpointCount` properties let tests pin the growth policy and the sparse checkpoint policy. |
| Mutants survived for these cases: one-bit nested padding, empty regions at an offset, truncated prefixes after consumed bits, the largest 32-bit count, element overruns by one byte, trailing-bit limits, unaligned element bytes, and UTF-8 sequences cut at the region end. | Tests were missing. A behavior test now covers each case. |
| `ValueHolderGenerator` survived at `trailingBits > 0`. | A test was missing. The `>=` mutant emits `skipBits(0)` and `writeZeros(0)`, and a dense-holder test now rejects that output. |
| After the processor target widened to the whole package, the boundary mutant in `LayoutValidator.validateNoOverlaps` survived, because the check compared half-open ranges with an inclusive end. | This was a real bug. The validator accepted one-bit overlaps, including two identical 1-bit fields, and the error message printed the wrong end. The check is now half-open (`a.bitOffset < b.endBit && b.bitOffset < a.endBit`), and a test pins a single-bit overlap. |
| `FramedFieldCodeGenerator` survived on the signed shifts for widths 31 and 63 and on the signedness of repeated elements. | Tests were missing. Generator tests now pin each width and the signedness of repeated scalar elements. |
| The `FramedFieldCodeGenerator` write split at `bitWidth <= 31` survived. | Both branches wrote the same bits. Generated `Int` writes now always use `writeBitsLong`. |
| `FramedLayoutValidator` survived the bit remainder `+` to `-`. | The mutant is equivalent modulo 8 for a yes-or-no check. The alignment error now reports how many bits into a byte the field starts, or that its start depends on the count of a preceding repeat. |

## Limitations

- The results cover JVM execution only. They give no Android or Native
  evidence.
- MutFlow truncates long killer identities in the JSON report, so the
  redundancy groups are only candidates.
- MutFlow mutates only the targets listed in the `mutflow { targets }` block
  of each module.
