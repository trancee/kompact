package ch.trancee.kompact.example

import ch.trancee.kompact.runtime.KompactDecodeError
import ch.trancee.kompact.runtime.KompactDecodeResult
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.KompactWriteError
import kotlin.jvm.JvmInline

public enum class BatteryStatus(public val code: UInt) {
    NORMAL(0u),
    LOW(1u),
    CRITICAL(2u),
}

public object VehicleTelemetry {
    public const val SCHEMA_ID: Int = 42
    public const val LAYOUT_VERSION: Int = 0
    public const val BODY_BIT_SIZE: Int = 16
    public const val PACKET_BYTE_SIZE: Int = 4

    public fun wrap(packet: ByteArray): KompactDecodeResult<VehicleTelemetryView> {
        if (packet.size < 2) {
            return KompactDecodeResult.Failure(
                KompactDecodeError.InvalidPacketLength(
                    expectedLength = 2,
                    actualLength = packet.size,
                )
            )
        }
        val envelope = KompactRuntime.readBits(packet, 0, 16).toInt()
        val schemaId = envelope and 0x0FFF
        val version = envelope ushr 12
        if (schemaId != SCHEMA_ID) {
            return KompactDecodeResult.Failure(
                KompactDecodeError.UnknownSchemaId(schemaId.toUShort(), version.toUByte())
            )
        }
        if (version != LAYOUT_VERSION) {
            return KompactDecodeResult.Failure(
                KompactDecodeError.UnsupportedLayoutVersion(schemaId.toUShort(), version.toUByte())
            )
        }
        if (packet.size != PACKET_BYTE_SIZE) {
            return KompactDecodeResult.Failure(
                KompactDecodeError.InvalidPacketLength(PACKET_BYTE_SIZE, packet.size)
            )
        }
        if (KompactRuntime.readBits(packet, 31, 1) != 0uL) {
            return KompactDecodeResult.Failure(
                KompactDecodeError.NonzeroReservedBits(
                    SCHEMA_ID.toUShort(),
                    LAYOUT_VERSION.toUByte(),
                    "future",
                    15,
                )
            )
        }
        val batteryCode = KompactRuntime.readBits(packet, 16, 4).toUInt()
        if (BatteryStatus.entries.none { it.code == batteryCode }) {
            return KompactDecodeResult.Failure(
                KompactDecodeError.UnknownEnumCode(
                    SCHEMA_ID.toUShort(),
                    LAYOUT_VERSION.toUByte(),
                    "battery_status",
                    0,
                )
            )
        }
        return KompactDecodeResult.Success(VehicleTelemetryView(packet))
    }

    public fun initialize(packet: ByteArray): KompactDecodeResult<VehicleTelemetryWriter> {
        if (packet.size != PACKET_BYTE_SIZE) {
            return KompactDecodeResult.Failure(
                KompactDecodeError.InvalidPacketLength(PACKET_BYTE_SIZE, packet.size)
            )
        }
        packet.fill(0)
        KompactRuntime.writeBits(packet, 0, 12, SCHEMA_ID.toULong())
        KompactRuntime.writeBits(packet, 12, 4, LAYOUT_VERSION.toULong())
        return KompactDecodeResult.Success(VehicleTelemetryWriter(packet))
    }

    public fun edit(packet: ByteArray): KompactDecodeResult<VehicleTelemetryWriter> =
        when (val result = wrap(packet)) {
            is KompactDecodeResult.Success ->
                KompactDecodeResult.Success(VehicleTelemetryWriter(packet))
            is KompactDecodeResult.Failure -> result
        }
}

@JvmInline
public value class VehicleTelemetryView internal constructor(internal val packet: ByteArray) {
    public val batteryStatus: BatteryStatus
        get() = BatteryStatus.entries[KompactRuntime.readBits(packet, 16, 4).toInt()]

    public val speed: UInt
        get() = KompactRuntime.readBits(packet, 20, 10).toUInt()

    public val isMalfunctioning: Boolean
        get() = KompactRuntime.readBitsBoolean(packet, 30)

    public fun contentEquals(other: VehicleTelemetryView): Boolean =
        packet.contentEquals(other.packet)

    public fun contentHashCode(): Int = packet.contentHashCode()
}

@JvmInline
public value class VehicleTelemetryWriter internal constructor(internal val packet: ByteArray) {
    public fun writeBatteryStatus(value: BatteryStatus): KompactWriteError? =
        KompactRuntime.writeBits(packet, 16, 4, value.code.toULong())

    public fun writeSpeed(value: UInt): KompactWriteError? =
        KompactRuntime.writeBits(packet, 20, 10, value.toULong())

    public fun writeIsMalfunctioning(value: Boolean): KompactWriteError? {
        KompactRuntime.writeBitsBoolean(packet, 30, value)
        return null
    }

    public fun view(): VehicleTelemetryView = VehicleTelemetryView(packet)
}
