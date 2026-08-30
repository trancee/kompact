package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class KompactErrorContractTest {
    @Test
    fun decodeErrorsKeepAssignedStatusCodes() {
        val errors =
            listOf(
                KompactDecodeError.InvalidPacketLength(4, 3) to
                    KompactStatusCode.INVALID_PACKET_LENGTH,
                KompactDecodeError.ReservedSchemaId(0u) to KompactStatusCode.RESERVED_SCHEMA_ID,
                KompactDecodeError.UnknownSchemaId(42u, 0u) to KompactStatusCode.UNKNOWN_SCHEMA_ID,
                KompactDecodeError.UnsupportedLayoutVersion(42u, 1u) to
                    KompactStatusCode.UNSUPPORTED_LAYOUT_VERSION,
                KompactDecodeError.NonzeroTailBits(42u, 0u, 31) to
                    KompactStatusCode.NONZERO_TAIL_BITS,
                KompactDecodeError.UnknownEnumCode(42u, 0u, "status", 0) to
                    KompactStatusCode.UNKNOWN_ENUM_CODE,
                KompactDecodeError.NonzeroReservedBits(42u, 0u, "future", 15) to
                    KompactStatusCode.NONZERO_RESERVED_BITS,
                KompactDecodeError.NonzeroAbsentOptional(42u, 0u, "optional", 12) to
                    KompactStatusCode.NONZERO_ABSENT_OPTIONAL,
                KompactDecodeError.InternalInvariantFailure("decode") to
                    KompactStatusCode.INTERNAL_INVARIANT_FAILURE,
            )

        assertEquals(errors.map { it.second }, errors.map { it.first.status })
    }

    @Test
    fun writeErrorsKeepAssignedStatusCodes() {
        val errors =
            listOf(
                KompactWriteError.ValueOutOfRange(5) to KompactStatusCode.VALUE_OUT_OF_RANGE,
                KompactWriteError.IndexOutOfRange(3) to KompactStatusCode.INDEX_OUT_OF_RANGE,
                KompactWriteError.UnknownEnumCode("status") to KompactStatusCode.UNKNOWN_ENUM_CODE,
                KompactWriteError.InternalInvariantFailure("write") to
                    KompactStatusCode.INTERNAL_INVARIANT_FAILURE,
            )

        assertEquals(errors.map { it.second }, errors.map { it.first.status })
    }

    @Test
    fun statusCodeValuesMatchPublicAssignments() {
        assertEquals((0x00..0x0C).map(Int::toUByte), KompactStatusCode.entries.map { it.value })
    }
}
