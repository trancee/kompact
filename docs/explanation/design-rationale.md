# Design rationale

Why Kompact works the way it does. This document explains the trade-offs that shaped
the API — the decisions documented in the locked wayfinder map at
`.scratch/kompact-spec/map.md`.

## Bit-packed, not byte-aligned

The wire format packs fields into the smallest number of bits. A 1-bit boolean takes
one bit; a 4-bit enum takes four bits; a 10-bit counter takes ten. The byte boundary
isn't special — a 10-bit field at offset 4 occupies bits 4..13, which span two bytes
on the wire.

The alternative — fixed-width fields, byte-aligned — wastes up to 7 bits per field
and adds 1 byte per field to every record. For short BLE frames, that overhead is
the difference between one advertisement per connection interval and three. Protobuf
chose bit-packing for the same reason. FlatBuffers chose byte-alignment for offset
random-access — Kompact deliberately gives up random access (Ticket 05: parse-forward
sequential) to recover the bit efficiency.

## Value classes over boxed primitives

The read path needs to be zero-allocation on the hot path. A function that returns
`Int` is fine, but a function that returns `Int?` boxes; a function that returns
`String` allocates; a function that returns `Pair<Int, Int>` allocates a Pair object.

`KompactRuntime.readBits` returns a primitive `Int`. `KompactRead.readUInt8` returns
`IntResult`, which is an `@JvmInline` value class on JVM and a plain value class on
iOS. Either way, the value is held in a primitive register on the success path; no
heap allocation. The cost: a `KompactError` failure code is a small integer packed
into the high bits, not a thrown exception (Ticket 06: never throw on the read path).
Byte offset is not on the fast path (Ticket 08 tradeoff); the opt-in `decodeFull()`
diagnostics path attaches it only on failure.

## Uniform length-prefix width

A Kompact schema with several length-prefixed fields must use the same prefix
width everywhere — 8, 16, or 32 bits, one value per schema. This costs a few bits
of overhead per field (a 4-byte prefix where a 1-byte prefix would suffice) and
buys forward compatibility: an older reader can scan past an unknown trailing
length-delimited field by reading the uniform-width prefix and skipping the
payload. Without the uniformity, an older reader would have to know the new
field's prefix width — and that knowledge is exactly what versioning is supposed
to make unnecessary.

A mixed-prefix schema fails `LayoutModel.uniformPrefixWidthSatisfied()` at
compile time. The cost is fixed (one decision per schema); the benefit is a
single-pass scan for unknown fields.

## Length-prefix, not offset-table

FlatBuffers indexes every field by an absolute byte offset; readers jump to
each field directly. Kompact can't do that with variable-length fields
(strings, blobs, nested) — an offset would have to be recomputed every time a
preceding field's length changes. The alternative — offsets relative to the
start of the parent struct — still require walking the parent to find the field.

Kompact's solution: parse forward. The reader has a cursor. Length-prefixed fields
read their prefix, then their payload, then advance the cursor. Unknown fields
(unknown to the reader) are skipped by their uniform-width prefix. The cost is
sequential access; the benefit is that a single forward scan can decode the
whole stream, and forward compatibility reduces to "skip one prefix + payload".

## Version prefix, not magic number

A 4-byte little-endian `UInt` at the start of every stream is the version. An
older reader that sees a version it doesn't recognize fails fast with
`UnsupportedSchemaVersion` — typed, no silent misread, no guessing. The cost is
4 bytes per stream; the benefit is that a version bump is a real, explicit
event, not a heuristic.

The default supported version set is `{1u}`. A library user overrides it
via `KompactVersionedStream.setSupportedVersions(...)` on the reader. The
writer always emits the version it was compiled with.

## Zero-alloc read, alloc-on-write

The read path is the hot path. It must not allocate. The write path is
construction-time: it builds the buffer, allocates as it grows, then snapshots
the result. Allocating during construction is fine — the calling code is
typically building a single message per event, not in a tight loop.

Kompact's read API is `ByteArray` in, typed result out. The caller owns the buffer
(Ticket 03: "caller-owned `ByteArray` read path"). No defensive copy, no
allocation per field, no boxing. The KSP-generated view is a value class wrapping
the caller's `ByteArray`; each accessor is a `KompactRuntime.readBits(...)` or
`KompactRead.readXxx*(...)` call, nothing else.

## Why hand-written common API, KSP-generated value-class views

The runtime (`KompactRuntime`, `KompactRead`, `KompactWriter`, `KompactVersionedStream`,
`AllocationCounter`, the result types) is hand-written common code. It has to be
correct on every KMP target from the first commit; it's the foundation everything
else is built on.

The KSP processor generates per-schema value-class views (the `expect value class
VehicleTelemetryView(val raw: ByteArray)` and its platform actuals). This is the
boilerplate: for every `@KompactField`, the view exposes a `val foo: T get() =
KompactRead.readUInt*(raw, …)`. A schema with 30 fields would otherwise mean 30
identical-shape accessor declarations; the processor writes them. The runtime
stays small and reviewable; the schemas stay declarative.

The alternative — fully runtime reflection — would either re-introduce allocation
(the boxed `KProperty` lookup) or push a giant macro system onto the build.
KSP 2.x with a deterministic, incremental processor is the middle ground.

## Why LSB-first bit packing

LSB-first matches the way modern CPUs and buses order bytes (little-endian) and
bits (LSB first in shift registers). Protobuf chose LSB-first for the same reason.
MSB-first (network byte order, ASN.1 BER) is the alternative; it's correct but
slightly less natural for the cross-byte-boundary shifts the bit-packed format
requires. The cross-platform zero-allocation constraint pushed LSB-first: every
shift, mask, and `and 0xFF` operation is identical on JVM and Kotlin/Native.

## What's deferred

- **Floats** (Ticket 04). IEEE-754 32/64-bit floats with NaN canonicalization are in the v1
  type set per the spec, but the implementation is deferred. The `KompactField` annotation
  doesn't yet carry an `isFloat` marker; the writer doesn't have `writeFloat32`/`writeFloat64`.
- **iOS KSP per-target actuals** (KSP `#567`). The processor emits the common `expect`; the
  consumer's per-target `actual` is hand-written. A future v2 of the processor can close
  this gap by running a second KSP round per target.
- **JMH benchmark module**. The current `KompactReadBitsBenchmarkTest` covers the shape
  (100,000 warmup + measure + value check + ns/call bound) but a proper JMH subproject
  with `-prof gc` and `assertAllocations` is a follow-up.

These are scope expansions, not spec drift. The locked wayfinder map at
`.scratch/kompact-spec/map.md` records the destination they each support.

## See also

- [How to use Kompact](../how-to-use-kompact.md) — the practitioner view.
- [Runtime reference](../reference/runtime.md), [Result types reference](../reference/result-types.md), [Annotations and processor reference](../reference/annotations-and-processor.md) — the API mirror.
- The locked wayfinder map: `.scratch/kompact-spec/map.md` — the decision trail that led to this design.
