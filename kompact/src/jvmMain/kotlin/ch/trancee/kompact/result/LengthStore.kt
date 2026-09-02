package ch.trancee.kompact.result

/**
 * JVM `actual` of the thread-local value registry backing the
 * length-prefixed result value classes.
 */
internal actual object LengthStore {

    private val threadLocal = ThreadLocal.withInitial<MutableMap<Long, Any>> {
        HashMap()
    }

    private fun store(): MutableMap<Long, Any> = threadLocal.get()!!

    private fun newHandle(payload: Any): Long {
        val map = store()
        val id = ((map.size + 1).toLong() shl 8)
        map[id] = payload
        return OK_FLAG or id
    }

    actual fun internString(value: String): Long = newHandle(value)
    actual fun internByteArray(value: ByteArray): Long = newHandle(value)
    actual fun internRepeated(count: Int, elements: List<ByteArray>): Long =
        newHandle(RepeatedEntry(count, elements))

    actual fun stringHandle(packed: Long): String = store()[packed and CLEAR_FLAGS] as String
    actual fun byteArrayHandle(packed: Long): ByteArray = store()[packed and CLEAR_FLAGS] as ByteArray
    actual fun repeatedCount(packed: Long): Int = (store()[packed and CLEAR_FLAGS] as RepeatedEntry).count
    actual fun repeatedElements(packed: Long): List<ByteArray> =
        (store()[packed and CLEAR_FLAGS] as RepeatedEntry).elements

    actual fun clear() {
        store().clear()
    }

    private const val OK_FLAG: Long = 1L shl 56
    private const val CLEAR_FLAGS: Long = 0x00FFFFFFFFFFFFFFL

    private data class RepeatedEntry(val count: Int, val elements: List<ByteArray>)
}
