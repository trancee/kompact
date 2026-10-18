# Design and trade-offs

Kompact gives you two ways to say where a field lives on the wire, and two ways
to decode it. This page explains why both exist, what each one asks of you,
and who owns the bytes. For signatures, see the
[API reference](api-reference.md). For step-by-step tasks, see the
[how-to guides](how-to/README.md).

## Fixed positions or sequential fields

A fixed-layout model gives every field a stable bit offset. That suits a
small record whose fields never move. A reader jumps straight to any field,
and the generated view checks the minimum buffer size once, when you create
it.

A string, blob, nested message, or repeat has no fixed size, so the framed
layout reads fields in order. A string, blob, or nested message starts with a
length prefix that holds its size in bytes. A repeat starts with a count
prefix, and each variable-size element carries its own length prefix. The
reader consumes one bounded region before it moves to the next field.

Kompact chose this sequential format over an offset table on purpose. An
earlier field can move every later field, so a framed schema declares a
contiguous `order` instead of absolute offsets. The prefixes make every
boundary explicit. They do not give you random access to a field, and they do
not make a schema change compatible.

Use a [fixed-layout schema](how-to/define-message.md) when offsets are stable.
Use a [sequential framed schema](how-to/define-framed-schema.md) when a field
has a variable size.

## Generated code is common Kotlin

Both schema kinds are regular classes annotated with `@KompactModel`. The
`ch.trancee.kompact.codegen` Gradle plugin runs the KSP processor once over
`commonMain`. For a schema `Name`, the processor writes one common file,
`NameView.kt`, that contains:

- `NameView`, a `@JvmInline` value class for a fixed layout or a regular class
  for a framed layout.
- `MutableNameView`, only for a fixed layout declared with
  `@KompactModel(mutable = true)`.
- `NameViewHolder` and its `decodeInto` and `encodeFrom` extension functions.
  A framed holder also gets helper functions that decode a nested field or one
  repeat element.

Neither the runtime nor the generated code uses `expect` or `actual`, so the
generated sources need no platform-specific source sets and no compiler
flags. [ADR-0009](adr/0009-common-only-code-generation.md) records this
decision.

Views are read-only by default. A `MutableNameView` writes through to the
backing `ByteArray`, which makes every change visible to any other reader of
that array. [ADR-0006](adr/0006-immutable-default-models.md) explains why
mutation is the exception.

## Views for convenience, holders for reuse

A generated view is the easy path. `NameView.decode(raw)` returns a
`KompactFrameResult`, and the view's properties return decoded values. A
framed view allocates the view object, and it allocates a `String`, a
`ByteArray` copy, or a `KompactRepeatedView` when you read those properties.

A holder is the path for code that decodes many frames and wants to reuse
memory. You create the `KompactCursor`, the `KompactByteRange` objects, the
`KompactRepeatWorkspace` storage, and the holder once. Then each
`decodeInto` or `encodeFrom` call fills that state and returns a
`KompactStatus`. The status is an enum entry, so returning it does not
allocate. The cursor stores the failing bit offset and a detail value in
primitive fields.

A framed holder's `decodeInto` and `encodeFrom` take a second, distinct probe
cursor. The holder first walks the whole frame with the probe cursor. It
changes the holder fields or the destination bytes only after every field
passes. A failure in the last field therefore leaves no half-written holder
or frame. The cost is that a variable-width repeat is traversed twice. Kompact
accepts that cost in exchange for a holder that is never half-updated.

## Keep a decode inside its boundary

Bytes from a transport or a file can be truncated or malformed. Kompact
separates two kinds of failure:

- Malformed input is a property of the received bytes. A checked read
  returns it as a `KompactDecodeError`, such as `BoundsError` or
  `InvalidUtf8`. A cursor reports it as a `KompactStatus` whose
  `decodeError` is the matching `KompactDecodeError`.
