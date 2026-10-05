@file:OptIn(ch.trancee.kompact.annotations.KompactPreview::class)

package example

import ch.trancee.kompact.annotations.KompactField
import ch.trancee.kompact.annotations.KompactModel

@KompactModel(framed = true)
public class PacketSchema {
    @KompactField(order = 0, bitWidth = 8)
    public val marker: Int = 0
}

@KompactModel(framed = true)
public class ChildSchema {
    @KompactField(order = 0, bitWidth = 8)
    public val value: Int = 0
}

@KompactModel(framed = true)
public class BytePayloadSchema {
    @KompactField(order = 0, lengthPrefixWidth = 8)
    public val label: String = ""

    @KompactField(order = 1, lengthPrefixWidth = 8)
    public val payload: ByteArray = byteArrayOf()

    @KompactField(order = 2, lengthPrefixWidth = 8, isNested = true)
    public val child: ChildSchema = ChildSchema()

    @KompactField(order = 3, bitWidth = 8, repeatCountWidth = 8)
    public val codes: List<Int> = emptyList()

    @KompactField(order = 4, lengthPrefixWidth = 8, repeatCountWidth = 8)
    public val names: List<String> = emptyList()

    @KompactField(order = 5, lengthPrefixWidth = 8, repeatCountWidth = 8, isNested = true)
    public val children: List<ChildSchema> = emptyList()
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
