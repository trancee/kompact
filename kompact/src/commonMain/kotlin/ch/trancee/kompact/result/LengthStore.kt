package ch.trancee.kompact.result

/**
 * Common `expect` of the thread-local value registry backing the
 * length-prefixed result value classes. Platform actuals (JVM: a
 * `ThreadLocal<MutableMap>`; iOS: a `FreezableAtomicReference`)
 * maintain the per-thread map.
 */
internal expect object LengthStore {
    fun internString(value: String): Long
    fun internByteArray(value: ByteArray): Long
    fun internRepeated(count: Int, elements: List<ByteArray>): Long

    fun stringHandle(packed: Long): String
    fun byteArrayHandle(packed: Long): ByteArray
    fun repeatedCount(packed: Long): Int
    fun repeatedElements(packed: Long): List<ByteArray>

    fun clear()
}
