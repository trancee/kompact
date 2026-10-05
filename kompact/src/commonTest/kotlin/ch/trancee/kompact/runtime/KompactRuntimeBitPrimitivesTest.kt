package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class KompactRuntimeBitPrimitivesTest {
    @Test
    fun readsAndWritesMatchIndependentBitOrderAcrossOffsetsAndWidths() {
        for (offset in 0 until 8) {
            for (width in 1..64) {
                val initial = ByteArray(10) { 0xA5.toByte() }
                val expected = initial.copyOf()
                val value = 0x53C5_A719_82E6_4B0FL xor (offset.toLong() shl 37) xor width.toLong()
                var expectedValue = 0L

                for (bit in 0 until width) {
                    val valueBit = (value ushr bit) and 1L
                    val absoluteBit = offset + bit
                    val byteIndex = absoluteBit / 8
                    val bitInByte = absoluteBit % 8
                    val oldByte = expected[byteIndex].toInt() and 0xFF
                    expected[byteIndex] =
                        if (valueBit == 1L) {
                            (oldByte or (1 shl bitInByte)).toByte()
                        } else {
                            (oldByte and (1 shl bitInByte).inv()).toByte()
                        }
                    expectedValue = expectedValue or (valueBit shl bit)
                }

                val actual = initial.copyOf()
                KompactRuntime.writeBitsLong(actual, offset, width, value)

                assertContentEquals(expected, actual, "write width=$width offset=$offset")
                assertEquals(expectedValue, KompactRuntime.readBitsLong(actual, offset, width), "read width=$width offset=$offset")
                if (width <= 32) {
                    assertEquals(expectedValue.toInt(), KompactRuntime.readBits(actual, offset, width), "int read width=$width offset=$offset")
                }
            }
        }
    }
}
