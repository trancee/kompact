package ch.trancee.kompact.runtime

import ch.trancee.kompact.result.KompactError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spec: .scratch/kompact-spec/issues/09-versioning-schema-evolution.md
 *
 * Top-level version prefix:
 *  - `KompactVersionedStream.writeVersion(buf, version)` writes a
 *    little-endian `version` (UInt) at offset 0.
 *  - `KompactVersionedStream.readVersion(buf)` returns the version or
 *    fails fast with `UnsupportedSchemaVersion` on an unknown version
 *    (out of the supported range).
 *  - The version is the FIRST 4 bytes (32 bits) of any Kompact stream.
 */
class KompactVersionedStreamTest {

    @Test
    fun write_then_read_version_1() {
        val buf = ByteArray(8)
        val written = KompactVersionedStream.writeVersion(buf, version = 1u)
        assertEquals(4, written)
        val r = KompactVersionedStream.readVersion(buf)
        assertTrue(r.isOk)
        assertEquals(1u, r.value.toUInt())
    }

    @Test

    fun readVersion_after_payload() {
        val buf = ByteArray(8)
        KompactVersionedStream.writeVersion(buf, version = 1u)
        // Subsequent bytes are the schema's payload.
        KompactRuntime.writeBits(buf, bitOffset = 32, bitWidth = 8, value = 0xAB.toLong())
        val r = KompactVersionedStream.readVersion(buf)
        assertTrue(r.isOk)
        assertEquals(1u, r.value.toUInt())
        // Payload still readable.
        assertEquals(0xAB, KompactRuntime.readBits(buf, bitOffset = 32, bitWidth = 8))
    }

    @Test
    fun readVersion_unknown_returns_UnsupportedSchemaVersion() {
        val buf = ByteArray(8)
        KompactVersionedStream.writeVersion(buf, version = 99u)
        val r = KompactVersionedStream.readVersion(buf)
        assertFalse(r.isOk)
        assertEquals(KompactError.UnsupportedSchemaVersion, r.errorCode)
    }

    @Test
    fun readVersion_short_buffer_returns_BoundsError() {
        val buf = ByteArray(2) // less than 4 bytes
        val r = KompactVersionedStream.readVersion(buf)
        assertFalse(r.isOk)
        assertEquals(KompactError.BoundsError, r.errorCode)
    }

    @Test
    fun writeVersion_little_endian() {
        val buf = ByteArray(4)
        KompactVersionedStream.writeVersion(buf, version = 0x01020304u)
        assertEquals(0x04, buf[0].toInt() and 0xFF)
        assertEquals(0x03, buf[1].toInt() and 0xFF)
        assertEquals(0x02, buf[2].toInt() and 0xFF)
        assertEquals(0x01, buf[3].toInt() and 0xFF)
    }
}
