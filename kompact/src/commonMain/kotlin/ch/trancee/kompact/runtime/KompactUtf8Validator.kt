package ch.trancee.kompact.runtime

internal object KompactUtf8Validator {
    fun isValid(
        bytes: ByteArray,
        start: Int,
        end: Int,
    ): Boolean {
        var index = start
        while (index < end) {
            val first = bytes[index].toInt() and 0xFF
            when {
                first <= 0x7F -> index++
                first in 0xC2..0xDF -> {
                    if (index + 1 >= end || !isContinuation(bytes[index + 1])) return false
                    index += 2
                }
                first == 0xE0 -> {
                    if (index + 2 >= end) return false
                    val second = bytes[index + 1].toInt() and 0xFF
                    if (second !in 0xA0..0xBF || !isContinuation(bytes[index + 2])) return false
                    index += 3
                }
                first in 0xE1..0xEC || first in 0xEE..0xEF -> {
                    if (index + 2 >= end ||
                        !isContinuation(bytes[index + 1]) ||
                        !isContinuation(bytes[index + 2])
                    ) {
                        return false
                    }
                    index += 3
                }
                first == 0xED -> {
                    if (index + 2 >= end) return false
                    val second = bytes[index + 1].toInt() and 0xFF
                    if (second !in 0x80..0x9F || !isContinuation(bytes[index + 2])) return false
                    index += 3
                }
                first == 0xF0 -> {
                    if (index + 3 >= end) return false
                    val second = bytes[index + 1].toInt() and 0xFF
                    if (second !in 0x90..0xBF ||
                        !isContinuation(bytes[index + 2]) ||
                        !isContinuation(bytes[index + 3])
                    ) {
                        return false
                    }
                    index += 4
                }
                first in 0xF1..0xF3 -> {
                    if (index + 3 >= end ||
                        !isContinuation(bytes[index + 1]) ||
                        !isContinuation(bytes[index + 2]) ||
                        !isContinuation(bytes[index + 3])
                    ) {
                        return false
                    }
                    index += 4
                }
                first == 0xF4 -> {
                    if (index + 3 >= end) return false
                    val second = bytes[index + 1].toInt() and 0xFF
                    if (second !in 0x80..0x8F ||
                        !isContinuation(bytes[index + 2]) ||
                        !isContinuation(bytes[index + 3])
                    ) {
                        return false
                    }
                    index += 4
                }
                else -> return false
            }
        }
        return true
    }

    private fun isContinuation(value: Byte): Boolean = value.toInt() and 0xC0 == 0x80
}
