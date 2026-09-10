package ch.trancee.kompact.runtime

public actual value class NestedRegionResult(
    public actual val packed: Long,
) {
    public actual val isSuccess: Boolean get() = !isLongFailure(packed)
    public actual val isFailure: Boolean get() = isLongFailure(packed)
    public actual val error: KompactDecodeError? get() =
        if (isSuccess) null else decodeLongError(packed)
    public actual val startBit: Int get() = (packed ushr 32).toInt()
    public actual val bitLength: Int get() = packed.toInt()

    public actual fun getOrThrow(): NestedRegion =
        if (isSuccess) startBit to bitLength else throwDecodeErrorFromLong(packed)

    public actual companion object {
        public actual fun success(
            startBit: Int,
            bitLength: Int,
        ): NestedRegionResult = NestedRegionResult((startBit.toLong() shl 32) or (bitLength.toLong() and 0xFFFF_FFFFL))

        public actual fun failure(error: KompactDecodeError): NestedRegionResult =
            NestedRegionResult(encodeLongFailure(error))
    }
}
