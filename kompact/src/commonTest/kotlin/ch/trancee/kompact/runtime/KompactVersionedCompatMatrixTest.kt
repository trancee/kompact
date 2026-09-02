package ch.trancee.kompact.runtime

import ch.trancee.kompact.result.KompactError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spec: .scratch/kompact-spec/issues/10-cross-platform-testing-model.md
 *        .scratch/kompact-spec/issues/09-versioning-schema-evolution.md
 *
 * Cross-version compatibility matrix:
 *  - Newer writer → older reader (skip): handled by uniform
 *    length-prefix width (Ticket 09) plus per-field default values.
 *  - Older writer → newer reader (defaults): handled by
 *    `readXxxWithDefault` falling back when buffer is short.
 *  - Version skew (unknown version): fail-fast
 *    `UnsupportedSchemaVersion` (Ticket 06/09).
 *  - Malformed length-prefix (> remaining): fail-fast
 *    `BadLengthPrefix` (Ticket 06).
 */
class KompactVersionedCompatMatrixTest {

    @Test
    fun version_skew_returns_UnsupportedSchemaVersion() {
        val buf = ByteArray(8)
        KompactVersionedStream.writeVersion(buf, version = 1u)
        KompactRuntime.writeBits(buf, bitOffset = 32, bitWidth = 8, value = 0x42)
        val v = KompactVersionedStream.readVersion(buf)
        assertTrue(v.isOk)
        assertEquals(1u, v.value.toUInt())
        KompactVersionedStream.writeVersion(buf, version = 99u)
        val v2 = KompactVersionedStream.readVersion(buf)
        assertFalse(v2.isOk)
        assertEquals(KompactError.UnsupportedSchemaVersion, v2.errorCode)
    }

    @Test
    fun newer_reader_sees_older_writer_with_defaults() {
        val buf = ByteArray(4)
        KompactVersionedStream.writeVersion(buf, version = 1u)
        val v = KompactRead.readUInt8WithDefault(buf, bitOffset = 32, default = 42)
        assertEquals(42, v)
    }

    @Test
    fun older_reader_skips_newer_writer_length_prefixed_field() {
        val buf = ByteArray(16)
        KompactVersionedStream.writeVersion(buf, version = 1u)
        KompactRuntime.writeBits(buf, bitOffset = 32, bitWidth = 8, value = 0xAB)
        KompactRead.writeLengthPrefix(buf, 40, 8, 5)
        val field1 = KompactRead.readUInt8(buf, bitOffset = 32)
        assertTrue(field1.isOk)
        assertEquals(0xAB, field1.value)
        val skip = KompactRead.readSkipLengthPrefixed(buf, 40, 8)
        assertTrue(skip.isOk)
        assertEquals(88, skip.value)
    }

    @Test
    fun malformed_prefix_returns_BadLengthPrefix() {
        val buf = ByteArray(8)
        KompactVersionedStream.writeVersion(buf, version = 1u)
        KompactRuntime.writeBits(buf, bitOffset = 40, bitWidth = 8, value = 100)
        val r = KompactRead.readString(buf, bitOffset = 40, lengthPrefixBits = 8)
        assertFalse(r.isOk)
        assertEquals(KompactError.BadLengthPrefix, r.errorCode)
    }

    @Test
    fun newer_reader_sees_missing_field_returns_declared_default() {
        val buf = ByteArray(4)
        KompactVersionedStream.writeVersion(buf, version = 1u)
        val v = KompactRead.readUInt8WithDefault(buf, bitOffset = 32, default = 0xA)
        assertEquals(0xA, v)
    }
}
