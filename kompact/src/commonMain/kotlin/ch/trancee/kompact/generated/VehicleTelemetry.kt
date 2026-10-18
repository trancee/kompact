@file:OptIn(KompactPreview::class)

package ch.trancee.kompact.generated

import ch.trancee.kompact.annotations.KompactField
import ch.trancee.kompact.annotations.KompactModel
import ch.trancee.kompact.annotations.KompactPreview
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.KompactWriter
import ch.trancee.kompact.runtime.ScalarType
import kotlin.jvm.JvmInline

/**
 * Concrete shared example (PROMPT §3), implemented as a common `@JvmInline value class`
 * backed by a `ByteArray`. Its
 * value-class representation does not guarantee allocation behavior at every
 * call site. See the allocation and boxing research note for the evidence
 * needed to support a performance claim.
 *
 * Layout matrix (LSB-first), packed into 16 bits:
 * - [0..3]    (4 bits) : Battery Status Enum (0-15)
 * - [4..13]   (10 bits): Speed integer (0-1023)
 * - [14..14]  (1 bit)  : Is Engine Malfunction Active (Boolean)
 * - [15..15]  (1 bit)  : Reserved/Unused
 *
 * ADR-0006 D1/D3 shape. `VehicleTelemetry` is the immutable default view:
 * `val` fields read the packed bits, there are no in-place setters, and
 * `copy(...)` derives an updated frame backed by a fresh 2-byte buffer via
 * `KompactWriter`.
 *
 * For write-through mutation (read a BLE characteristic, tweak one field, and
 * re-send the same backing `ByteArray`), opt the schema in with
 * `@KompactModel(mutable = true)`: the processor then emits a
 * `MutableVehicleTelemetry` sibling whose `var` properties write each field's
 * bit range in place on `raw` via `KompactRuntime.writeBits*`. Performance
 * depends on the call site and must be measured for a target workload.
 *
 * The `ByteArray` is the wire format. A producer builds it via `KompactWriter`
 * or `VehicleTelemetry.create(...)`; a consumer reads fields via the
 * `@KompactField`-annotated properties. The default-view getters are checked
 * accessors (`KompactRuntime.readScalar` / `readBool` returning an
 * `IntResult` / `BooleanResult`). A truncated input is rejected by the
 * constructor, and the unchecked `KompactRuntime.readBits` primitives remain
 * available for layouts whose bounds are already known (Ticket 06).
 */
@KompactModel(mutable = true)
@JvmInline
public value class VehicleTelemetry(
    public val raw: ByteArray,
) {
    init {
        require(raw.size >= 2) {
            "VehicleTelemetry requires a buffer of at least 2 bytes (16-bit layout, bits 0-15); got ${raw.size}"
        }
    }

    public companion object {
        public fun create(
            batteryStatus: Int,
            speed: Int,
            isMalfunctioning: Boolean,
        ): VehicleTelemetry = VehicleTelemetry(encodeVehicleTelemetry(batteryStatus, speed, isMalfunctioning))
    }

    public fun copy(
        batteryStatus: Int = this.batteryStatus,
        speed: Int = this.speed,
        isMalfunctioning: Boolean = this.isMalfunctioning,
    ): VehicleTelemetry = VehicleTelemetry(encodeVehicleTelemetry(batteryStatus, speed, isMalfunctioning))

    @KompactField(bitOffset = 0, bitWidth = 4)
    public val batteryStatus: Int
        get() = KompactRuntime.readScalar(raw, 0, ScalarType.of(4, signed = false)).getOrThrow()

    @KompactField(bitOffset = 4, bitWidth = 10)
    public val speed: Int
        get() = KompactRuntime.readScalar(raw, 4, ScalarType.of(10, signed = false)).getOrThrow()

    @KompactField(bitOffset = 14, bitWidth = 1)
    public val isMalfunctioning: Boolean
        get() = KompactRuntime.readBool(raw, 14).getOrThrow()
}

/**
 * Write-through sibling emitted when `VehicleTelemetry` is annotated with
 * `@KompactModel(mutable = true)` (ADR-0006 D3, bounded escape hatch). The `var`
 * properties read the packed bits (checked) and write them in place on `raw`.
 * Use `create(...)` to construct a fresh frame; this sibling intentionally has
 * no `copy`.
 */
@JvmInline
public value class MutableVehicleTelemetry(
    public val raw: ByteArray,
) {
    init {
        require(raw.size >= 2) {
            "MutableVehicleTelemetry requires a buffer of at least 2 bytes (16-bit layout, bits 0-15); got ${raw.size}"
        }
    }

    public companion object {
        public fun create(
            batteryStatus: Int,
            speed: Int,
            isMalfunctioning: Boolean,
        ): MutableVehicleTelemetry = MutableVehicleTelemetry(encodeVehicleTelemetry(batteryStatus, speed, isMalfunctioning))
    }

    @KompactField(bitOffset = 0, bitWidth = 4)
    public var batteryStatus: Int
        get() = KompactRuntime.readScalar(raw, 0, ScalarType.of(4, signed = false)).getOrThrow()
        set(value) {
            KompactRuntime.writeBits(raw, 0, 4, value)
        }

    @KompactField(bitOffset = 4, bitWidth = 10)
    public var speed: Int
        get() = KompactRuntime.readScalar(raw, 4, ScalarType.of(10, signed = false)).getOrThrow()
        set(value) {
            KompactRuntime.writeBits(raw, 4, 10, value)
        }

    @KompactField(bitOffset = 14, bitWidth = 1)
    public var isMalfunctioning: Boolean
        get() = KompactRuntime.readBool(raw, 14).getOrThrow()
        set(value) {
            KompactRuntime.writeBitsBoolean(raw, 14, value)
        }
}

/**
 * Encodes the three VehicleTelemetry fields into a fresh 2-byte wire-format
 * buffer. Shared by the JVM and iOS `create` and `copy` factories to avoid
 * duplication.
 */
internal fun encodeVehicleTelemetry(
    batteryStatus: Int,
    speed: Int,
    isMalfunctioning: Boolean,
): ByteArray {
    val w = KompactWriter()
    w.writeScalar(ScalarType.of(4, signed = false), batteryStatus.toLong())
    w.writeScalar(ScalarType.of(10, signed = false), speed.toLong())
    w.writeBool(isMalfunctioning)
    return w.build()
}
