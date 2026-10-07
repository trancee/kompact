package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplicationEnvelopeExampleTest {
    @Test
    fun encodedEnvelopeCanBeBorrowedWithoutCopyingItsPayload() {
        val packetBytes = byteArrayOf(0xA5.toByte(), 0x40.toByte())
        val envelope = ByteArray(packetBytes.size + 1)

        assertEquals(
            envelope.size,
            ApplicationEnvelopeExample.encodeEnvelope(packetBytes, envelope),
        )
        assertContentEquals(byteArrayOf(1, 0xA5.toByte(), 0x40.toByte()), envelope)

        val borrowedPayload = KompactByteRange(ByteArray(0))

        assertTrue(ApplicationEnvelopeExample.borrowEnvelopePayload(envelope, borrowedPayload))
        assertEquals(packetBytes.size, borrowedPayload.size)

        val copiedPayload = ByteArray(packetBytes.size)
        assertTrue(borrowedPayload.copyTo(copiedPayload))
        assertContentEquals(packetBytes, copiedPayload)
    }

    @Test
    fun unsupportedEnvelopeVersionDoesNotChangeTheBorrowedRange() {
        val borrowedPayload = KompactByteRange(byteArrayOf(0x11, 0x22))
        val before = ByteArray(borrowedPayload.size)
        assertTrue(borrowedPayload.copyTo(before))

        assertFalse(
            ApplicationEnvelopeExample.borrowEnvelopePayload(
                byteArrayOf(2, 0xA5.toByte()),
                borrowedPayload,
            ),
        )

        val after = ByteArray(borrowedPayload.size)
        assertTrue(borrowedPayload.copyTo(after))
        assertContentEquals(before, after)
    }

    @Test
    fun insufficientDestinationDoesNotModifyTheEnvelope() {
        val destination = byteArrayOf(0x55, 0x66)
        val before = destination.copyOf()

        assertEquals(
            -1,
            ApplicationEnvelopeExample.encodeEnvelope(
                byteArrayOf(0xA5.toByte(), 0x40.toByte()),
                destination,
            ),
        )
        assertContentEquals(before, destination)
    }
}

internal object ApplicationEnvelopeExample {
    private const val APPLICATION_VERSION = 1

    fun encodeEnvelope(payload: ByteArray, destination: ByteArray): Int {
        if (destination.size < payload.size + 1) return -1
        destination[0] = APPLICATION_VERSION.toByte()
        payload.copyInto(destination, destinationOffset = 1)
        return payload.size + 1
    }

    fun borrowEnvelopePayload(
        envelope: ByteArray,
        payload: KompactByteRange,
    ): Boolean {
        if (envelope.isEmpty() || (envelope[0].toInt() and 0xFF) != APPLICATION_VERSION) return false
        return payload.reset(envelope, start = 1, end = envelope.size)
    }
}
