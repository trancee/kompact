package ch.trancee.kompact.runtime

import kotlin.jvm.JvmInline

/**
 * Width and signedness of a scalar field, packed into one `Int`.
 *
 * Use [of] or the predefined constants. [bitWidth] is 1..64.
 */
@JvmInline
public value class ScalarType private constructor(
    private val packed: Int,
) {
    public val bitWidth: Int get() = packed ushr 1
    public val signed: Boolean get() = (packed and 1) != 0

    public companion object {
        public fun of(
            bitWidth: Int,
            signed: Boolean,
        ): ScalarType {
            require(bitWidth in 1..64) { "ScalarType bitWidth must be 1..64, was $bitWidth" }
            return ScalarType((bitWidth shl 1) or (if (signed) 1 else 0))
        }

        public val INT_8: ScalarType = of(8, signed = true)
        public val INT_16: ScalarType = of(16, signed = true)
        public val INT_32: ScalarType = of(32, signed = true)
        public val INT_64: ScalarType = of(64, signed = true)
        public val UINT_8: ScalarType = of(8, signed = false)
        public val UINT_16: ScalarType = of(16, signed = false)
        public val UINT_32: ScalarType = of(32, signed = false)
        public val UINT_64: ScalarType = of(64, signed = false)
        public val BOOL: ScalarType = of(1, signed = false)
    }
}
