@file:OptIn(ch.trancee.kompact.annotations.KompactPreview::class)

package ch.trancee.kompact.compiletest

import ch.trancee.kompact.runtime.KompactCursor
import ch.trancee.kompact.runtime.KompactStatus
import ch.trancee.kompact.runtime.KompactDecodeError
import ch.trancee.kompact.runtime.KompactFrameResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
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
    fun checkedDecodeReturnsBoundsFailureForUndersizedInput() {
        val result = SensorFrameView.decode(ByteArray(2))

        val failure = assertIs<KompactFrameResult.Failure>(result)
        assertEquals(KompactDecodeError.BoundsError, failure.error)
        assertIs<KompactFrameResult.Failure>(MutableSensorFrameView.decode(ByteArray(2)))
    }

    @Test
    fun checkedDecodeWrapsTheCallerBufferWithoutCopying() {
        val raw = SensorFrameView.create(status = 7, temperature = -1, alarm = true).raw

        val view = assertIs<KompactFrameResult.Success<SensorFrameView>>(SensorFrameView.decode(raw)).value
        assertSame(raw, view.raw)
        assertEquals(7, view.status)
        assertEquals(-1, view.temperature)
        val mutable = assertIs<KompactFrameResult.Success<MutableSensorFrameView>>(MutableSensorFrameView.decode(raw)).value
        mutable.status = 2
        assertEquals(2, view.status)
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

        assertEquals(KompactStatus.OK, source.encodeFrom(KompactCursor(raw)))
        val decoded = SensorFrameViewHolder()
        assertEquals(KompactStatus.OK, decoded.decodeInto(KompactCursor(raw)))

        assertEquals(9, decoded.status)
        assertEquals(100, decoded.temperature)
        assertTrue(decoded.alarm)
        assertEquals(9, SensorFrameView(raw).status)
    }
}
