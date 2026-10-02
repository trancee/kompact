package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KompactFrameWriterTest {
    @Test
    fun writeNestedAppendsOnlyBoundedViewWithoutChangingSource() {
        val source = byteArrayOf(99, 4, 5, 88)
        val writer = KompactWriter()

        writer.writeNested(8, source, 1, 3)
        val bytes = writer.build()

        assertContentEquals(byteArrayOf(2, 4, 5), bytes)
        assertContentEquals(byteArrayOf(99, 4, 5, 88), source)
    }

    @Test
    fun framingPrefixesRejectSixteenBitOverflowAndAllowMaxInt() {
        val raw = ByteArray(4)

        val overflow = assertFailsWith<IllegalArgumentException> {
            KompactFraming.writeLengthPrefix(raw, 0, 16, 65_536)
        }
        KompactFraming.writeLengthPrefix(raw, 0, 32, Int.MAX_VALUE)

        assertTrue(overflow.message.orEmpty().contains("does not fit"))
        assertContentEquals(byteArrayOf(-1, -1, -1, 127), raw)
    }

    @Test
    fun writerRejectsSixteenBitCountOverflowBeforeMutation() {
        val writer = KompactWriter()

        assertFailsWith<IllegalArgumentException> {
            writer.writeRepeated(65_536, 16) { writeBool(true) }
        }
        assertFailsWith<IllegalArgumentException> {
            writer.writeBlob(7, byteArrayOf(1))
        }

        assertContentEquals(byteArrayOf(), writer.build())
    }

    @Test
    fun sliceWriterSupportsBorrowedAlignedAndUnalignedAppends() {
        val source = byteArrayOf(2, 3, 4)
        val frame = KompactFrame.decode(source).getOrThrow()
        val slice = frame.readBlob(8)
        val aligned = KompactWriter()
        val unaligned = KompactWriter()

        aligned.writeSlice(slice)
        aligned.writeNested(8, slice)
        unaligned.writeBool(true)
        unaligned.writeSlice(slice)

        assertContentEquals(byteArrayOf(3, 4, 2, 3, 4), aligned.build())
        assertContentEquals(byteArrayOf(7, 8, 0), unaligned.build())
        assertContentEquals(byteArrayOf(2, 3, 4), source)
    }

    @Test
    fun invalidNestedViewBoundsDoNotAlterWriter() {
        val writer = KompactWriter()
        writer.writeBits(8, 9)

        assertFailsWith<IllegalArgumentException> { writer.writeNested(8, byteArrayOf(1), -1, 1) }
        assertFailsWith<IllegalArgumentException> { writer.writeNested(8, byteArrayOf(1), 1, 0) }
        assertFailsWith<IllegalArgumentException> { writer.writeNested(8, byteArrayOf(1), 0, 2) }

        assertContentEquals(byteArrayOf(9), writer.build())
    }

    @Test
    fun invalidSliceBoundsAreRejectedBeforeAppending() {
        val writer = KompactWriter()
        val source = byteArrayOf(1)

        assertFailsWith<IllegalArgumentException> { writer.writeSlice(KompactByteSlice(source, -1, 1)) }
        assertFailsWith<IllegalArgumentException> { writer.writeSlice(KompactByteSlice(source, 1, 0)) }
        assertFailsWith<IllegalArgumentException> { writer.writeSlice(KompactByteSlice(source, 0, 2)) }

        assertContentEquals(byteArrayOf(), writer.build())
    }

    @Test
    fun writerRejectsOversizedBlobBeforeMutation() {
        val oversized = ByteArray(Int.MAX_VALUE / 8 + 1)
        val writer = KompactWriter()

        val failure = assertFailsWith<IllegalArgumentException> { writer.writeBlob(32, oversized) }

        assertTrue(failure.message.orEmpty().contains("overflow"))
        assertContentEquals(byteArrayOf(), writer.build())
    }
}
