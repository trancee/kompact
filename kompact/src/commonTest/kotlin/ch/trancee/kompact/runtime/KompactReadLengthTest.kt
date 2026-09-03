package ch.trancee.kompact.runtime

import ch.trancee.kompact.result.KompactError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KompactReadLengthTest {

    @Test
    fun readString_after_write() {
        val buf = byteArrayOf(2, 'H'.code.toByte(), 'i'.code.toByte())
        val r = KompactRead.readString(buf, bitOffset = 0, lengthPrefixBits = 8)
        println("readString packed=${r.toLong().toString(16)} isOk=${r.isOk} error=${r.errorCode}")
        assertTrue(r.isOk)
        assertEquals("Hi", r.value)
    }

    @Test
    fun readString_with_16bit_prefix() {
        val buf = byteArrayOf(1, 0, 'X'.code.toByte())
        val r = KompactRead.readString(buf, bitOffset = 0, lengthPrefixBits = 16)
        assertTrue(r.isOk)
        assertEquals("X", r.value)
    }

    @Test
    fun readString_truncated_returns_BadLengthPrefix() {
        val buf = byteArrayOf(5, 'H'.code.toByte(), 'i'.code.toByte())
        val r = KompactRead.readString(buf, bitOffset = 0, lengthPrefixBits = 8)
        assertFalse(r.isOk)
        assertEquals(KompactError.BadLengthPrefix, r.errorCode)
    }

    @Test
    fun readBlob_returns_byte_array() {
        val buf = byteArrayOf(3, 0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte())
        val r = KompactRead.readBlob(buf, bitOffset = 0, lengthPrefixBits = 8)
        assertTrue(r.isOk)
        assertEquals(3, r.value.size)
        assertEquals(0xDE.toByte(), r.value[0])
        assertEquals(0xAD.toByte(), r.value[1])
        assertEquals(0xBE.toByte(), r.value[2])
    }

    @Test
    fun readNested_exposes_sub_region() {
        val buf = byteArrayOf(2, 0xAA.toByte(), 0xBB.toByte())
        val r = KompactRead.readNested(buf, bitOffset = 0, lengthPrefixBits = 8)
        assertTrue(r.isOk)
        assertEquals(2, r.value.size)
        assertEquals(0xAA.toByte(), r.value[0])
        assertEquals(0xBB.toByte(), r.value[1])
    }

    @Test
    fun readRepeated_returns_count_and_total_bit_width() {
        val buf = byteArrayOf(2, 0x11, 0x22)
        val r = KompactRead.readRepeated(buf, bitOffset = 0, countPrefixBits = 8, elementBitWidth = 8)
        assertTrue(r.isOk)
        assertEquals(2, r.count)
        assertEquals(0x11, r.elements[0][0].toInt() and 0xFF)
        assertEquals(0x22, r.elements[1][0].toInt() and 0xFF)
    }

    @Test
    fun readString_empty_string() {
        val buf = byteArrayOf(0)
        val r = KompactRead.readString(buf, bitOffset = 0, lengthPrefixBits = 8)
        assertTrue(r.isOk)
        assertEquals("", r.value)
    }
}
