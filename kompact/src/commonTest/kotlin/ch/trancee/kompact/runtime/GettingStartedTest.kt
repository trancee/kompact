package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The executable companion to `docs/getting-started.md`.
 *
 * If this file is green, the tutorial is honest. The tutorial quotes the wire
 * bytes and round-trip values asserted below; if a future change to the runtime
 * shifts the wire layout, this test fails first and the doc is updated with it.
 *
 * Layout (LSB-first, 16 bits = 2 bytes):
 *   [0..3]   batteryStatus (4 bits, 0..15)   unsigned
 *   [4..13]  speed         (10 bits, 0..1023) unsigned
 *   [14]     isMalfunctioning (1 bit)
 *   [15]     reserved      (1 bit, left zero)
 */
class GettingStartedTest {

    @Test
    fun readYourFirstMessage() {
        // --- Write: build a 2-byte telemetry frame with KompactWriter ---
        val w = KompactWriter()
        w.writeScalar(bitWidth = 4, value = 5L)     // battery = 5
        w.writeScalar(bitWidth = 10, value = 10L)   // speed = 10
        w.writeBool(true)                           // isMalfunctioning = true
        // bit 15 (reserved) is left zero.
        val bytes: ByteArray = w.build()
        assertEquals(2, bytes.size)

        // The LSB-first wire bytes for battery=5, speed=10, malfunction=true.
        // (Computed from the layout above; pinned here so a silent wire-format
        // regression breaks the tutorial test, not just the round-trip values.)
        assertEquals(0xA5, bytes[0].toInt() and 0xFF)
        assertEquals(0x40, bytes[1].toInt() and 0xFF)

        // --- Read: the checked, typed accessors on KompactRuntime ---
        val battery: Int = KompactRuntime.readScalar(bytes, 0, 4, signed = false).getOrThrow()
        val speed: Int = KompactRuntime.readScalar(bytes, 4, 10, signed = false).getOrThrow()
        val malfunction: Boolean = KompactRuntime.readBool(bytes, 14).getOrThrow()

        assertEquals(5, battery)
        assertEquals(10, speed)
        assertEquals(true, malfunction)
    }

    @Test
    fun readScalar_reportsBoundsErrorInsteadOfThrowing() {
        // Truncated buffer for the 16-bit layout: 1 byte (8 bits), but reading
        // `speed` at offset 4 with width 10 needs bits 4..13 (14 bits) — overruns.
        val truncated = byteArrayOf(0xA5.toByte())

        val speed = KompactRuntime.readScalar(truncated, 4, 10, signed = false)

        assertTrue(speed.isFailure)
        assertFalse(speed.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, speed.error)
        // The success-path hot code never throws (Ticket 03 / 08).
        // Only the explicit recovery call getOrThrow() can raise, and only on failure.
        assertFailsWith<KompactDecodeException> { speed.getOrThrow() }
    }
}
