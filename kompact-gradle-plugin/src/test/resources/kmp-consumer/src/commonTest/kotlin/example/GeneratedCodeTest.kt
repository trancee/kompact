@file:OptIn(ch.trancee.kompact.annotations.KompactPreview::class)

package example

import ch.trancee.kompact.runtime.KompactCursor
import ch.trancee.kompact.runtime.KompactByteRange
import ch.trancee.kompact.runtime.KompactRepeatWorkspace
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GeneratedCodeTest {
    @Test
    fun framedAndFixedViewsUseGeneratedCommonContracts() {
        val packet = PacketSchemaView.create(marker = 7)
        val scalar = ScalarSchema.create(value = 42)

        assertEquals(7, PacketSchemaView.decode(packet.raw).getOrThrow().marker)
        assertEquals(42, scalar.value)
    }

    @Test
    fun generatedFixedHolderEncodesAndDecodesInCallerOwnedStorage() {
        val raw = byteArrayOf(0xFF.toByte())
        val source = ScalarSchemaHolder(value = 42)
        val writer = KompactCursor(raw)

        assertEquals(KompactCursor.STATUS_OK, source.encodeFrom(writer))
        assertContentEquals(byteArrayOf(42), raw)
        assertEquals(8, writer.position)

        val decoded = ScalarSchemaHolder()
        val reader = KompactCursor(raw)

        assertEquals(KompactCursor.STATUS_OK, decoded.decodeInto(reader))
        assertEquals(42, decoded.value)
        assertEquals(8, reader.position)
    }

    @Test
    fun generatedFixedHolderRejectsOutOfRangeValueWithoutMutatingOutput() {
        val raw = byteArrayOf(0x55)
        val holder = ScalarSchemaHolder(value = 256)
        val cursor = KompactCursor(raw)

        assertEquals(KompactCursor.STATUS_INVALID_VALUE, holder.encodeFrom(cursor))

        assertContentEquals(byteArrayOf(0x55), raw)
        assertEquals(0, cursor.position)
    }

    @Test
    fun generatedFixedHolderRejectsTruncatedInputWithoutChangingHolder() {
        val raw = byteArrayOf(0x2A)
        val holder = ScalarSchemaHolder(value = 9)
        val cursor = KompactCursor(raw)
        cursor.reset(raw, endBit = 7)

        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, holder.decodeInto(cursor))

        assertEquals(9, holder.value)
        assertEquals(0, cursor.position)
    }

    @Test
    fun generatedFramedHolderEncodesAndDecodesInCallerOwnedStorage() {
        val raw = byteArrayOf(0)
        val source = PacketSchemaViewHolder(marker = 7)
        val writer = KompactCursor(raw)

        assertEquals(KompactCursor.STATUS_OK, source.encodeFrom(writer))
        assertContentEquals(byteArrayOf(7), raw)
        assertEquals(8, writer.position)

        val decoded = PacketSchemaViewHolder()
        val reader = KompactCursor(raw)

        assertEquals(KompactCursor.STATUS_OK, decoded.decodeInto(reader))
        assertEquals(7, decoded.marker)
        assertEquals(8, reader.position)
    }

    @Test
    fun generatedFramedHolderRejectsInvalidValuesWithoutMutatingOutput() {
        val raw = byteArrayOf(0x55)
        val holder = PacketSchemaViewHolder(marker = 256)
        val cursor = KompactCursor(raw)

        assertEquals(KompactCursor.STATUS_INVALID_VALUE, holder.encodeFrom(cursor))

        assertContentEquals(byteArrayOf(0x55), raw)
        assertEquals(0, cursor.position)
    }

    @Test
    fun generatedFramedHolderRejectsTruncatedInputWithoutChangingHolder() {
        val raw = byteArrayOf(0x2A)
        val holder = PacketSchemaViewHolder(marker = 9)
        val cursor = KompactCursor(raw)
        cursor.reset(raw, endBit = 7)

        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, holder.decodeInto(cursor))

        assertEquals(9, holder.marker)
        assertEquals(0, cursor.position)
    }

    @Test
    fun generatedFramedHolderBorrowsUtf8AndBlobRanges() {
        val raw =
            byteArrayOf(
                2, 0xC3.toByte(), 0xA9.toByte(),
                2, 1, 2,
                1, 0x77,
                2, 0x10, 0x20,
                2, 1, 0x41,
                2, 0x4F, 0x4B,
                1, 1, 0x33,
            )
        val holder = newBytePayloadHolder(workspaceCapacity = 1)
        val reader = KompactCursor(raw)

        assertEquals(KompactCursor.STATUS_OK, holder.decodeInto(reader, KompactCursor(raw)))

        assertEquals(2, holder.label.size)
        assertEquals(0xC3.toByte(), holder.label.buffer[holder.label.start])
        assertEquals(0xA9.toByte(), holder.label.buffer[holder.label.start + 1])
        assertEquals(2, holder.payload.size)
        assertEquals(1.toByte(), holder.payload.buffer[holder.payload.start])
        assertEquals(2.toByte(), holder.payload.buffer[holder.payload.start + 1])
        assertEquals(1, holder.child.size)
        assertEquals(0x77.toByte(), holder.child.buffer[holder.child.start])
        assertEquals(raw.size * 8, reader.position)

        val childHolder = ChildSchemaViewHolder()
        val childCursor = KompactCursor(raw)
        assertEquals(
            KompactCursor.STATUS_OK,
            holder.decodeChildInto(childHolder, childCursor, KompactCursor(raw)),
        )
        assertEquals(0x77, childHolder.value)

        val elementCursor = KompactCursor(raw)
        assertEquals(KompactCursor.STATUS_OK, holder.decodeCodesElement(1, reader, elementCursor))
        assertEquals(0x20, holder.codesElement)
        assertEquals(KompactCursor.STATUS_OK, holder.decodeNamesElement(1, reader, elementCursor))
        assertEquals(2, holder.namesElement.size)
        assertEquals('O'.code.toByte(), holder.namesElement.buffer[holder.namesElement.start])
        assertEquals('K'.code.toByte(), holder.namesElement.buffer[holder.namesElement.start + 1])
        val childElementHolder = ChildSchemaViewHolder()
        assertEquals(
            KompactCursor.STATUS_OK,
            holder.decodeChildrenElementInto(
                0,
                reader,
                elementCursor,
                childElementHolder,
                KompactCursor(raw),
                KompactCursor(raw),
            ),
        )
        assertEquals(0x33, childElementHolder.value)

        val output = ByteArray(raw.size)
        val writer = KompactCursor(output)
        val probe = KompactCursor(output)

        assertEquals(KompactCursor.STATUS_OK, holder.encodeFrom(writer, probe))

        assertContentEquals(raw, output)
        assertEquals(raw.size * 8, writer.position)
    }

    @Test
    fun generatedFramedHolderRejectsInvalidUtf8WithoutChangingRangeOrCursor() {
        val original = byteArrayOf(0x55)
        val originalRange = KompactByteRange(original)
        originalRange.reset(original, end = 1)
        val holder = newBytePayloadHolder(label = originalRange)
        val malformed = byteArrayOf(1, 0xFF.toByte(), 0, 0)
        val reader = KompactCursor(malformed)
        val probe = KompactCursor(malformed)

        assertEquals(KompactCursor.STATUS_INVALID_UTF8, holder.decodeInto(reader, probe))

        assertSame(original, holder.label.buffer)
        assertEquals(1, holder.label.size)
        assertEquals(0, reader.position)
    }

    @Test
    fun generatedFramedHolderRejectsInvalidUtf8BeforeWritingOutput() {
        val invalid = byteArrayOf(0xFF.toByte())
        val invalidRange = KompactByteRange(invalid)
        invalidRange.reset(invalid, end = invalid.size)
        val holder = newBytePayloadHolder(label = invalidRange)
        val output = byteArrayOf(0x55, 0x66, 0x77, 0x22)
        val writer = KompactCursor(output)
        val probe = KompactCursor(output)

        assertEquals(KompactCursor.STATUS_INVALID_UTF8, holder.encodeFrom(writer, probe))

        assertContentEquals(byteArrayOf(0x55, 0x66, 0x77, 0x22), output)
        assertEquals(0, writer.position)
    }

    @Test
    fun generatedFramedHolderRejectsRepeatWorkspaceShortageAtomically() {
        val original = byteArrayOf(0x55)
        val originalRange = KompactByteRange(original)
        val holder = newBytePayloadHolder(label = originalRange)
        val raw = byteArrayOf(0, 0, 1, 0x77, 2, 0x10, 0x20)
        val cursor = KompactCursor(raw)

        assertEquals(
            KompactCursor.STATUS_WORKSPACE_TOO_SMALL,
            holder.decodeInto(cursor, KompactCursor(raw)),
        )

        assertSame(original, holder.label.buffer)
        assertEquals(1, holder.label.size)
        assertEquals(0, holder.codesWorkspace.count)
        assertEquals(0, cursor.position)
    }

    @Test
    fun generatedFramedHolderRejectsAliasedDecodeCursors() {
        val raw = byteArrayOf(0, 0, 0, 0, 0, 0)
        val holder = newBytePayloadHolder()
        val cursor = KompactCursor(raw)

        assertEquals(KompactCursor.STATUS_INVALID_ARGUMENT, holder.decodeInto(cursor, cursor))

        assertEquals(0, cursor.position)
    }

    @Test
    fun generatedFramedHolderRejectsAliasedEncodeCursors() {
        val output = ByteArray(6)
        val holder = newBytePayloadHolder()
        val cursor = KompactCursor(output)

        assertEquals(KompactCursor.STATUS_INVALID_ARGUMENT, holder.encodeFrom(cursor, cursor))

        assertContentEquals(ByteArray(6), output)
        assertEquals(0, cursor.position)
    }

    private fun newBytePayloadHolder(
        label: KompactByteRange = KompactByteRange(ByteArray(0)),
        workspaceCapacity: Int = 0,
    ): BytePayloadSchemaViewHolder =
        BytePayloadSchemaViewHolder(
            label = label,
            payload = KompactByteRange(ByteArray(0)),
            child = KompactByteRange(ByteArray(0)),
            codesWorkspace = KompactRepeatWorkspace(IntArray(workspaceCapacity)),
            codesBytes = KompactByteRange(ByteArray(0)),
            namesWorkspace = KompactRepeatWorkspace(IntArray(workspaceCapacity)),
            namesBytes = KompactByteRange(ByteArray(0)),
            namesElement = KompactByteRange(ByteArray(0)),
            childrenWorkspace = KompactRepeatWorkspace(IntArray(workspaceCapacity)),
            childrenBytes = KompactByteRange(ByteArray(0)),
            childrenElement = KompactByteRange(ByteArray(0)),
        )
}
