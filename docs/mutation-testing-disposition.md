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
| `:kompact` | 387 | 442 / 442 | 442 | 0 | 0 | 0 | 100% |
| `:kompact-ksp` | 180 | 192 / 192 | 192 | 0 | 0 | 0 | 100% |

"Wrapped tests" equals the number of `@Test` methods in the adapter sources.

### Mutations per production target

| Target | Mutations |
|---|---:|
| `KompactRuntime.kt` | 70 |
| `KompactCursor.kt` | 89 |
| `KompactCursorByteRanges.kt` | 116 |
| `KompactCursorRepeats.kt` | 148 |
| `KompactByteRange.kt` | 19 |
| `FramedClassGenerator.kt` | 8 |
| `FramedHolderGenerator.kt` | 44 |
| `FramedScalarHolderGenerator.kt` | 50 |
| `ValueClassGenerator.kt` | 25 |
| `ValueHolderGenerator.kt` | 65 |

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
| `ValueHolderGenerator` `trailingBits > 0` survived. | Test gap. The `>=` mutant emits `skipBits(0)` and `writeZeros(0)`. A dense-holder test now rejects them. |

## Limitations

- The results cover JVM execution only. They provide no Android or Native
  evidence.
- MutFlow truncates long killer identities in the JSON report, so the
  redundancy groups are only candidates.
- Only the targets listed in each module's `mutflow { targets }` are mutated.
