package ch.trancee.kompact.generated

import ch.trancee.kompact.runtime.KompactField
import ch.trancee.kompact.runtime.KompactModel

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
 * Zero-copy read view (PROMPT §1 #2, §3): a producer serializes via
 * `KompactRuntime.writeBits` into a buffer, then wraps it; a consumer reads
 * fields via the val getters with no heap allocation. Read-only by design.
 */
@KompactModel
public expect value class VehicleTelemetry(public val raw: ByteArray) {

    // F-001: the platform actuals validate raw.size >= 2 (the 16-bit layout,
    // bits 0-15) in their constructor init-blocks, failing fast with
    // IllegalArgumentException on a truncated buffer (Ticket 06) rather than a
    // delayed AIOOBE at field-access. These getters stay the raw zero-alloc fast
    // path (Ticket 08:39); decode untrusted input via the checked accessors.

    @KompactField(bitOffset = 0, bitWidth = 4)
    public val batteryStatus: Int

    @KompactField(bitOffset = 4, bitWidth = 10)
    public val speed: Int

    @KompactField(bitOffset = 14, bitWidth = 1)
    public val isMalfunctioning: Boolean
}
