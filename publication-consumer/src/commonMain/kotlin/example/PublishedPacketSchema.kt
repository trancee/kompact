package example

import ch.trancee.kompact.annotations.KompactField
import ch.trancee.kompact.annotations.KompactSchema

@KompactSchema(registryName = "published_packet", id = 1, version = 0)
interface PublishedPacketSchema {
    @KompactField(stableName = "enabled", semanticType = "enabled", bitOffset = 0, bitWidth = 1)
    val enabled: Boolean
}
