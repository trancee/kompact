# In-place nested writes in KompactWriter

`KompactWriter.writeNested(block)` used to run the block against a child
writer, build a copy of the child buffer, and then append that copy. Each call
allocated a writer object, its 16-byte initial buffer, any grown buffers, and
the snapshot. The writer now reserves the length prefix, runs the block in
place, zero-pads the payload to whole bytes, and back-patches the prefix. A
thrown block or an oversized payload rolls the writer back to its earlier
position. `reset()` lets callers reuse a writer and its grown buffer.

The wire output is unchanged: `KompactWriterReuseTest` checks byte equality
with the slice-based encoding, including at unaligned offsets.

## Measurement

`WriterBenchmark.nestedEncode` encodes 16 nested regions of 53 bits each,
using the `writer` profile (JVM, 3 forks × 5 iterations, avgt). It was run once
on an Apple-silicon development host with JDK 21:

| Variant | ns/op |
| --- | --- |
| Child writer per nested region (previous) | 615.8 ± 32.5 |
| In place, new `KompactWriter` per message | 485.7 ± 6.8 |
| In place, reused writer via `reset()` | 386.3 ± 16.3 |

These results come from a single host and are not a portable budget. Re-run
both variants on the same host before relying on the numbers.
