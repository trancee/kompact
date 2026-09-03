package ch.trancee.kompact.result

/**
 * Internal helper result used by [ch.trancee.kompact.runtime.KompactRead]'s
 * length-prefix parser. Packed: bit 60 = ok, bits 61..63 = error code,
 * low 28 bits = length, bits 28..59 = afterPrefix bit offset (32 bits
 * used; length limited to 28 bits = 256MB which is plenty for BLE
 * payloads). Internal — no need to share the packing with the public
 * result classes.
 */
public expect value class LengthReadResult(public val packed: Long) {
    public val isOk: Boolean
    public val isError: Boolean
    public val errorCode: Int
    public val value: Pair<Int, Int>
    public fun toLong(): Long

    public companion object {
        public fun success(length: Int, afterPrefix: Int): LengthReadResult
        public fun failure(error: Int): LengthReadResult
    }
}
