@file:OptIn(ch.trancee.kompact.annotations.KompactPreview::class)

package ch.trancee.kompact.compiletest

import ch.trancee.kompact.runtime.KompactFrameResult

/** Shared contract compiled against the generated platform implementation. */
public expect class PayloadSchemaView(
    raw: ByteArray,
    start: Int,
    end: Int,
) {
    public val raw: ByteArray
    public val start: Int
    public val end: Int
    public val value: Int

    public fun copy(value: Int = this.value): PayloadSchemaView

    public companion object {
        public fun decode(
            raw: ByteArray,
            start: Int = 0,
            end: Int = raw.size,
        ): KompactFrameResult<PayloadSchemaView>

        public fun create(value: Int): PayloadSchemaView
    }
}
