package ch.trancee.kompact.runtime

/*
 * getOrElse / map extensions on the seven result value classes plus
 * NestedRegionResult (ergonomics-04: result-ergonomics extensions).
 *
 * These mirror stdlib Result<T>.getOrElse / Result<T>.map, specialized per type
 * so no boxing occurs on the success path (each result is a value class over a
 * single Long):
 *   - getOrElse returns the success value, or invokes [fallback] with the
 *     [KompactDecodeError] on failure. The fallback may return any type R.
 *   - map transforms the success value in place (same result type); on failure
 *     the result is re-returned unchanged. map is intentionally same-type (these
 *     are specialized value classes, not a generic Result<T>).
 *
 * `error` is non-null on the failure path (guarded by [isFailure]), so the
 * `error!!` assertions are safe. Both fns are inline so the value-class
 * accessors stay inlined at the call site (Ticket 03).
 */

public inline fun ByteResult.getOrElse(fallback: (KompactDecodeError) -> Byte): Byte =
    if (isSuccess) getOrThrow() else fallback(error!!)

public inline fun ByteResult.map(transform: (Byte) -> Byte): ByteResult =
    if (isSuccess) ByteResult.success(transform(getOrThrow())) else this

public inline fun ShortResult.getOrElse(fallback: (KompactDecodeError) -> Short): Short =
    if (isSuccess) getOrThrow() else fallback(error!!)

public inline fun ShortResult.map(transform: (Short) -> Short): ShortResult =
    if (isSuccess) ShortResult.success(transform(getOrThrow())) else this

public inline fun IntResult.getOrElse(fallback: (KompactDecodeError) -> Int): Int =
    if (isSuccess) getOrThrow() else fallback(error!!)

public inline fun IntResult.map(transform: (Int) -> Int): IntResult =
    if (isSuccess) IntResult.success(transform(getOrThrow())) else this

public inline fun LongResult.getOrElse(fallback: (KompactDecodeError) -> Long): Long =
    if (isSuccess) getOrThrow() else fallback(error!!)

public inline fun LongResult.map(transform: (Long) -> Long): LongResult =
    if (isSuccess) LongResult.success(transform(getOrThrow())) else this

public inline fun FloatResult.getOrElse(fallback: (KompactDecodeError) -> Float): Float =
    if (isSuccess) getOrThrow() else fallback(error!!)

public inline fun FloatResult.map(transform: (Float) -> Float): FloatResult =
    if (isSuccess) FloatResult.success(transform(getOrThrow())) else this

public inline fun DoubleResult.getOrElse(fallback: (KompactDecodeError) -> Double): Double =
    if (isSuccess) getOrThrow() else fallback(error!!)

public inline fun DoubleResult.map(transform: (Double) -> Double): DoubleResult =
    if (isSuccess) DoubleResult.success(transform(getOrThrow())) else this

public inline fun BooleanResult.getOrElse(fallback: (KompactDecodeError) -> Boolean): Boolean =
    if (isSuccess) getOrThrow() else fallback(error!!)

public inline fun BooleanResult.map(transform: (Boolean) -> Boolean): BooleanResult =
    if (isSuccess) BooleanResult.success(transform(getOrThrow())) else this

public inline fun NestedRegionResult.getOrElse(fallback: (KompactDecodeError) -> NestedRegion): NestedRegion =
    if (isSuccess) getOrThrow() else fallback(error!!)

public inline fun NestedRegionResult.map(transform: (NestedRegion) -> NestedRegion): NestedRegionResult =
    if (isSuccess) {
        val region = transform(getOrThrow())
        NestedRegionResult.success(region.first, region.second)
    } else {
        this
    }
