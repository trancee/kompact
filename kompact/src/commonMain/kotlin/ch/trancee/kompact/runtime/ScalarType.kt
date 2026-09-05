package ch.trancee.kompact.runtime

public expect value class ScalarType(public val packed: Int) {
    public val bitWidth: Int
    public val signed: Boolean
    public companion object {
        public fun of(bitWidth: Int, signed: Boolean): ScalarType
        public val INT_8: ScalarType
        public val INT_16: ScalarType
        public val INT_32: ScalarType
        public val INT_64: ScalarType
        public val UINT_8: ScalarType
        public val UINT_16: ScalarType
        public val UINT_32: ScalarType
        public val UINT_64: ScalarType
        public val BOOL: ScalarType
    }
}
