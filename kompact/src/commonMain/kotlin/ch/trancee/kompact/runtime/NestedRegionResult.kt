package ch.trancee.kompact.runtime

import kotlin.jvm.JvmInline

public typealias NestedRegion = Pair<Int, Int>

@JvmInline
public value class NestedRegionResult(
    public val packed: Long,
) {
    public val isSuccess: Boolean get() = !isPackedFailure(packed)
    public val isFailure: Boolean get() = isPackedFailure(packed)
    public val error: KompactDecodeError? get() =
        if (isSuccess) null else decodePackedError(packed)
    public val startBit: Int get() = (packed ushr 32).toInt()
    public val bitLength: Int get() = packed.toInt()

    public fun getOrThrow(): NestedRegion =
        if (isSuccess) startBit to bitLength else throwDecodeErrorFromPacked(packed)

    public companion object {
        public fun success(
            startBit: Int,
            bitLength: Int,
        ): NestedRegionResult = NestedRegionResult((startBit.toLong() shl 32) or (bitLength.toLong() and 0xFFFF_FFFFL))

        public fun failure(error: KompactDecodeError): NestedRegionResult =
            NestedRegionResult(encodePackedFailure(error))
    }
}
