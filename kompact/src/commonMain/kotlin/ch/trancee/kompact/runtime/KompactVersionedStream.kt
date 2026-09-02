package ch.trancee.kompact.runtime

import ch.trancee.kompact.result.IntResult
import ch.trancee.kompact.result.KompactError

/**
 * Spec: .scratch/kompact-spec/issues/09-versioning-schema-evolution.md
 *
 * Top-level version prefix for a Kompact stream. The first 4 bytes
 * are a little-endian `UInt` version number. A reader that sees an
 * unsupported version fails fast with `UnsupportedSchemaVersion`
 * (Ticket 06 + 09) — never silently misread.
 *
 * v1 supports version `1u`; v2+ will register additional supported
 * versions. The default `SUPPORTED_VERSIONS` set is `1u` only; a
 * library user can override it via [setSupportedVersions] before
 * calling [readVersion].
 */
public object KompactVersionedStream {

    private var supportedVersions: Set<UInt> = setOf(1u)


    /**
     * Override the set of supported schema versions. Pass a set
     * containing every version this build of the reader can decode.
     */
    public fun setSupportedVersions(versions: Set<UInt>) {
        supportedVersions = versions
    }

    public fun supportedVersions(): Set<UInt> = supportedVersions

    /**
     * Write the version prefix at offset 0. Returns the number of
     * bytes written (always 4). The buffer must be at least 4 bytes.
     */
    public fun writeVersion(buf: ByteArray, version: UInt): Int {
        require(buf.size >= 4) { "buffer must be at least 4 bytes for the version prefix" }
        buf[0] = (version.toInt() and 0xFF).toByte()
        buf[1] = ((version.toInt() ushr 8) and 0xFF).toByte()
        buf[2] = ((version.toInt() ushr 16) and 0xFF).toByte()
        buf[3] = ((version.toInt() ushr 24) and 0xFF).toByte()
        return 4
    }

    /**
     * Read the version prefix from the start of the buffer. Returns
     * the version on success, or a typed failure (BoundsError if the
     * buffer is too short; UnsupportedSchemaVersion if the version is
     * not in the supported set).
     */
    public fun readVersion(buf: ByteArray): IntResult {
        if (buf.size < 4) {
            return IntResult.failure(KompactError.BoundsError)
        }
        val version = (buf[0].toInt() and 0xFF) or
            ((buf[1].toInt() and 0xFF) shl 8) or
            ((buf[2].toInt() and 0xFF) shl 16) or
            ((buf[3].toInt() and 0xFF) shl 24)
        val asUInt = version.toUInt()
        if (asUInt !in supportedVersions) {
            return IntResult.failure(KompactError.UnsupportedSchemaVersion)
        }
        return IntResult.success(version)
    }
}
