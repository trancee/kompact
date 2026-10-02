@file:OptIn(ch.trancee.kompact.annotations.KompactPreview::class)

package ch.trancee.kompact.compiletest

import ch.trancee.kompact.runtime.KompactDecodeError
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class FramedGeneratedCodeTest {
    @Test
    fun generatedViewsCompileAndRoundTripAllSupportedFieldShapes() {
        val originalPayload = byteArrayOf(4, 5)
        val child = PayloadSchemaView.create(42)
        val packet =
            PacketSchemaView.create(
                id = 7,
                title = "sensor",
                payload = originalPayload,
                child = child,
                samples = listOf(10, 20),
                titles = listOf("one", "two"),
                blobs = listOf(byteArrayOf(1), byteArrayOf(2, 3)),
                children = listOf(child),
            )

        val decoded = PacketSchemaView.decode(packet.raw).getOrThrow()

        assertEquals(7, decoded.id)
        assertEquals("sensor", decoded.title)
        assertContentEquals(originalPayload, decoded.payload)
        assertSame(packet.raw, decoded.payloadSlice.raw)
        assertContentEquals(originalPayload, decoded.payloadSlice.toByteArray())
        assertEquals(42, decoded.child.value)
        assertEquals(listOf(10, 20), decoded.samples.toList())
        assertEquals(listOf("one", "two"), decoded.titles.toList())
        assertContentEquals(byteArrayOf(2, 3), decoded.blobs[1])
        val repeatedBlobSlice = decoded.blobs.getElementSlice(1).getOrThrow()
        assertSame(packet.raw, repeatedBlobSlice.raw)
        assertContentEquals(byteArrayOf(2, 3), repeatedBlobSlice.toByteArray())
        assertEquals(42, decoded.children[0].value)
        assertEquals("updated", decoded.copy(title = "updated").title)
        assertEquals(KompactDecodeError.BoundsError, PacketSchemaView.decode(byteArrayOf()).error)
    }
}
