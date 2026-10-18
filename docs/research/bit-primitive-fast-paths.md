# Faster bit reads in KompactRuntime

`KompactRuntime.readBitsLong` used to walk the field one byte chunk at a time.
Each step computed the chunk width with `minOf`, masked the chunk, and shifted
it into the result, so every step depended on the one before. The read now
shifts each byte the field touches straight to its place, ORs it into one
`Long`, and masks the result once. `readBits` returns
`readBitsLong(...).toInt()`, and `writeBits` calls `writeBitsLong`, so one read
and one write implementation remain.

The writes still use the chunk loop. This page explains why, and records the
numbers behind both choices.

## What stayed the same

The wire format and the public signatures did not change, and `checkKotlinAbi`
passes. A read touches exactly the bytes in `byteSpan(bitOffset, bitWidth)`, so
a field that ends at the last byte never reads past the buffer. A width of 0
still returns 0. `KompactFrame.requireComplete` relies on that when no bits
remain.

`KompactRuntimeBitPrimitivesTest` compares every width from 0 to 64 at bit
offsets 0 to 23 against a reference implementation, over the backgrounds
`0x00`, `0xA5`, and `0xFF`. Each buffer is exactly as long as the field needs,
so a read or write one byte too far throws. The test passed on the old code
before the read changed.

## Measurement

`ScalarBenchmark` has four cases:

- `read` reads 64 bits through `KompactCursor`. The offset is a field of the
  cursor, so the compiler cannot treat it as a constant.
- `readFrame` reads seven fields that pack one 8-byte frame. Offsets and
  widths are constants, as in generated views.
- `readUnaligned` reads a 64-bit field at bit offset 3, which spans 9 bytes.
- `write`, `writeFrame`, and `writeUnaligned` are the matching writes.

`RepeatIndexBenchmark.variableIndex` also reads through the primitives with
offsets known only at run time.

Both columns come from the `main` profile, one run each, on an Apple-silicon
development host with JDK 25. Times are ns/op.

| Runtime | Case | Before | After | Change |
| --- | --- | ---: | ---: | ---: |
| JVM | `read` | 5.75 | 2.64 | −54% |
| JVM | `readFrame` | 1.86 | 2.09 | +12% |
| JVM | `readUnaligned` | 11.44 | 1.71 | −85% |
| JVM | `variableIndex` (65 entries) | 724 | 502 | −31% |
| JVM | `write` | 12.44 | 12.08 | −3% |
| JVM | `writeFrame` | 3.86 | 3.74 | −3% |
| JVM | `writeUnaligned` | 13.44 | 13.05 | −3% |
| macosArm64 | `read` | 26.55 | 22.08 | −17% |
| macosArm64 | `readFrame` | 13.43 | 12.10 | −10% |
| macosArm64 | `readUnaligned` | 13.07 | 11.35 | −13% |
| macosArm64 | `write` | 28.08 | 28.27 | +1% |
| macosArm64 | `writeFrame` | 8.54 | 8.19 | −4% |
| macosArm64 | `writeUnaligned` | 5.82 | 5.67 | −3% |

The write changes are within run-to-run noise. Allocation stayed at about
0 B/op in `jvmAllocationBenchmark`. Native read times include boxing the `Long`
that the benchmark returns, so compare them with each other, not with the
write times.

These numbers come from one host and are not a portable budget. To reproduce
them, follow [How to benchmark the runtime](../how-to/benchmark-runtime.md).

## Why the writes keep the chunk loop

Generated views call the primitives with constant offsets and widths. On
Kotlin/Native, LLVM inlines the small chunk loop at those call sites and folds
it to a few loads, masks, and stores. Disassembly of the benchmark binary
confirms this. With a variable offset, the old loop took 18.3 ns where a
word-based write took 9.2 ns, which shows how much of the old speed comes from
folding.

Every write rewrite that was tried lost that inlining:

- A loop bounded by `minOf(span, 8)` with a separate ninth-byte step. LLVM
  fully unrolls a bounded loop before deciding whether to inline, and the
  unrolled body is too large. `writeFrame` went from 8.3 to 14.5 ns on Native,
  and `writeUnaligned` from 6.1 to 9.0 ns.
- Several variants that build a word, clear the field with a mask, and store
  the bytes back. These cut the JVM `write` case from 12 to about 4.3 ns, but
  LLVM did not inline them for fields that span 2 or more bytes, so Native
  constant-site writes regressed.

The ticket allows no regression, and Native is where BLE apps on iOS run, so
the chunk loop stays. A faster write would need to keep the Native body small
enough to inline at constant sites.

## The JVM `readFrame` cost

`readFrame` on the JVM got 0.23 ns slower across seven fields, about 0.03 ns
per field. A second run in reverse order gave the same gap (1.82 against
2.13 ns). `-XX:+PrintInlining` shows that C2 inlines all seven calls in both
versions, so the gap comes from the code C2 generates, not from inlining.
Variants that kept the chunk loop for 1- and 2-byte fields, or added a
single-byte fast path, all landed between 2.09 and 2.23 ns.

This cost is worth paying. It applies only to narrow fields at constant
offsets on the JVM, where reads were already under 0.3 ns per field. In
return, reads at variable offsets got 26% to 54% faster, wide unaligned reads
got 85% faster, and every Native read case improved.
