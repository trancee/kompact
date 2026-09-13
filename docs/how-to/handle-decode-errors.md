# How to handle decode errors

Goal: recover from a malformed wire buffer (truncated, bad length
prefix, unknown enum code) without throwing on the hot path, and
without silently misreading.

Kompact's read API is **typed-result-shaped** — every checked accessor
returns a `*Result` value class instead of a primitive and never
throws on success. `getOrThrow()` is the only call that throws, and
only on failure. The error is carried as a [`KompactDecodeError`](../api-reference.md#kompactdecodeerror)
sealed-class instance.

## 1. Inspect the result with `isSuccess` / `isFailure`

The simplest pattern is the `Result`-style branching:

```kotlin
import ch.trancee.kompact.runtime.KompactDecodeError
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.ScalarType

val r = KompactRuntime.readScalar(raw, 0, ScalarType.of(10, signed = false))
if (r.isSuccess) {
    val speed: Int = r.getOrThrow()          // safe — we just checked
    println("speed=$speed")
} else {
    when (val err = r.error) {
        KompactDecodeError.BoundsError      -> println("buffer too short")
        KompactDecodeError.TruncatedNested  -> println("nested truncated")
        is KompactDecodeError.UnknownEnumCode -> println("unknown enum ${err.rawCode}")
        KompactDecodeError.BadLengthPrefix  -> println("length prefix overruns")
    }
}
```

The four subtypes are
[documented here](../api-reference.md#kompactdecodeerror).

## 2. Use `getOrElse` for a single fallback

`getOrElse` mirrors `kotlin.Result.getOrElse` — same shape, no boxing
on the success path:

```kotlin
val speed: Int = KompactRuntime.readScalar(raw, 0, ScalarType.of(10, signed = false))
    .getOrElse { err ->
        // log the error, then return a safe default
        logger.warn("decode failed: $err")
        0
    }
```

`getOrElse` is defined as an extension on every result value class —
see [`api-reference.md#extension-functions`](../api-reference.md#extension-functions).

## 3. Chain with `map`

`map` transforms the success value without touching the error — same
as `kotlin.Result.map`:

```kotlin
val r = KompactRuntime.readScalar(raw, 0, ScalarType.of(10, signed = false))
    .map { speed -> speed.coerceIn(0, 200) }   // still IntResult, errors pass through

// Later, in the same call site:
val safeSpeed = r.getOrElse { 0 }
```

## 4. Throw on error with `getOrThrow` (when you want exceptions)

If you prefer exceptions over pattern-matching, every accessor has an
`OrThrow` variant:

```kotlin
val speed: Int = KompactRuntime.readScalarOrThrow(raw, 0, ScalarType.of(10, signed = false))
// throws KompactDecodeException on bounds / enum / prefix error
```

The throwing variants are sugar — they call `getOrThrow()` internally.
They exist for callers that prefer try/catch over `when` chains.

## 5. Nested regions

`KompactFraming.readNested` returns a `NestedRegionResult` (a
zero-alloc result type for nested reads) with the same
`isSuccess` / `error` shape:

```kotlin
import ch.trancee.kompact.runtime.KompactFraming
import ch.trancee.kompact.runtime.NestedRegionResult
import ch.trancee.kompact.runtime.NestedRegion

val region: NestedRegionResult = KompactFraming.readNested(raw, bitOffset = 24, prefixBitWidth = 16)
when {
    region.isSuccess -> {
        val (startBit, bitLength) = region.getOrThrow()  // NestedRegion = Pair<Int, Int>
        // read the inner record starting at startBit, up to bitLength bits
    }
    else -> when (val err = region.error) {
        KompactDecodeError.BadLengthPrefix  -> logger.warn("nested length overruns buffer")
        KompactDecodeError.TruncatedNested  -> logger.warn("nested region truncated")
        else -> logger.error("unexpected: $err")
    }
}
```

A throwing variant exists too:
`KompactFraming.readNestedOrThrow` returns `NestedRegion` (a
`Pair<Int, Int>`) directly.

## 6. Common patterns

### Pattern A — silently substitute a default, keep the original

```kotlin
val battery = KompactRuntime.readScalar(raw, 0, ScalarType.of(4, signed = false))
    .getOrElse { 0 }
```

### Pattern B — collect, don't fail, across many fields

When decoding a partial buffer where some fields may be valid and
others not, don't fail on the first error — collect successes and
report failures at the end:

```kotlin
sealed class FieldResult<out T> {
    data class Ok<T>(val value: T) : FieldResult<T>()
    data object Err : FieldResult<Nothing>()
}

fun readField(raw: ByteArray, off: Int, t: ScalarType): FieldResult<Int> {
    val r = KompactRuntime.readScalar(raw, off, t)
    return if (r.isSuccess) FieldResult.Ok(r.getOrThrow()) else FieldResult.Err
}
```

### Pattern C — propagate the error up with a custom exception

```kotlin
class DecodeFailure(val error: KompactDecodeError, val field: String) : RuntimeException(
    "decode failed on $field: $error"
)

fun readOrFail(raw: ByteArray, off: Int, t: ScalarType, name: String): Int =
    KompactRuntime.readScalar(raw, off, t).getOrElse { err ->
        throw DecodeFailure(err, name)
    }
```

### Pattern D — fail fast on the first field (BLE re-sync)

When a BLE stream goes out of sync, the simplest recovery is to
discard the buffer and wait for the next one. Skip the per-field
recovery and throw:

```kotlin
val speed = KompactRuntime.readScalarOrThrow(raw, 0, ScalarType.of(10, signed = false))
// throws → caller drops the buffer, resyncs on the next characteristic notification
```

## 7. Enum codes

Kompact does not have a built-in `enum` type for the wire. Enums are
modelled as a fixed-width integer field plus a hand-written check
against the declared set, producing `UnknownEnumCode(rawCode)`:

```kotlin
val statusResult = KompactRuntime.readScalar(raw, 0, ScalarType.of(4, signed = false))
val code: Int = statusResult.getOrElse { return@decodeFrame DecodeResult.Malformed }
when (code) {
    0 -> BatteryStatus.OK
    1 -> BatteryStatus.LOW
    2 -> BatteryStatus.CRITICAL
    3 -> BatteryStatus.CHARGING
    else -> return DecodeResult.UnknownEnum(BatteryStatus, code)
}
```

`UnknownEnumCode` is a data-class variant of `KompactDecodeError` —
it carries the raw wire value so the caller can decide whether to
treat it as a schema version mismatch (unknown → drop) or as data
corruption (unknown → log and drop).

## Common pitfalls

- **Catching `KompactDecodeException` on the hot path.** The whole
  point of the typed-result API is that the success path never
  throws. If you find yourself writing a try/catch around every read,
  switch to `isSuccess` / `getOrElse` instead — the cost is one
  branch per read, no allocation.
- **Boxing through a generic `Result<Int>`.** The seven result types
  are specialized to keep the success path unboxed. Don't write
  `Result<Int>` yourself; use `IntResult` (or `Kompact.Result.Int`).
- **Forgetting `error` is nullable.** `result.error` is
  `KompactDecodeError?` — `null` on success, non-null on failure.
  Branch on `isSuccess` first to avoid the null.
- **`LongResult` sentinel band.** Values in
  `Long.MIN_VALUE .. Long.MIN_VALUE + (1L shl 58) - 1` are not
  representable as success — see
  [architecture — runtime error encoding](../architecture.md#runtime-error-encoding)
  for the details.

## What's next

- The wire format and error encoding rationale:
  [architecture — runtime error encoding](../architecture.md#runtime-error-encoding).
- The full API surface for results and errors:
  [`api-reference.md`](../api-reference.md#typed-result-value-classes).
- Send / receive over BLE: [`integrate-ble.md`](integrate-ble.md).
