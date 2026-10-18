@file:OptIn(ch.trancee.kompact.annotations.KompactPreview::class)

package ch.trancee.kompact.compiletest

import ch.trancee.kompact.annotations.KompactField
import ch.trancee.kompact.annotations.KompactModel

@KompactModel(mutable = true)
public class SensorFrame {
    @KompactField(bitOffset = 0, bitWidth = 4)
    public val status: Int = 0

    @KompactField(bitOffset = 4, bitWidth = 12, signed = true)
    public val temperature: Int = 0

    @KompactField(bitOffset = 16, bitWidth = 1)
    public val alarm: Boolean = false
}
