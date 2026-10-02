@file:OptIn(ch.trancee.kompact.annotations.KompactPreview::class)

package example

import kotlin.test.Test
import kotlin.test.assertEquals

class GeneratedCodeTest {
    @Test
    fun framedAndFixedViewsUseGeneratedCommonContracts() {
        val packet = PacketSchemaView.create(marker = 7)
        val scalar = ScalarSchema.create(value = 42)

        assertEquals(7, PacketSchemaView.decode(packet.raw).getOrThrow().marker)
        assertEquals(42, scalar.value)
    }
}
