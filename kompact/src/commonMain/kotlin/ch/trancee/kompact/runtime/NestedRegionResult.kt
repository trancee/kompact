package ch.trancee.kompact.runtime

public typealias NestedRegion = Pair<Int, Int>

public expect value class NestedRegionResult(
    public val packed: Long,
) {
    public val isSuccess: Boolean
    public val isFailure: Boolean
    public val error: KompactDecodeError?
    public val startBit: Int
    public val bitLength: Int

    public fun getOrThrow(): NestedRegion

    public companion object {
        public fun success(
            startBit: Int,
            bitLength: Int,
        ): NestedRegionResult

        public fun failure(error: KompactDecodeError): NestedRegionResult
    }
}
