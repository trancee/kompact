# Design rationale

Why Kompact works the way it does. This document explains the trade-offs that shaped the API. It sits next to the reference docs, which describe *what* each function does. This one describes *why*.

## Bit-packed, not byte-aligned

The wire format packs fields into the smallest number of bits. A 1-bit boolean takes one bit. A 4-bit enum takes four. A 10-bit counter takes ten. The byte boundary isn't special. A 10-bit field at offset 4 occupies bits 4..13, which span two bytes on the wire.

The alternative, fixed-width fields byte-aligned, wastes up to 7 bits per field and adds 1 byte per field to every record. For short BLE frames, that overhead is the difference between one advertisement per connection interval and three. Protobuf chose bit-packing for the same reason. FlatBuffers chose byte-alignment for offset random-access, and Kompact deliberately gives up random access to recover the bit efficiency.

Kompact's bit layout (LSB-first, see the how-to for the wire-format diagram) is the same convention Protobuf and Cap'n Proto settled on. It also matches the way x86 and ARM buses order bits, which makes the cross-byte-boundary shifts the bit-packed format requires natural on both.

## The trade-off space at a glance

```
Need                       Common approach            Kompact
------------------------    -------------------------   -------
Short BLE frames          bit-pack + parse-forward   yes
Random access to fields    offset table (FlatBuf)     no
Schema evolution           fixed schema + envelopes   partial (ver+skip)
Zero-alloc on every read    typed result, no boxing    yes
```

The `partial` row for schema evolution is the cost of the bit-packing choice. Adding a new field to the end of a v1 stream is non-breaking. Reordering existing fields, inserting a fixed-width field, or changing a field's bit width are all breaking changes. The KSP processor catches overlap and width errors at compile time. Forward compat on the read side is handled by uniform length-prefix widths (covered later).

## Value classes over boxed primitives

The read path needs to be zero-allocation. A function that returns `Int` is fine. A function that returns `Int?` boxes. A function that returns `String` allocates. A function that returns `Pair<Int, Int>` allocates a Pair object.

`KompactRuntime.readBits` returns a primitive `Int`. `KompactRead.readUInt8` returns `IntResult`, which is a value class on JVM (with `@JvmInline`) and a plain value class on iOS. Either way, the value is held in a primitive register on the success path. No heap allocation.

The cost: a `KompactError` failure code is a small integer packed into the high bits, not a thrown exception. Throwing allocates (stack trace capture), which would break the zero-alloc read contract. Byte offset is not on the fast path. The opt-in `decodeFull()` diagnostics path attaches it only on failure.

```
Success path (hot)
  ByteArray -- readBits / readBitsLong -- packed: Long -- extract(value, errorCode, ok) -- return value
                                                              |
                                                              v on failure
Failure path (cold, opt-in)
  ByteArray -- readBits fails bounds check -- packed: Long (errorCode != 0) -- decodeFull() attaches offset
```

The success path never touches the failure path. The failure path is opt-in and allocates only when explicitly requested.

## Uniform length-prefix width

A Kompact schema with several length-prefixed fields must use the same prefix width everywhere: 8, 16, or 32 bits, one value per schema. This costs a few bits of overhead per field (a 4-byte prefix where a 1-byte prefix would suffice) and buys forward compatibility: an older reader can scan past an unknown trailing length-delimited field by reading the uniform-width prefix and skipping the payload. Without the uniformity, an older reader would have to know the new field's prefix width, and that knowledge is exactly what versioning is supposed to make unnecessary.

```
Older v1 reader                      Wire stream v2
---------------                       ---------------
   | read 4-byte version                 | [v:2][known_8][new_8=99][known_8]
   |       |                             |
   |<------+- 2 (unknown)                 |
   | skip uniform-width 8-bit prefix       |
   | length=1, skip 1 byte                |
   | read next known_8-bit field           |
   v                                       v
Decoded v1 fields; ignored the v2 field.
```

A mixed-prefix schema fails `LayoutModel.uniformPrefixWidthSatisfied()` at compile time. The cost is fixed (one decision per schema); the benefit is a single-pass scan for unknown fields.

## Length-prefix, not offset-table

