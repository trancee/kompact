package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class KompactCursorByteRangeBoundaryTest {
    @Test
    fun nestedWriteReportsBoundsWhenAnAlignedPrefixDoesNotFitWithoutMutation() {
        for (prefixWidth in listOf(8, 16, 32)) {
            val output = ByteArray(6) { 0x55 }
            val before = output.copyOf()
            val writer = KompactCursor(output)
            writer.reset(output, startBit = 8, endBit = 8 + prefixWidth - 1)
            val childBuffer = byteArrayOf(0x33, 0x44)
            val child = KompactCursor(childBuffer)
            child.reset(childBuffer, startBit = 1, position = 2, endBit = 7)

            val status = writer.writeNested(prefixWidth, 0, child)

            assertEquals(KompactStatus.BOUNDS_ERROR, status, "prefixWidth=$prefixWidth")
            assertEquals(8, writer.errorBitOffset)
            assertEquals(0, writer.errorDetail)
            assertEquals(8, writer.position)
            assertContentEquals(before, output)
            assertSame(childBuffer, child.buffer)
            assertEquals(1, child.startBit)
            assertEquals(2, child.position)
            assertEquals(7, child.endBit)
        }
    }

    @Test
    fun emptyPayloadFitsExactlyAfterItsLengthPrefix() {
        val frame = byteArrayOf(0)
        val nested = KompactCursor(ByteArray(0))
        val nestedParent = KompactCursor(frame)

        assertEquals(KompactStatus.OK, nestedParent.readNested(8, nested))
        assertEquals(8, nestedParent.position)
        assertEquals(8, nested.startBit)
        assertEquals(8, nested.endBit)

        val range = KompactByteRange(ByteArray(0))
        val rangeReader = KompactCursor(frame)
        assertEquals(KompactStatus.OK, rangeReader.readByteRange(8, range))
        assertEquals(0, range.size)
        assertEquals(8, rangeReader.position)

        val utf8Reader = KompactCursor(frame)
        assertEquals(KompactStatus.OK, utf8Reader.readUtf8Range(8, range))
        assertEquals(0, range.size)

        val skipper = KompactCursor(frame)
        assertEquals(KompactStatus.OK, skipper.skipByteRange(8, validateUtf8 = true))
        assertEquals(8, skipper.position)

        val nestedOutput = ByteArray(1)
        val nestedWriter = KompactCursor(nestedOutput)
        val child = KompactCursor(ByteArray(0))
        assertEquals(KompactStatus.OK, nestedWriter.writeNested(8, 0, child))
        assertContentEquals(frame, nestedOutput)
        assertEquals(8, child.startBit)
        assertEquals(8, child.endBit)

        val rangeOutput = ByteArray(1)
        val rangeWriter = KompactCursor(rangeOutput)
        assertEquals(KompactStatus.OK, rangeWriter.writeByteRange(8, range))
        assertContentEquals(frame, rangeOutput)

        val preflight = KompactCursor(ByteArray(1))
        assertEquals(KompactStatus.OK, preflight.preflightByteRangeWrite(8, range))
        assertEquals(8, preflight.position)
    }

    @Test
    fun copyToRejectsPositiveOffsetOverrunWithoutChangingDestination() {
        val range = KompactByteRange(byteArrayOf(0x11, 0x22))
        val destination = byteArrayOf(0x55, 0x66, 0x77, 0x7F)
        val before = destination.copyOf()

        val copied = range.copyTo(destination, destinationOffset = 3)

        assertEquals(false, copied)
        assertContentEquals(before, destination)
    }

    @Test
    fun maximumEightBitPayloadFitsExactlyAtTheBufferBoundary() {
        val payload = ByteArray(255) { (it xor 0x5A).toByte() }
        val borrowed = KompactByteRange(payload)
        val output = ByteArray(256)
        val writer = KompactCursor(output)

        assertEquals(KompactStatus.OK, writer.writeByteRange(8, borrowed))
        assertEquals(255, output[0].toInt() and 0xFF)
        assertEquals(2048, writer.position)
        assertContentEquals(payload, output.copyOfRange(1, output.size))

        val reader = KompactCursor(output)
        val decoded = KompactByteRange(ByteArray(0))
        assertEquals(KompactStatus.OK, reader.readByteRange(8, decoded))
        assertEquals(payload.size, decoded.size)
        val copy = ByteArray(payload.size)
        assertEquals(true, decoded.copyTo(copy))
        assertContentEquals(payload, copy)

        val preflight = KompactCursor(ByteArray(256))
        assertEquals(KompactStatus.OK, preflight.preflightByteRangeWrite(8, borrowed))
        assertEquals(2048, preflight.position)
    }
}
