package ch.trancee.kompact.runtime

import kotlin.jvm.JvmInline

@JvmInline
public value class ScalarType(
    public val packed: Int,
) {
    public val bitWidth: Int get() = packed ushr 1
    public val signed: Boolean get() = (packed and 1) != 0

    public companion object {
        public fun of(
            bitWidth: Int,
            signed: Boolean,
        ): ScalarType = ScalarType((bitWidth shl 1) or (if (signed) 1 else 0))

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
