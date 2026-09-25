@file:OptIn(KompactPreview::class)

package ch.trancee.kompact.generated

import ch.trancee.kompact.annotations.KompactField
import ch.trancee.kompact.annotations.KompactModel
import ch.trancee.kompact.annotations.KompactPreview
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.KompactWriter
import ch.trancee.kompact.runtime.ScalarType

/**
 * Concrete shared example (PROMPT §3), realized as an `expect value class` per
 * Ticket 03: a plain `value class` in common (no `@JvmInline`, per PROMPT §1)
 * backed by a single `ByteArray`. Platform actuals provide the member bodies;
 * the JVM actual is `@JvmInline` for zero-allocation wrapping, iOS uses a plain
 * actual value class (Ticket 03 reconciliation).
 *
 * Layout matrix (LSB-first), packed into 16 bits:
 * - [0..3]    (4 bits) : Battery Status Enum (0-15)
 * - [4..13]   (10 bits): Speed integer (0-1023)
 * - [14..14]  (1 bit)  : Is Engine Malfunction Active (Boolean)
 * - [15..15]  (1 bit)  : Reserved/Unused
 *
 * ADR-0006 D1/D3 shape. `VehicleTelemetry` is the immutable default view:
 * `val` fields read the packed bits, there are no in-place setters, and
 * `copy(...)` derives an updated frame (allocating a fresh 2-byte buffer via
 * `KompactWriter` — an outbound-frame operation, not the read hot path).
 *
 * For write-through mutation (read a BLE characteristic, tweak one field, and
 * re-send the same backing `ByteArray` with no allocation), opt the schema in
 * with `@KompactModel(mutable = true)`: the processor then emits a
 * `MutableVehicleTelemetry` sibling whose `var` properties write each field's
 * bit range in place on `raw` via `KompactRuntime.writeBits*`.
 *
 * The `ByteArray` is the wire format. A producer builds it via `KompactWriter`
 * or `VehicleTelemetry.create(...)`; a consumer reads fields via the
 * `@KompactField`-annotated properties. The default-view getters are checked
 * accessors (` KompactRuntime.readScalar` / `readBool` returning a
 * `KompactResult`) so untrusted input throws on a bounds error (Ticket 04/07),
 * trading one bounds-check per field for safety; the unchecked
 * ` KompactRuntime.readBits` fast path stays available for trusted in-memory
 * frames (Ticket 06).
 */
@KompactModel(mutable = true)
public expect value class VehicleTelemetry(
    public val raw: ByteArray,
) {
    public companion object {
        /**
         * Creates a fully-encoded frame from individual field values.
         * Allocates on the write path (`KompactWriter`'s growable buffer);
         * use this for outbound frames, not the read hot path.
         */
        public fun create(
            batteryStatus: Int,
            speed: Int,
            isMalfunctioning: Boolean,
        ): VehicleTelemetry
    }

    /**
     * Returns a new `VehicleTelemetry` copying `this` with any supplied fields
     * overridden. Each parameter defaults to the current value, so only the
     * fields you want to change need to be passed. Allocates a fresh buffer.
     */
    public fun copy(
        batteryStatus: Int = this.batteryStatus,
        speed: Int = this.speed,
        isMalfunctioning: Boolean = this.isMalfunctioning,
    ): VehicleTelemetry

    // F-001: the platform actuals validate raw.size >= 2 (the 16-bit layout,
    // bits 0-15) in their constructor init-blocks, failing fast with
    // IllegalArgumentException on a truncated buffer (Ticket 06). These
    // getters decode untrusted input via the checked `readScalar`/`readBool`
    // accessors and throw on a bounds error (Ticket 04/07), trading one
    // bounds-check per field for safety; the unchecked `KompactRuntime.readBits`
    // fast path stays available for trusted in-memory frames.

    @KompactField(bitOffset = 0, bitWidth = 4)
    public val batteryStatus: Int

    @KompactField(bitOffset = 4, bitWidth = 10)
    public val speed: Int

    @KompactField(bitOffset = 14, bitWidth = 1)
    public val isMalfunctioning: Boolean
}

/**
 * Write-through sibling emitted when `VehicleTelemetry` is annotated with
 * `@KompactModel(mutable = true)` (ADR-0006 D3, bounded escape hatch). The `var`
 * properties read the packed bits (checked) and write them in place on `raw` —
 * mutate a field and re-send the same buffer with no allocation. Construct a
 * fresh frame with `create(...)`; this sibling intentionally has no `copy`.
 */
public expect value class MutableVehicleTelemetry(
    public val raw: ByteArray,
) {
    public companion object {
        public fun create(
            batteryStatus: Int,
            speed: Int,
            isMalfunctioning: Boolean,
        ): MutableVehicleTelemetry
    }

    @KompactField(bitOffset = 0, bitWidth = 4)
    public var batteryStatus: Int

    @KompactField(bitOffset = 4, bitWidth = 10)
    public var speed: Int

    @KompactField(bitOffset = 14, bitWidth = 1)
    public var isMalfunctioning: Boolean
}

/**
 * Encodes the three VehicleTelemetry fields into a fresh 2-byte wire-format
 * buffer. Shared by the JVM and iOS `create` and `copy` factories to avoid
 * duplication. Allocates on the write path (`KompactWriter`'s growable buffer);
 * use this for outbound frames, not the read hot path.
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
