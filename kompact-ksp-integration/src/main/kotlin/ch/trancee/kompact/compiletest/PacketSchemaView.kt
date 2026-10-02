@file:OptIn(ch.trancee.kompact.annotations.KompactPreview::class)

package ch.trancee.kompact.compiletest

import ch.trancee.kompact.runtime.KompactByteSlice
import ch.trancee.kompact.runtime.KompactFrameResult
import ch.trancee.kompact.runtime.KompactRepeatedView

/** Shared contract compiled against the generated platform implementation. */
public expect class PacketSchemaView(
    raw: ByteArray,
    start: Int,
    end: Int,
) {
    public val raw: ByteArray
    public val start: Int
    public val end: Int
    public val id: Int
    public val title: String
    public val payload: ByteArray
    public val payloadSlice: KompactByteSlice
    public val child: PayloadSchemaView
    public val samples: KompactRepeatedView<Int>
    public val titles: KompactRepeatedView<String>
    public val blobs: KompactRepeatedView<ByteArray>
    public val children: KompactRepeatedView<PayloadSchemaView>

    public fun copy(
        id: Int = this.id,
        title: String = this.title,
        payload: ByteArray = this.payload,
        child: PayloadSchemaView = this.child,
        samples: List<Int> = this.samples,
        titles: List<String> = this.titles,
        blobs: List<ByteArray> = this.blobs,
        children: List<PayloadSchemaView> = this.children,
    ): PacketSchemaView

    public companion object {
        public fun decode(
            raw: ByteArray,
            start: Int = 0,
            end: Int = raw.size,
        ): KompactFrameResult<PacketSchemaView>

        public fun create(
            id: Int,
            title: String,
            payload: ByteArray,
            child: PayloadSchemaView,
            samples: List<Int>,
            titles: List<String>,
            blobs: List<ByteArray>,
            children: List<PayloadSchemaView>,
        ): PacketSchemaView
    }
}
