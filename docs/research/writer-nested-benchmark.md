# In-place nested writes in KompactWriter

`KompactWriter.writeNested(lengthPrefixWidth, block)` used to run the block
against a child writer, copy the child buffer, and then append that copy. Each
call allocated a writer object, its 16-byte initial buffer, any grown buffers,
and the copy. The writer now reserves the length prefix, runs the block in
place, pads the payload with zero bits to whole bytes, and back-patches the
prefix. If the block throws, or the payload is too long for the prefix, the
writer rolls back to its earlier position. `reset()` lets you reuse a writer
and its grown buffer.

The wire output did not change. `KompactWriterReuseTest` checks that the
bytes equal the encoding from `writeNested(lengthPrefixWidth, raw, start, end)`,
including at unaligned offsets.

## Measurement

`WriterBenchmark.nestedEncode` encodes 16 nested regions of 53 bits each, with
an 8-bit length prefix. It uses the `writer` profile: JVM, 3 forks of
5 iterations, average time. Each variant was run once on an Apple-silicon
development host with JDK 21:

| Variant | ns/op |
| --- | --- |
| Child writer per nested region (previous) | 615.8 ± 32.5 |
| In place, new `KompactWriter` per message | 485.7 ± 6.8 |
| In place, reused writer through `reset()` | 386.3 ± 16.3 |

These results come from a single host and are not a portable budget. The
child-writer implementation no longer exists, and `nestedEncode` now measures
only the reused-writer variant. Before you rely on these numbers, rerun
`nestedEncode` on your host. The other two rows need their old code to rerun.
