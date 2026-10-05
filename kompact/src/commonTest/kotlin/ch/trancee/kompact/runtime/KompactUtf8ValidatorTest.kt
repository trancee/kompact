package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class KompactUtf8ValidatorTest {
    @Test
    fun captureRegionAcceptsValidUtf8SequencesAndRejectsMalformedSequences() {
        val cases =
            listOf(
                byteArrayOf() to true,
                byteArrayOf(0x00, 0x7F) to true,
                byteArrayOf(0xC2.toByte(), 0x80.toByte()) to true,
                byteArrayOf(0xDF.toByte(), 0xBF.toByte()) to true,
                byteArrayOf(0xE0.toByte(), 0xA0.toByte(), 0x80.toByte()) to true,
                byteArrayOf(0xE1.toByte(), 0x80.toByte(), 0x80.toByte()) to true,
                byteArrayOf(0xEC.toByte(), 0xBF.toByte(), 0xBF.toByte()) to true,
                byteArrayOf(0xED.toByte(), 0x9F.toByte(), 0xBF.toByte()) to true,
                byteArrayOf(0xEE.toByte(), 0x80.toByte(), 0x80.toByte()) to true,
                byteArrayOf(0xEF.toByte(), 0xBF.toByte(), 0xBF.toByte()) to true,
                byteArrayOf(0xF0.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()) to true,
                byteArrayOf(0xF1.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte()) to true,
                byteArrayOf(0xF3.toByte(), 0xBF.toByte(), 0xBF.toByte(), 0xBF.toByte()) to true,
                byteArrayOf(0xF4.toByte(), 0x8F.toByte(), 0xBF.toByte(), 0xBF.toByte()) to true,
                byteArrayOf(0xC0.toByte(), 0x80.toByte()) to false,
                byteArrayOf(0xC1.toByte(), 0xBF.toByte()) to false,
                byteArrayOf(0x80.toByte()) to false,
                byteArrayOf(0xC2.toByte()) to false,
                byteArrayOf(0xC2.toByte(), 0x41) to false,
                byteArrayOf(0xE0.toByte()) to false,
                byteArrayOf(0xE0.toByte(), 0xA0.toByte()) to false,
                byteArrayOf(0xE0.toByte(), 0x9F.toByte(), 0x80.toByte()) to false,
                byteArrayOf(0xE0.toByte(), 0xC0.toByte(), 0x80.toByte()) to false,
                byteArrayOf(0xE0.toByte(), 0xA0.toByte(), 0x41) to false,
                byteArrayOf(0xE1.toByte(), 0x80.toByte()) to false,
                byteArrayOf(0xE1.toByte(), 0x41, 0x80.toByte()) to false,
                byteArrayOf(0xE1.toByte(), 0x80.toByte(), 0x41) to false,
                byteArrayOf(0xED.toByte()) to false,
                byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte()) to false,
                byteArrayOf(0xED.toByte(), 0x7F, 0x80.toByte()) to false,
                byteArrayOf(0xED.toByte(), 0x80.toByte(), 0x41) to false,
                byteArrayOf(0xF0.toByte()) to false,
                byteArrayOf(0xF0.toByte(), 0x90.toByte(), 0x80.toByte()) to false,
                byteArrayOf(0xF0.toByte(), 0x8F.toByte(), 0x80.toByte(), 0x80.toByte()) to false,
                byteArrayOf(0xF0.toByte(), 0xC0.toByte(), 0x80.toByte(), 0x80.toByte()) to false,
                byteArrayOf(0xF0.toByte(), 0x90.toByte(), 0x41, 0x80.toByte()) to false,
                byteArrayOf(0xF0.toByte(), 0x90.toByte(), 0x80.toByte(), 0x41) to false,
                byteArrayOf(0xF1.toByte()) to false,
                byteArrayOf(0xF1.toByte(), 0x80.toByte(), 0x80.toByte()) to false,
                byteArrayOf(0xF1.toByte(), 0x41, 0x80.toByte(), 0x80.toByte()) to false,
                byteArrayOf(0xF1.toByte(), 0x80.toByte(), 0x41, 0x80.toByte()) to false,
                byteArrayOf(0xF1.toByte(), 0x80.toByte(), 0x80.toByte(), 0x41) to false,
                byteArrayOf(0xF4.toByte()) to false,
                byteArrayOf(0xF4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()) to false,
                byteArrayOf(0xF4.toByte(), 0x7F, 0x80.toByte(), 0x80.toByte()) to false,
                byteArrayOf(0xF4.toByte(), 0x80.toByte(), 0x41, 0x80.toByte()) to false,
                byteArrayOf(0xF4.toByte(), 0x80.toByte(), 0x80.toByte(), 0x41) to false,
                byteArrayOf(0xF5.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte()) to false,
            )

        cases.forEach { (bytes, valid) ->
            val cursor = KompactCursor(bytes)
            val range = KompactByteRange(ByteArray(0))

            val status = cursor.captureRegion(range, validateUtf8 = true)

            assertEquals(if (valid) KompactCursor.STATUS_OK else KompactCursor.STATUS_INVALID_UTF8, status)
        }
    }
}
