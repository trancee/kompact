@file:OptIn(ch.trancee.kompact.annotations.KompactPreview::class)

package example

import ch.trancee.kompact.runtime.KompactCursor
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GeneratedProbeCursorTest {
    @Test
    fun scalarFramedDecodeRejectsAliasedCursorsWithoutChangingHolder() {
        val holder = PacketSchemaViewHolder(marker = 9)
        val cursor = KompactCursor(byteArrayOf(0x2A))

        assertEquals(KompactCursor.STATUS_INVALID_ARGUMENT, holder.decodeInto(cursor, cursor))

        assertEquals(9, holder.marker)
        assertEquals(0, cursor.position)
    }

    @Test
    fun scalarFramedEncodeRejectsAliasedCursorsWithoutChangingOutput() {
        val raw = byteArrayOf(0x55)
        val holder = PacketSchemaViewHolder(marker = 42)
        val cursor = KompactCursor(raw)

        assertEquals(KompactCursor.STATUS_INVALID_ARGUMENT, holder.encodeFrom(cursor, cursor))

        assertContentEquals(byteArrayOf(0x55), raw)
        assertEquals(0, cursor.position)
    }

    @Test
    fun scalarFramedDecodePreflightsTruncatedInputWithoutChangingHolder() {
        val holder = PacketSchemaViewHolder(marker = 9)
        val raw = byteArrayOf(0x2A)
        val cursor = KompactCursor(raw)
        cursor.reset(raw, endBit = 7)
        val probe = KompactCursor(ByteArray(0))

        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, holder.decodeInto(cursor, probe))

        assertEquals(9, holder.marker)
        assertEquals(0, cursor.position)
    }

    @Test
    fun scalarFramedEncodePreflightsInvalidValueWithoutChangingOutput() {
        val raw = byteArrayOf(0x55)
        val holder = PacketSchemaViewHolder(marker = 256)
        val cursor = KompactCursor(raw)
        val probe = KompactCursor(ByteArray(0))

        assertEquals(KompactCursor.STATUS_INVALID_VALUE, holder.encodeFrom(cursor, probe))

        assertContentEquals(byteArrayOf(0x55), raw)
        assertEquals(0, cursor.position)
    }
}
