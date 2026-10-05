package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class KompactCursorByteRangeBoundaryTest {
    @Test
    fun emptyPayloadFitsExactlyAfterItsLengthPrefix() {
        val frame = byteArrayOf(0)
        val nested = KompactCursor(ByteArray(0))
        val nestedParent = KompactCursor(frame)

        assertEquals(KompactCursor.STATUS_OK, nestedParent.readNested(8, nested))
        assertEquals(8, nestedParent.position)
        assertEquals(8, nested.startBit)
        assertEquals(8, nested.endBit)

        val range = KompactByteRange(ByteArray(0))
        val rangeReader = KompactCursor(frame)
        assertEquals(KompactCursor.STATUS_OK, rangeReader.readByteRange(8, range))
        assertEquals(0, range.size)
        assertEquals(8, rangeReader.position)

        val utf8Reader = KompactCursor(frame)
        assertEquals(KompactCursor.STATUS_OK, utf8Reader.readUtf8Range(8, range))
        assertEquals(0, range.size)

        val skipper = KompactCursor(frame)
        assertEquals(KompactCursor.STATUS_OK, skipper.skipByteRange(8, validateUtf8 = true))
        assertEquals(8, skipper.position)

        val nestedOutput = ByteArray(1)
        val nestedWriter = KompactCursor(nestedOutput)
        val child = KompactCursor(ByteArray(0))
        assertEquals(KompactCursor.STATUS_OK, nestedWriter.writeNested(8, 0, child))
        assertContentEquals(frame, nestedOutput)
        assertEquals(8, child.startBit)
        assertEquals(8, child.endBit)

        val rangeOutput = ByteArray(1)
        val rangeWriter = KompactCursor(rangeOutput)
        assertEquals(KompactCursor.STATUS_OK, rangeWriter.writeByteRange(8, range))
        assertContentEquals(frame, rangeOutput)

        val preflight = KompactCursor(ByteArray(1))
        assertEquals(KompactCursor.STATUS_OK, preflight.preflightByteRangeWrite(8, range))
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

    fun maximumEightBitPayloadFitsExactlyAtTheBufferBoundary() {
        val payload = ByteArray(255) { (it xor 0x5A).toByte() }
        val borrowed = KompactByteRange(payload)
        val output = ByteArray(256)
        val writer = KompactCursor(output)

        assertEquals(KompactCursor.STATUS_OK, writer.writeByteRange(8, borrowed))
        assertEquals(255, output[0].toInt() and 0xFF)
        assertEquals(2048, writer.position)
        assertContentEquals(payload, output.copyOfRange(1, output.size))

        val reader = KompactCursor(output)
        val decoded = KompactByteRange(ByteArray(0))
        assertEquals(KompactCursor.STATUS_OK, reader.readByteRange(8, decoded))
        assertEquals(payload.size, decoded.size)
        val copy = ByteArray(payload.size)
        assertEquals(true, decoded.copyTo(copy))
        assertContentEquals(payload, copy)

        val preflight = KompactCursor(ByteArray(256))
        assertEquals(KompactCursor.STATUS_OK, preflight.preflightByteRangeWrite(8, borrowed))
        assertEquals(2048, preflight.position)
    }
}
