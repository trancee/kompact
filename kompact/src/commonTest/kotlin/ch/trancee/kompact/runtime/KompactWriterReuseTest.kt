package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KompactWriterReuseTest {
    @Test
    fun resetDiscardsContentAndReusesTheWriter() {
        val writer = KompactWriter()
        writer.writeBitsLong(64, -1L)
        writer.writeBool(true)

        writer.reset()
        writer.writeBits(4, 0x5)

        assertContentEquals(byteArrayOf(0x05), writer.build())
    }

    @Test
    fun nestedBlockThrowingLeavesTheWriterUnchanged() {
        val writer = KompactWriter()
        writer.writeBool(true)

        assertFailsWith<IllegalStateException> {
            writer.writeNested(lengthPrefixWidth = 8) {
                writeBitsLong(32, 0xFFFF_FFFFL)
                error("boom")
            }
        }
        writer.writeBits(7, 0)

        assertContentEquals(byteArrayOf(0x01), writer.build())
    }

    @Test
    fun nestedAtUnalignedOffsetMatchesTheSliceEncoding() {
        val viaBlock = KompactWriter()
        viaBlock.writeBits(3, 0b101)
        viaBlock.writeNested(lengthPrefixWidth = 8) {
            writeBits(5, 0b10011)
            writeBitsLong(16, 0xBEEFL)
        }
        val child = KompactWriter().apply {
            writeBits(5, 0b10011)
            writeBitsLong(16, 0xBEEFL)
        }.build()
        val viaSlice = KompactWriter()
        viaSlice.writeBits(3, 0b101)
        viaSlice.writeNested(8, child, 0, child.size)

        assertContentEquals(viaSlice.build(), viaBlock.build())
    }

    @Test
    fun nestedPaddingOverwritesStaleBytesAfterReset() {
        val writer = KompactWriter()
        writer.writeBitsLong(64, -1L)
        writer.reset()

        writer.writeNested(lengthPrefixWidth = 8) { writeBits(1, 1) }

        assertContentEquals(byteArrayOf(1, 1), writer.build())
        assertEquals(16, writer.bitCursor)
    }
}