- A caller-contract error is a bug in the calling code, such as a bit width
  of 65 or a prefix width of 12. The runtime throws
  `IllegalArgumentException`. A cursor reports it as a status with a `null`
  `decodeError`, such as `INVALID_WIDTH`.

[ADR-0013](adr/0013-typed-status-and-caller-contract-errors.md) records why
these two kinds stay apart. If `KompactFrame.decode` turned a width bug into
a `Failure`, your code would treat a programming error as bad input from a
peer.

The runtime also keeps three styles of read apart:

- A checked read returns a result, such as `IntResult` or
  `KompactFrameResult`, and never throws for malformed input.
- A throwing read, such as `getOrThrow()`, `readScalarOrThrow`, or a direct
  `KompactFrame` read, throws `KompactDecodeException`. The name or the type
  makes the throw visible.
- A raw read, such as `KompactRuntime.readBits`, does not check bounds. It is
  for layouts whose bounds you already checked.

A framed decode also rejects a frame that is too long. If unread bytes or
non-zero padding bits remain after the last declared field, the decode fails
with `TrailingData`. A receiver can tell an over-long frame from a truncated
one, which fails with `BoundsError` or `BadLengthPrefix`.

Kompact does not retry and has no fallback value. Your application decides
what to do with a failure. See
[How to handle malformed input](how-to/handle-decode-errors.md) for the
choices.

## Borrowed views keep the owner visible

Generated views, `KompactByteSlice`, `KompactByteRange`, and
`KompactRepeatedView` refer to the caller's `ByteArray`. None of them makes a
copy. Kompact avoids copying nested payloads and decoding every repeated
element up front. The cost is that the owner of the array stays responsible
for its lifetime and for any change to it.

If other code changes the array, every view over it sees the change. A
changed length prefix inside a repeat can also invalidate the index that the
view built when you created it. Keep the array unchanged while you use a
borrowed view. When the data needs its own lifetime, copy it with
`KompactByteSlice.toByteArray()` or `KompactByteRange.copyTo`. The
[BLE boundary guide](how-to/integrate-ble.md) applies the same rule to a
transport that keeps a reference to a buffer.

Views do not compare by content either. A fixed-layout view compares by the
identity of its `ByteArray`, and a framed view compares by object identity.
Two views over equal bytes in different arrays are not equal.

## Result representations and performance evidence

The checked API has one concrete result type per value type instead of one
generic `Result<T>`. `IntResult`, `FloatResult`, `BooleanResult`,
`DoubleResult`, and `NestedRegionResult` are value classes that pack the value
and the error into one `Long`. `DoubleResult` has no spare bits, so it
reserves some NaN payloads for errors and returns every successful NaN as the
canonical NaN. `LongResult` is a regular class, so it can hold every `Long`
value without giving up a sentinel
([ADR-0005](adr/0005-full-domain-long-result.md)).

These types describe how a result is stored. They do not promise that a call
allocates nothing. Kotlin boxes a value class when it passes through a
nullable type, a generic type, or an interface, and the actual behavior
depends on the compiler and the platform. Kompact publishes no portable
latency or allocation guarantee. The
[host baselines for repeat indexing](research/repeat-index-benchmark.md) and
the [diagnostic comparison budgets](how-to/benchmark-runtime.md) cover only
selected scalar and repeat calls through caller-owned state on the JVM and
macOS Native. The
[allocation and boxing research note](research/allocation-boxing-measurement.md)
describes the evidence that a performance claim needs.

## Schema evolution is application policy

Framed schemas are positional and strict. The field order and the declared
types form the wire contract, and every field is required. Appending a field
breaks compatibility in both directions. An older reader rejects the extra
bytes as `TrailingData`, and a newer reader fails on a frame that lacks the
new field. Kompact adds no schema-version field. If your application must
accept more than one version, define your own version field and your own
migration rules.

[ADR-0008](adr/0008-framed-generated-views.md) records the framing decision
and its limits. [ADR-0002](adr/0002-defer-versioning-surface-to-v2.md) records
why Kompact deferred schema versioning.
