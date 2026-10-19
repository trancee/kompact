# API reference

This page lists every public declaration of the `ch.trancee.kompact:kompact`
runtime and the code that the KSP processor generates from it. It describes
the current checkout, version `0.11.0-SNAPSHOT`. To upgrade from `0.9.0`, see
[API changes in 0.10.0](#api-changes-in-0100).

The committed ABI dumps are the source of truth for this list:
[`kompact/api/jvm/kompact.api`](../kompact/api/jvm/kompact.api) for the JVM
and Android, and [`kompact/api/kompact.klib.api`](../kompact/api/kompact.klib.api)
for `androidNativeArm64`, `iosArm64`, and `iosSimulatorArm64`. Dokka
generates a page for every symbol, with the full KDoc, into
[`kompact/docs/api/`](../kompact/docs/api/index.md).

## Package `ch.trancee.kompact.annotations`

The schema annotations have source retention. They are not a runtime
dependency of generated code.

### `@KompactModel`

Marks a regular class as a schema. Target: class.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `mutable: Boolean` | `false` | Also generate `Mutable<Name>View` for a fixed-layout model. The processor rejects `mutable = true` on a framed model. |
| `framed: Boolean` | `false` | Generate a sequential framed view instead of a fixed-layout view. |

### `@KompactField`

Describes one property of a schema. Target: property.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `bitOffset: Int` | `0` | Fixed layout only. Zero-based, LSB-first start bit. Fields must not overlap. Gaps are allowed. |
| `bitWidth: Int` | `0` | Width of a scalar field or a scalar repeat element, 1 to 64 bits. |
| `signed: Boolean` | `false` | `true` for two's complement, `false` for unsigned. |
| `lengthPrefixWidth: Int` | `8` | Framed only. Width in bits (8, 16, or 32) of the little-endian byte-count prefix of a `String`, `ByteArray`, nested model, or variable-size repeat element. |
| `repeatCountWidth: Int` | `8` | Framed only. Width in bits (8, 16, or 32) of the little-endian count prefix of a `List` field. |
| `order: Int` | `-1` | Framed only. Zero-based position of the field. Orders must be contiguous from zero. |

A fixed-layout field has the type `Boolean`, `Int`, `Long`, `Float`, or
`Double`. A framed field can also have the type `String`, `ByteArray`, a
class annotated with `@KompactModel(framed = true)`, or a `List` of any of
these types. A `List` cannot contain another `List`. The processor infers a
nested field from its type.

### `@KompactPreview`

A `@RequiresOptIn` marker at level `WARNING` on `@KompactModel`,
`@KompactField`, and every generated declaration. Code that uses these
declarations without `@OptIn(KompactPreview::class)` gets a compiler warning.
The runtime reader and writer APIs do not carry this marker.

## Package `ch.trancee.kompact.runtime`

All bit offsets are LSB-first. Bit 0 is the least-significant bit of the
first byte.

### `KompactWriter`

A growable, forward-only builder of wire bytes. Create it with
`KompactWriter()`.

| Member | Behavior |
| --- | --- |
| `writeBits(bitWidth: Int, value: Int)` | Appends the low `bitWidth` bits of `value`. `bitWidth` must be 1 to 31. |
| `writeBitsLong(bitWidth: Int, value: Long)` | Appends the low `bitWidth` bits of `value`. `bitWidth` must be 1 to 64. |
| `writeBool(value: Boolean)` | Appends one bit. |
| `writeScalar(type: ScalarType, value: Long)` | Appends the low `type.bitWidth` bits of `value`. It does not check that `value` fits. |
| `writeString(countWidth: Int, value: String)` | Appends a length prefix and the UTF-8 bytes of `value`. |
| `writeBlob(countWidth: Int, bytes: ByteArray)` | Appends a length prefix and `bytes`. |
| `writeNested(lengthPrefixWidth: Int = 16, block: KompactWriter.() -> Unit)` | Reserves a length prefix, runs `block` on this writer, pads the payload to a whole byte, and then writes the payload length into the prefix. If `block` throws or the length does not fit the prefix, the writer returns to its state before the call. |
| `writeNested(lengthPrefixWidth: Int, slice: KompactByteSlice)` | Appends a length prefix and the bytes of `slice`. |
| `writeNested(lengthPrefixWidth: Int, raw: ByteArray, start: Int, end: Int)` | Appends a length prefix and `raw[start until end]`. |
| `writeSlice(slice: KompactByteSlice)` | Appends the bytes of `slice` with no prefix. |
| `writeRepeated(count: Int, countWidth: Int = 8, block: KompactWriter.() -> Unit)` | Appends a count prefix and then runs `block` `count` times. |
| `build(): ByteArray` | Returns a copy of the bytes written so far. Unused bits of the last byte are zero. The writer keeps its content. |
| `reset()` | Discards the content and keeps the grown buffer. |

A prefix width must be 8, 16, or 32, and the length or count must fit it.
Otherwise the method throws `IllegalArgumentException` before it changes the
writer.

### `KompactRuntime`

An object with stateless reads and writes on a caller-owned `ByteArray`.

The raw methods do not check bounds or return a result. Use them only for
offsets and widths that you already validated.

| Raw member | Behavior |
| --- | --- |
| `readBits(raw: ByteArray, bitOffset: Int, bitWidth: Int): Int` | Reads 1 to 31 bits. |
| `readBitsLong(raw: ByteArray, bitOffset: Int, bitWidth: Int): Long` | Reads 1 to 64 bits. |
| `readBitsBoolean(raw: ByteArray, bitOffset: Int): Boolean` | Reads one bit. |
| `writeBits(raw: ByteArray, bitOffset: Int, bitWidth: Int, value: Int)` | Writes the low 1 to 31 bits of `value`. |
| `writeBitsLong(raw: ByteArray, bitOffset: Int, bitWidth: Int, value: Long)` | Writes the low 1 to 64 bits of `value`. |
| `writeBitsBoolean(raw: ByteArray, bitOffset: Int, value: Boolean)` | Writes one bit. |
| `fits(raw: ByteArray, bitOffset: Int, bitWidth: Int): Boolean` | Returns `true` when `bitOffset >= 0`, `bitWidth >= 1`, and the bits end inside `raw`. |

The checked methods return a failure with `KompactDecodeError.BoundsError`
when the read does not fit in `raw`. The `OrThrow` methods throw
`KompactDecodeException` with the same error instead.

| Checked member | Throwing member | Value |
| --- | --- | --- |
| `readBool(raw, bitOffset): BooleanResult` | `readBoolOrThrow(raw, bitOffset): Boolean` | One bit. |
| `readScalar(raw, bitOffset, type: ScalarType): IntResult` | `readScalarOrThrow(raw, bitOffset, type): Int` | `type.bitWidth` bits, sign-extended when `type.signed`. Throws `IllegalArgumentException` when `type.bitWidth` is greater than 32. |
| `readScalarAsLong(raw, bitOffset, type: ScalarType): LongResult` | `readScalarAsLongOrThrow(raw, bitOffset, type): Long` | 1 to 64 bits, sign-extended when `type.signed`. |
| `readFloat(raw, bitOffset): FloatResult` | `readFloatOrThrow(raw, bitOffset): Float` | 32 IEEE 754 bits. |
| `readDouble(raw, bitOffset): DoubleResult` | `readDoubleOrThrow(raw, bitOffset): Double` | 64 IEEE 754 bits. A NaN reads as the canonical NaN. |

### `ScalarType`

A value class that holds a bit width and a sign flag in one `Int`.

| Member | Meaning |
| --- | --- |
| `ScalarType.of(bitWidth: Int, signed: Boolean)` | Creates a type. Throws `IllegalArgumentException` when `bitWidth` is outside 1 to 64. |
| `bitWidth: Int` | Width in bits. |
| `signed: Boolean` | `true` for two's complement. |
| `INT_8`, `INT_16`, `INT_32`, `INT_64` | Signed constants in the companion. |
| `UINT_8`, `UINT_16`, `UINT_32`, `UINT_64` | Unsigned constants in the companion. |
| `BOOL` | One unsigned bit. |

### Scalar result types

`IntResult`, `FloatResult`, `BooleanResult`, and `DoubleResult` are value
classes. `LongResult` is a regular class, so it can hold every `Long` value.
Each type has these members:

| Member | Meaning |
| --- | --- |
| `isSuccess: Boolean` | `true` when the result holds a value. |
| `isFailure: Boolean` | `true` when the result holds an error. |
| `error: KompactDecodeError?` | The error, or `null` on success. |
| `getOrThrow()` | Returns the value, or throws `KompactDecodeException` with `error`. |
| `success(value)` and `failure(error)` | Companion factories. The constructors are private. |
| `getOrElse(fallback: (KompactDecodeError) -> T): T` | Inline extension. Returns the value, or the result of `fallback`. |
| `map(transform: (T) -> T)` | Inline extension. Returns a result of the same type with the transformed value. A failure is returned unchanged. |

`LongResult` also has `value: Long?`, which is `null` on failure. Reading it
boxes the `Long`. `FloatResult` keeps the exact bits of a NaN. `DoubleResult`
reserves some NaN payloads for errors, so a successful NaN becomes the
canonical NaN.

### `KompactDecodeError`

A sealed interface. Every case is a `data object`, so `toString()` returns the
case name.

| Case | Meaning |
| --- | --- |
| `BoundsError` | A read would end past the input or the region. |
| `BadLengthPrefix` | A length or count prefix cannot be read, or it exceeds the remaining input. |
| `TruncatedNested` | A nested region or repeat element ends before its declared content. |
| `InvalidUtf8` | A string field holds malformed UTF-8. |
| `TrailingData` | A complete framed decode left unread bytes, or non-zero padding bits in the last partial byte, after the declared fields. |

### `KompactDecodeException`

A `RuntimeException` with the property `error: KompactDecodeError`.
`getOrThrow()` and the direct `KompactFrame` reads throw it.

### `KompactFrame`

A bounded, forward-only reader over a borrowed `ByteArray`. A read advances
the frame. A direct read throws `KompactDecodeException` for malformed input
and `IllegalArgumentException` for an invalid width.

| Member | Behavior |
| --- | --- |
| `raw: ByteArray`, `start: Int`, `end: Int` | The borrowed array and the byte bounds of the frame. |
| `readBits(width: Int): Long` | Reads 1 to 64 bits. Fails with `BoundsError`. |
| `readString(prefixWidth: Int): String` | Reads a length-prefixed UTF-8 string. Fails with `BadLengthPrefix` or `InvalidUtf8`. |
| `readBlob(prefixWidth: Int): KompactByteSlice` | Borrows a length-prefixed byte region without copying. Fails with `BadLengthPrefix`. |
| `readNested(prefixWidth: Int): KompactByteSlice` | Borrows a length-prefixed nested payload without copying. Fails with `BadLengthPrefix`. |
| `readRepeated(countWidth: Int, elementWidth: Int, elementPrefixWidth: Int, decodeElement: (KompactFrame) -> T): KompactRepeatedView<T>` | Validates a count-prefixed repeat and returns a lazy view. Set exactly one of `elementWidth` (1 to 64) and `elementPrefixWidth` (8, 16, or 32). Fails with `BadLengthPrefix` or `TruncatedNested`. |
| `requireComplete()` | Fails with `TrailingData` when a whole unread byte or a non-zero padding bit remains. |
| `KompactFrame.decode(raw, start = 0, end = raw.size): KompactFrameResult<KompactFrame>` | Returns a frame over the region, or `Failure(BoundsError)` for invalid bounds. |
| `KompactFrame.decode(raw, start = 0, end = raw.size, block: (KompactFrame) -> T): KompactFrameResult<T>` | Runs `block`, then calls `requireComplete()`. Returns a `KompactDecodeException` as `Failure`. Other exceptions, including `IllegalArgumentException`, propagate. |

### `KompactFrameResult<T>`

A sealed class with the cases `Success(value: T)` and
`Failure(error: KompactDecodeError)`. Both cases have `error` and
`getOrThrow()`. `Failure.getOrThrow()` throws `KompactDecodeException`.

### `KompactRepeatedView<T>`

A read-only, lazy `List<T>` that `KompactFrame.readRepeated` and generated
framed views return. A fixed-width element is located by arithmetic. A
variable-size element is located from the nearest sparse checkpoint, one per
64 elements.

| Member | Behavior |
| --- | --- |
| `size: Int` | The element count. |
| `get(index: Int): T` | Decodes the element. Throws `KompactDecodeException` for malformed content and `IndexOutOfBoundsException` for a bad index. |
| `getResult(index: Int): KompactFrameResult<T>` | Decodes the element and returns malformed content as `Failure`. |
| `getElementSlice(index: Int): KompactFrameResult<KompactByteSlice>` | Borrows the payload of a length-prefixed element. Returns `Failure(BoundsError)` for a fixed-width element. |

### `KompactByteSlice`

A borrowed, byte-aligned region with `raw: ByteArray`, `start: Int`,
`end: Int`, and `size: Int`. Its constructor is internal.
`toByteArray()` returns an independent copy.

### `KompactFraming`

An object with stateless length-prefix helpers.

| Member | Behavior |
| --- | --- |
| `VALID_PREFIX_WIDTHS: Set<Int>` | `setOf(8, 16, 32)`. |
| `INVALID_LENGTH_PREFIX: Int` | `-1`, the value `readLengthPrefix` returns when the prefix does not fit in `raw`. |
| `readLengthPrefix(raw: ByteArray, bitOffset: Int, bitWidth: Int): Int` | Reads a little-endian byte count, or returns `INVALID_LENGTH_PREFIX`. |
| `readLengthPrefixOrThrow(raw: ByteArray, bitOffset: Int, bitWidth: Int): Int` | Reads a little-endian byte count, or throws `KompactDecodeException` with `BadLengthPrefix`. |
| `writeLengthPrefix(raw: ByteArray, bitOffset: Int, bitWidth: Int, length: Int)` | Writes a little-endian byte count. Throws `IllegalArgumentException` before it writes when `length` is negative or does not fit. |
| `readNested(raw: ByteArray, bitOffset: Int, prefixBitWidth: Int): NestedRegionResult` | Reads a prefix and returns the payload region that follows it, or a `BadLengthPrefix` failure. |

Every method throws `IllegalArgumentException` when the width is not 8, 16,
or 32.

### `NestedRegionResult`

A value class that `KompactFraming.readNested` returns. It has `isSuccess`,
`isFailure`, `error`, `startBit: Int`, and `bitLength: Int`. `startBit` and
`bitLength` throw `KompactDecodeException` on a failure. The companion has
`success(startBit: Int, bitLength: Int)` and `failure(error)`.

### `KompactCursor`

A reusable, bounded bit cursor over a caller-owned buffer. Every operation
returns a `KompactStatus` and stores it in `status`. A failed operation leaves
the position, the destination bytes, and any bound range or workspace
unchanged. A cursor is not thread-safe.

| State | Meaning |
| --- | --- |
| `buffer: ByteArray` | The bound buffer. |
| `startBit: Int`, `endBit: Int` | The inclusive start and exclusive end of the region. |
| `position: Int` | The current bit position, between `startBit` and `endBit`. |
| `valueBits: Long` | The value from the last successful read. |
| `status: KompactStatus` | The status of the last operation. |
| `errorBitOffset: Int`, `errorDetail: Int` | The bit position and a detail value for the last failure. |

| Group | Members |
| --- | --- |
| Bind | `reset(buffer, startBit = 0, position = startBit, endBit = buffer.size * 8)`, `resetByteRange(buffer, startByte, endByte)` |
| Read and skip bits | `readBits(bitWidth)`, `skipBits(bitCount)`, `ensureAvailable(bitCount)` |
| Write bits | `writeSigned(bitWidth, value: Long)`, `writeUnsigned(bitWidth, value: Long)`, `writeUnsigned(bitWidth, value: ULong)`, `writeZeros(bitCount)`, `writeBitsUnchecked(bitWidth, value)` |
| Validate without writing | `validateSigned(bitWidth, value: Long)`, `validateUnsigned(bitWidth, value: Long)`, `validateUnsigned(bitWidth, value: ULong)` |
| Byte ranges | `readByteRange(prefixWidth, range)`, `readUtf8Range(prefixWidth, range)`, `skipByteRange(prefixWidth, validateUtf8 = false)`, `writeByteRange(prefixWidth, range)`, `preflightByteRangeWrite(prefixWidth, range, validateUtf8 = false)`, `writeRawByteRange(range)`, `preflightRawByteRange(range)`, `captureRegion(range, validateUtf8 = false)` |
| Nested regions | `readNested(prefixWidth, nestedCursor)`, `writeNested(prefixWidth, payloadByteLength, nestedCursor)` |
| Repeats | `readFixedRepeat(countPrefixWidth, elementBitWidth, workspace)`, `readVariableRepeat(countPrefixWidth, elementPrefixWidth, workspace)`, `readRepeatedElement(index, workspace, elementCursor)`, `skipFixedRepeat(countPrefixWidth, elementBitWidth, workspaceCapacity)`, `skipVariableRepeat(countPrefixWidth, elementPrefixWidth, workspaceCapacity)` |
| Two-cursor helpers | `ensureDistinct(other)`, `copyErrorFrom(source)` |

These rules apply to specific members:

- `writeSigned` and `writeUnsigned` return `INVALID_VALUE` when the value does
  not fit the width. `writeBitsUnchecked` writes the low bits and drops the
  rest.
- `readUtf8Range` validates UTF-8 without creating a `String`.
- `writeByteRange` returns `INVALID_ARGUMENT` for a range that overlaps the
  destination in the same buffer.
- `writeNested` returns `UNALIGNED_REGION` at a position that is not on a byte
  boundary, and `BOUNDS_ERROR` when the prefix or payload does not fit.
- `readFixedRepeat` and `readVariableRepeat` need a workspace with at least
  `ceil(count / 64)` checkpoint slots. An empty repeat needs none. With fewer
  slots, they return `WORKSPACE_TOO_SMALL`.
- `readRepeatedElement` binds `elementCursor` to one indexed element and does
  not move the parent cursor. The workspace must still be bound to this
  cursor's buffer.
- `ensureDistinct` returns `INVALID_ARGUMENT` when both arguments are the same
  cursor.

### `KompactStatus`

An enum of cursor outcomes. `decodeError: KompactDecodeError?` maps a
malformed-input status to its decode error and is `null` for `OK` and for
caller-contract statuses.

| Entry | `decodeError` | Meaning |
| --- | --- | --- |
| `OK` | `null` | The operation succeeded. |
| `INVALID_BOUNDS` | `null` | A reset region is outside the buffer. |
| `INVALID_WIDTH` | `null` | A bit width or prefix width is not supported. |
| `BOUNDS_ERROR` | `BoundsError` | The operation would cross the cursor's region. |
| `INVALID_VALUE` | `null` | A value does not fit the wire width. |
| `BAD_LENGTH_PREFIX` | `BadLengthPrefix` | A length prefix is malformed or too large for cursor offsets. |
| `UNALIGNED_REGION` | `null` | A byte operation started off a byte boundary. |
| `INVALID_ARGUMENT` | `null` | Two cursors, or a source and destination buffer, alias in a way the operation forbids. |
| `INVALID_UTF8` | `InvalidUtf8` | A string payload holds malformed UTF-8. |
| `WORKSPACE_TOO_SMALL` | `null` | The repeat workspace has too few checkpoint slots. |
| `TRUNCATED_INPUT` | `TruncatedNested` | A repeat element is truncated or crosses its region. |

### `KompactByteRange`

A reusable, borrowed half-open byte range. `KompactByteRange(buffer)` selects
the whole buffer.

| Member | Behavior |
| --- | --- |
| `buffer: ByteArray`, `start: Int`, `end: Int`, `size: Int` | The bound buffer and range. |
| `reset(buffer, start = 0, end = buffer.size): Boolean` | Rebinds the range. Returns `false` and keeps the old binding for invalid bounds. |
| `copyTo(destination: ByteArray, destinationOffset: Int = 0): Boolean` | Copies the bytes into `destination`. Returns `false` and copies nothing when they do not fit. |

### `KompactRepeatWorkspace`

Caller-owned checkpoint storage for one repeat. Create it with
`KompactRepeatWorkspace(checkpointStorage: IntArray)`.

| Member | Meaning |
| --- | --- |
| `CHECKPOINT_INTERVAL: Int` | `64`, the most elements between two checkpoints. |
| `capacity: Int` | The number of checkpoint slots. |
| `count: Int` | The element count of the last successful repeat read. |
| `startBit: Int`, `endBit: Int` | The bit bounds of the last repeat. |

The workspace keeps a reference to the buffer of the last successful repeat
read. Reusing it for another repeat replaces that binding.

## Generated code

For a schema class `Name`, the processor writes one common file `NameView.kt`.
Every generated declaration carries `@KompactPreview`.

### Fixed-layout view

`@JvmInline value class NameView(val raw: ByteArray)`:

| Member | Behavior |
| --- | --- |
| Constructor | Throws `IllegalArgumentException` when `raw` is shorter than the layout. |
| One `val` per field | Reads the field from `raw` with no bounds check. |
| `copy(...)` | Returns a new view with a new array. Each parameter defaults to the current field value. |
| `NameView.decode(raw): KompactFrameResult<NameView>` | Returns `Failure(BoundsError)` when `raw` is too short, otherwise wraps `raw` without copying. |
| `NameView.create(...)` | Encodes the field values into a new array. |

`@KompactModel(mutable = true)` adds `MutableNameView`, which has a `var` per
field. Its setters write into `raw`.

### Framed view

`class NameView(raw: ByteArray, start: Int, end: Int)`:

| Member | Behavior |
| --- | --- |
| Constructor | Decodes and validates the whole frame. Throws `KompactDecodeException` for malformed input, including `TrailingData`. |
| `raw`, `start`, `end` | The borrowed array and the byte bounds. |
| Scalar or `String` field | A `val` of the field type. |
| `ByteArray` field `x` | `xSlice: KompactByteSlice` borrows the bytes. `x: ByteArray` returns a copy. |
| Nested field | A `val` of the nested model's view type. |
| `List<T>` field | A `val` of type `KompactRepeatedView<T>`. |
| `copy(...)` | Returns a new view with new bytes. |
| `NameView.decode(raw, start = 0, end = raw.size): KompactFrameResult<NameView>` | Returns a `KompactDecodeException` as `Failure`. |
| `NameView.create(...)` | Encodes the field values. A `List` field takes a `List<T>`. |

### Holder

`class NameViewHolder` holds one `var` per scalar field and caller-supplied
`KompactByteRange` and `KompactRepeatWorkspace` objects for the other fields.
Its operations are extension functions that return `KompactStatus`:

| Function | Layout | Behavior |
| --- | --- | --- |
| `decodeInto(cursor)` | Fixed | Reads every field at the cursor position into the holder. |
| `encodeFrom(cursor)` | Fixed | Validates every value, then writes them at the cursor position. |
| `decodeInto(cursor, probeCursor)` | Framed | Walks the whole frame with `probeCursor`, then reads into the holder. A failure leaves the holder unchanged. |
| `encodeFrom(cursor, probeCursor)` | Framed | Validates every value and the destination capacity with `probeCursor`, then writes. A failure leaves the destination unchanged. |
| `decode<Field>Into(...)` | Framed | Decodes a nested field into a child holder. |
| `decode<Field>Element(index, sourceCursor, elementCursor)` | Framed | Decodes one repeat element into the holder's element state. |
| `decode<Field>ElementInto(...)` | Framed | Decodes one nested repeat element into a child holder. |

`probeCursor` must be a different object from `cursor`. Otherwise the call
returns `INVALID_ARGUMENT`.

## Ownership and allocation

These rules hold across the API:

- Generated views, `KompactByteSlice`, `KompactByteRange`,
  `KompactRepeatedView`, holders, and workspaces keep a reference to the
  caller's `ByteArray`. A change to that array is visible through all of them.
  A change to a length prefix inside a repeat invalidates the index built when
  the view or workspace read the repeat.
- `KompactByteSlice.toByteArray()`, a generated `ByteArray` property, and
  `copy(...)` return new arrays. `KompactByteRange.copyTo` copies only into the
  array that you pass.
- Generated views do not compare by content. A fixed-layout view compares by
  the identity of `raw`. A framed view compares by object identity.
- A framed holder's `decodeInto` traverses a variable-width repeat twice: once
  to validate it and once to build the workspace index.
- The cursor, ranges, holders, and workspaces are the API for code that
  avoids allocation. Conveniences that return a `String`, a copy, a view, or a
  `KompactFrameResult` allocate. Whether a call allocates depends on the
  target and the call site. The
  [allocation research note](research/allocation-boxing-measurement.md)
  records bounded Kotlin/Native GC measurements of selected operations on
  Android Arm64 and iOS Arm64 test binaries. Those measurements are not a
  general zero-allocation guarantee. The
  [repeat-index host baselines](research/repeat-index-benchmark.md) and the
  [diagnostic budgets](how-to/benchmark-runtime.md) cover only their
  documented JVM and macOS Native workloads.

## API changes in 0.10.0

Version `0.10.0` changed these parts of the `0.9.0` API:

- `KompactCursor` operations returned `Int` status codes from the
  `KompactCursor.STATUS_*` constants. They now return `KompactStatus`.
- `KompactDecodeError` had `UnknownEnumCode` and no `TrailingData`.
- `KompactWriter.reset()` did not exist.
- `KompactRuntime.decodeFull*`, `DetailedResult`, `DetailedDecodeError`,
  `KompactFraming.readNestedOrThrow`, the `ch.trancee.kompact.Kompact` alias
  object, and the sample `ch.trancee.kompact.generated` package existed.
- `@KompactField` had `isNested`, `enumWidth`, and `defaultValue`.
- The result value classes had public constructors and a public `packed`
  property.

[ADR-0010](adr/0010-minimal-checked-result-surface.md),
[ADR-0011](adr/0011-inferred-nested-fields-and-checked-fixed-decode.md), and
[ADR-0013](adr/0013-typed-status-and-caller-contract-errors.md) record these
changes.

## Regenerate the Dokka reference

The KDoc in the runtime sources is the source of the Dokka pages. To
regenerate the committed pages, run:

```bash
./gradlew :kompact:dokkaGeneratePublicationMarkdown
```

CI checks the generated pages for drift on macOS.
