@file:OptIn(ch.trancee.kompact.annotations.KompactPreview::class)

package ch.trancee.kompact.compiletest

import ch.trancee.kompact.runtime.KompactCursor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FixedLayoutGeneratedCodeTest {
    @Test
    fun generatedViewCreatesReadsAndCopiesWithDefaults() {
        val frame = SensorFrameView.create(status = 3, temperature = -5, alarm = true)

        assertEquals(3, frame.status)
        assertEquals(-5, frame.temperature)
        assertTrue(frame.alarm)
        val copied = frame.copy(alarm = false)
        assertEquals(3, copied.status)
        assertEquals(-5, copied.temperature)
        assertFalse(copied.alarm)
    }

    @Test
    fun generatedViewRejectsUndersizedBuffers() {
        assertFailsWith<IllegalArgumentException> { SensorFrameView(ByteArray(2)) }
    }

    @Test
    fun mutableSiblingWritesThroughToTheSharedBuffer() {
        val raw = SensorFrameView.create(status = 1, temperature = 0, alarm = false).raw
        val mutable = MutableSensorFrameView(raw)

        mutable.temperature = -2048
        mutable.alarm = true

        val view = SensorFrameView(raw)
        assertEquals(1, view.status)
        assertEquals(-2048, view.temperature)
        assertTrue(view.alarm)
    }

    @Test
    fun generatedHolderRoundTripsThroughCallerOwnedStorage() {
        val raw = ByteArray(3)
        val source = SensorFrameViewHolder(status = 9, temperature = 100, alarm = true)

        assertEquals(KompactCursor.STATUS_OK, source.encodeFrom(KompactCursor(raw)))
        val decoded = SensorFrameViewHolder()
        assertEquals(KompactCursor.STATUS_OK, decoded.decodeInto(KompactCursor(raw)))

        assertEquals(9, decoded.status)
        assertEquals(100, decoded.temperature)
        assertTrue(decoded.alarm)
        assertEquals(9, SensorFrameView(raw).status)
    }
}
