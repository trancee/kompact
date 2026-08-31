package example

import ch.trancee.kompact.runtime.KompactDecodeResult

fun initializePublishedPacket(packet: ByteArray): KompactDecodeResult<PublishedPacketWriter> =
    PublishedPacket.initialize(packet)
