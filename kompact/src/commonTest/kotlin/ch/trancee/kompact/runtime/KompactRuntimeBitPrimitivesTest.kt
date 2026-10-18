package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class KompactRuntimeBitPrimitivesTest {
    @Test
    fun readsAndWritesMatchIndependentBitOrderAcrossOffsetsAndWidths() {
        for (background in BACKGROUNDS) {
            for (offset in 0 until 24) {
                for (width in 0..64) {
                    val value = 0x53C5_A719_82E6_4B0FL xor (offset.toLong() shl 37) xor width.toLong()
                    // Exact-size buffer: touching one byte past the field throws.
                    val initial = ByteArray((offset + width + 7) / 8) { background }
                    val expected = referenceWrite(initial, offset, width, value)
                    val expectedValue = referenceRead(expected, offset, width)

                    val actual = initial.copyOf()
                    KompactRuntime.writeBitsLong(actual, offset, width, value)

                    val case = "width=$width offset=$offset background=$background"
                    assertContentEquals(expected, actual, "write $case")
                    assertEquals(expectedValue, KompactRuntime.readBitsLong(actual, offset, width), "read $case")
                    if (width <= 32) {
                        val intActual = initial.copyOf()
                        KompactRuntime.writeBits(intActual, offset, width, value.toInt())
                        assertContentEquals(expected, intActual, "int write $case")
                        assertEquals(expectedValue.toInt(), KompactRuntime.readBits(actual, offset, width), "int read $case")
                    }
                }
            }
        }
    }

    private fun referenceWrite(
        initial: ByteArray,
        offset: Int,
        width: Int,
        value: Long,
    ): ByteArray {
        val expected = initial.copyOf()
        for (bit in 0 until width) {
            val absoluteBit = offset + bit
            val byteIndex = absoluteBit / 8
            val mask = 1 shl (absoluteBit % 8)
            val oldByte = expected[byteIndex].toInt() and 0xFF
            expected[byteIndex] = (if ((value ushr bit) and 1L == 1L) oldByte or mask else oldByte and mask.inv()).toByte()
        }
        return expected
    }

    private fun referenceRead(
        bytes: ByteArray,
        offset: Int,
        width: Int,
    ): Long {
        var result = 0L
        for (bit in 0 until width) {
            val absoluteBit = offset + bit
            val bitValue = (bytes[absoluteBit / 8].toInt() ushr (absoluteBit % 8)) and 1
            result = result or (bitValue.toLong() shl bit)
        }
        return result
    }

    private companion object {
        val BACKGROUNDS = byteArrayOf(0x00, 0xA5.toByte(), 0xFF.toByte())
    }
}