FlatBuffers indexes every field by an absolute byte offset. Readers jump to each field directly. Kompact can't do that with variable-length fields (strings, blobs, nested). An offset would have to be recomputed every time a preceding field's length changes. Offsets relative to the start of the parent struct still require walking the parent to find the field.

Kompact's solution: parse forward. The reader has a cursor. Length-prefixed fields read their prefix, then their payload, then advance the cursor. Unknown fields are skipped by their uniform-width prefix. The cost is sequential access. The benefit is that a single forward scan can decode the whole stream, and forward compatibility reduces to "skip one prefix + payload".

```
Wire buffer:    [v:2 (4B)] [len=5 (1B)] ["hello" (5B)] [flag (1B)]
                    |            |            |              |
                    v            v            v              v
v1 reader:      read 4B      read 1B        skip 5B      read 1B
              skip (v=2)   len=5
              (unknown)
```

The reader doesn't need an index. It just walks the stream once. The cost is no random access. The benefit is the same code path that handles new fields handles old ones.

## Version prefix, not magic number

A 4-byte little-endian `UInt` at the start of every stream is the version. An older reader that sees a version it doesn't recognize fails fast with `UnsupportedSchemaVersion`. Typed, no silent misread, no guessing. The cost is 4 bytes per stream. The benefit is that a version bump is a real, explicit event, not a heuristic.

The default supported version set is `{1u}`. A library user overrides it via `KompactVersionedStream.setSupportedVersions(...)` on the reader. The writer always emits the version it was compiled with.

## Zero-alloc read, alloc-on-write

The read path is the hot path. It must not allocate. The write path is construction-time. It builds the buffer, allocates as it grows, then snapshots the result. Allocating during construction is fine. The calling code is typically building a single message per event, not in a tight loop.

Kompact's read API is `ByteArray` in, typed result out. The caller owns the buffer. No defensive copy. No allocation per field. No boxing. The KSP-generated view is a value class wrapping the caller's `ByteArray`; each accessor is a `KompactRuntime.readBits(...)` or `KompactRead.readXxx*(...)` call, nothing else.

## Why hand-written common API, KSP-generated value-class views

The runtime (`KompactRuntime`, `KompactRead`, `KompactWriter`, `KompactVersionedStream`, `AllocationCounter`, the result types) is hand-written common code. It has to be correct on every KMP target from the first commit. It is the foundation everything else is built on.

The KSP processor generates per-schema value-class views (the `expect value class VehicleTelemetryView(val raw: ByteArray)` and its platform actuals). This is the boilerplate. For every `@KompactField`, the view exposes a `val foo: T get() = KompactRead.readUInt*(raw, …)`. A schema with 30 fields would otherwise mean 30 identical-shape accessor declarations. The processor writes them. The runtime stays small and reviewable. The schemas stay declarative.

The alternative, fully runtime reflection, would either re-introduce allocation (the boxed `KProperty` lookup) or push a giant macro system onto the build. KSP with a deterministic, incremental processor is the middle ground.

## Why LSB-first bit packing

LSB-first matches the way modern CPUs and buses order bytes (little-endian) and bits (LSB first in shift registers). Protobuf chose LSB-first for the same reason. MSB-first (network byte order, ASN.1 BER) is the alternative. It is correct but slightly less natural for the cross-byte-boundary shifts the bit-packed format requires. The cross-platform zero-allocation constraint pushed LSB-first: every shift, mask, and `and 0xFF` operation is identical on JVM and Kotlin/Native.

## Deferred work

- **Floats**. IEEE-754 32/64-bit floats with NaN canonicalization are in the v1 type set per the locked design, but the implementation is deferred. The `KompactField` annotation doesn't yet carry an `isFloat` marker. The writer doesn't have `writeFloat32` / `writeFloat64`.
- **iOS KSP per-target actuals**. The pre-existing KSP limitation prevents the processor from emitting per-target actuals. The processor emits the common `expect`; the consumer's per-target `actual` is hand-written. A future version of the processor can close this gap by running a second KSP round per target.
- **Full JMH benchmark module**. The current `KompactReadBitsBenchmarkTest` covers the shape (100,000 warmup + measure + value check + ns/call bound) but a proper JMH subproject with `-prof gc` and `assertAllocations` is a follow-up.

These are scope expansions, not spec drift. They each support the locked destination without changing it.
