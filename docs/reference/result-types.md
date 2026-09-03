# Result types

Kompact read accessors return typed `expect/actual value class` results, never throw on the read path. Each scalar kind has its own result class; all wrap a single `Long` that packs the value, an ok-flag, a compact error code, and (where applicable) a raw enum code.

All result classes are zero-allocation: the `Long` is a primitive on the success path; on the failure path the failure is a primitive `Long` carrying the error code.

## Packed `Long` layout

For every result class except `LengthReadResult`, the same packing convention applies:

- Bit 60 = ok flag (1 = success, 0 = failure).
- Bits 61..63 = compact error code (0..7).
- Low 56 bits = the value on success, or 0 on failure.

For `LengthReadResult`, the layout is different (it carries both a length and a bit offset):

- Bit 60 = ok flag.
- Bits 61..63 = error code.
- Low 28 bits = `length`.
- Bits 28..59 = `afterPrefix` (bit offset after the prefix).

## Per-class API

All result classes expose the same shape:

- `packed: Long` — the underlying primitive (use only when interoperating with FFI or packing into a larger protocol).
- `isOk: Boolean` / `isError: Boolean`.
- `errorCode: Int` — one of the `KompactError` constants below.
- `value: T` — the decoded value (meaningful only when `isOk == true`).
- A companion `success(value)` and `failure(errorCode)` factory.

The `expect` declaration lives in commonMain; the platform `actual` adds `@JvmInline` on the JVM and is plain on iOS. The packing is identical; the `@JvmInline` annotation is a JVM-only language constraint, not a behavioral one.

### `BooleanResult` (1-bit)

- `value: Boolean`.

### `ByteResult` (8-bit signed)

- `value: Byte`.

### `IntResult` (1..32-bit, signed or unsigned)

- `value: Int`.
- For widths 1..8 the value is zero-extended; for 9..32 the value is the raw assembled bits (caller decides sign vs unsigned based on the schema).

### `LongResult` (1..64-bit, signed or unsigned)

- `value: Long`.

### `LengthReadResult` (internal helper for `KompactRead`)

- `value: Pair<Int, Int>` — `(length, afterPrefix)`.
- Used by the length-prefixed read APIs internally. Not typically returned to user code.

### `StringResult`, `BlobResult`, `NestedResult`, `RepeatedResult` (length-prefixed)

- `StringResult.value: String`
- `BlobResult.value: ByteArray`
- `NestedResult.value: ByteArray` — the sub-region's bytes; the caller wraps it in a generated nested view.
- `RepeatedResult.value: Pair<Int, List<ByteArray>>` — `(count, elements)`.

`StringResult`, `BlobResult`, and `NestedResult` use the same `String` / `ByteArray` heap-backed value, so they are **not** zero-allocation (the value itself is allocated). The packing is still zero-allocation. `RepeatedResult` is also not zero-allocation (allocates the `List<ByteArray>`).

## `KompactError` (object, commonMain)

Compact error codes packed into every result's high bits. Code 0 means success; non-zero discriminates the typed error. The full list:

| Constant | Value | When it's returned |
|---|---|---|
| `KompactError.Ok` | 0 | Success (never returned from a `failure(...)` factory). |
| `KompactError.BoundsError` | 1 | The buffer is too short for the requested read, or `widthBits` is not in `{8, 16, 32}`. |
| `KompactError.BadLengthPrefix` | 2 | A length-prefix claims more bytes than the buffer has left (strings, blobs, repeated), or the prefix's `widthBits` is invalid for `writeLengthPrefix`. |
| `KompactError.TruncatedNested` | 3 | A nested sub-region's length prefix exceeds the buffer. |
| `KompactError.UnknownEnumCode` | 4 | The wire ordinal is outside the enum's declared width (reserved for future enum-typed read accessors). |
| `KompactError.UnsupportedSchemaVersion` | 5 | The top-level version prefix is outside the supported set. |

All failures are typed — there is no global "exception" or `null` sentinel. A reader that wants to react categorically pattern-matches on the `errorCode`.

## Pattern: discriminating a result

```kotlin
when (val r = KompactRead.readUInt8(buf, 0)) {
    is IntResult.Success -> use(r.value)
    is IntResult.Failure -> when (r.errorCode) {
        KompactError.BoundsError    -> retryWithLargerBuffer()
        KompactError.BadLengthPrefix -> skipField()           // Ticket 09 forward compat
        else                       -> fail("unexpected: ${r.errorCode}")
    }
}
```
