package ch.trancee.kompact.runtime

import kotlin.jvm.JvmInline

/**
 * Checked result of [KompactFraming.readNested]: either the payload region
 * `[startBit, startBit + bitLength)` or a [KompactDecodeError].
 *
 * The region is packed into one `Long`, so success does not allocate. Read
 * [startBit] and [bitLength] only after checking [isSuccess]; both throw
 * [KompactDecodeException] on a failure result.
 */
@JvmInline
public value class NestedRegionResult private constructor(
    private val packed: Long,
) {
    public val isSuccess: Boolean get() = !isPackedFailure(packed)
    public val isFailure: Boolean get() = isPackedFailure(packed)
    public val error: KompactDecodeError? get() =
        if (isSuccess) null else decodePackedError(packed)

    /** First payload bit. Throws [KompactDecodeException] on failure. */
    public val startBit: Int get() = if (isSuccess) (packed ushr 32).toInt() else throwDecodeErrorFromPacked(packed)

    /** Payload length in bits. Throws [KompactDecodeException] on failure. */
    public val bitLength: Int get() = if (isSuccess) packed.toInt() else throwDecodeErrorFromPacked(packed)

    public companion object {
        public fun success(
            startBit: Int,
            bitLength: Int,
        ): NestedRegionResult {
            require(startBit >= 0 && bitLength >= 0) { "region must be non-negative: $startBit, $bitLength" }
            return NestedRegionResult((startBit.toLong() shl 32) or bitLength.toLong())
        }

        public fun failure(error: KompactDecodeError): NestedRegionResult =
            NestedRegionResult(encodePackedFailure(error))
    }
}
