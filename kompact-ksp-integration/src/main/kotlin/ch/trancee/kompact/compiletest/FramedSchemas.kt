@file:OptIn(ch.trancee.kompact.annotations.KompactPreview::class)

package ch.trancee.kompact.compiletest

import ch.trancee.kompact.annotations.KompactField
import ch.trancee.kompact.annotations.KompactModel

@KompactModel(framed = true)
public class PayloadSchema {
    @KompactField(order = 0, bitWidth = 16)
    public val value: Int = 0
}

@KompactModel(framed = true)
public class PacketSchema {
    @KompactField(order = 0, bitWidth = 8)
    public val id: Int = 0

    @KompactField(order = 1, lengthPrefixWidth = 8)
    public val title: String = ""

    @KompactField(order = 2, lengthPrefixWidth = 8)
    public val payload: ByteArray = byteArrayOf()

    @KompactField(order = 3, lengthPrefixWidth = 8, isNested = true)
    public val child: PayloadSchema = PayloadSchema()

    @KompactField(order = 4, bitWidth = 16, repeatCountWidth = 8)
    public val samples: List<Int> = emptyList()

    @KompactField(order = 5, lengthPrefixWidth = 8, repeatCountWidth = 8)
    public val titles: List<String> = emptyList()

    @KompactField(order = 6, lengthPrefixWidth = 8, repeatCountWidth = 8)
    public val blobs: List<ByteArray> = emptyList()

    @KompactField(order = 7, lengthPrefixWidth = 8, repeatCountWidth = 8, isNested = true)
    public val children: List<PayloadSchema> = emptyList()
}
