@file:OptIn(ch.trancee.kompact.annotations.KompactPreview::class)

package ch.trancee.kompact.benchmark.holders

import ch.trancee.kompact.annotations.KompactField
import ch.trancee.kompact.annotations.KompactModel

@KompactModel(framed = true)
public class ScalarFrame {
    @KompactField(order = 0, bitWidth = 16)
    public val id: Int = 0

    @KompactField(order = 1, bitWidth = 32)
    public val sequence: Int = 0

    @KompactField(order = 2, bitWidth = 64)
    public val timestamp: Long = 0
}

@KompactModel(framed = true)
public class FixedFrame {
    @KompactField(order = 0, bitWidth = 16)
    public val id: Int = 0

    @KompactField(order = 1, bitWidth = 32)
    public val sequence: Int = 0

    @KompactField(order = 2, lengthPrefixWidth = 16)
    public val payload: ByteArray = byteArrayOf()

    @KompactField(order = 3, bitWidth = 16, repeatCountWidth = 32)
    public val samples: List<Int> = emptyList()
}

@KompactModel(framed = true)
public class VariableFrame {
    @KompactField(order = 0, bitWidth = 16)
    public val id: Int = 0

    @KompactField(order = 1, bitWidth = 32)
    public val sequence: Int = 0

    @KompactField(order = 2, lengthPrefixWidth = 16)
    public val payload: ByteArray = byteArrayOf()

    @KompactField(order = 3, lengthPrefixWidth = 8, repeatCountWidth = 32)
    public val chunks: List<ByteArray> = emptyList()
}
