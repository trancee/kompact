package ch.trancee.kompact.result

import kotlin.jvm.JvmInline

private const val OK_BIT: Long = 1L shl 60
private const val ERR_SHIFT: Int = 61
private const val ERR_MASK: Long = 0x7L shl ERR_SHIFT
private const val LENGTH_MASK: Long = 0x0FFFFFFFL  // 28 bits
private const val AFTER_SHIFT: Int = 28

@JvmInline
public actual value class LengthReadResult actual constructor(public actual val packed: Long) {
    public actual val isOk: Boolean get() = (packed and OK_BIT) != 0L
    public actual val isError: Boolean get() = !isOk
    public actual val errorCode: Int get() = ((packed and ERR_MASK) ushr ERR_SHIFT).toInt()
    public actual val value: Pair<Int, Int>
        get() {
            val length = (packed and LENGTH_MASK).toInt()
            val after = ((packed ushr AFTER_SHIFT) and LENGTH_MASK).toInt()
            return length to after
        }
    public actual fun toLong(): Long = packed

    public actual companion object {
        public actual fun success(length: Int, afterPrefix: Int): LengthReadResult {
            val packed = OK_BIT or
                (length.toLong() and LENGTH_MASK) or
                ((afterPrefix.toLong() and LENGTH_MASK) shl AFTER_SHIFT)
            return LengthReadResult(packed)
        }
        public actual fun failure(error: Int): LengthReadResult {
            val packed = (error.toLong() and 0x7L) shl ERR_SHIFT
            return LengthReadResult(packed)
        }
    }
}
