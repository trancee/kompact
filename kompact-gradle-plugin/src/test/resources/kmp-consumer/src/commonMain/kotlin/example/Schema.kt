@file:OptIn(ch.trancee.kompact.annotations.KompactPreview::class)

package example

import ch.trancee.kompact.annotations.KompactField
import ch.trancee.kompact.annotations.KompactModel

@KompactModel(framed = true)
public class PacketSchema {
    @KompactField(order = 0, bitWidth = 8)
    public val marker: Int = 0
}

@KompactModel
public expect value class ScalarSchema(public val raw: ByteArray) {
    public companion object {
        public fun create(value: Int): ScalarSchema
    }

    public fun copy(value: Int): ScalarSchema

    @KompactField(bitOffset = 0, bitWidth = 8)
    public val value: Int
}
