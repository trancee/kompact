package ch.trancee.kompact.runtime

public sealed interface KompactDecodeResult<out T> {
    public data class Success<T>(public val value: T) : KompactDecodeResult<T>

    public data class Failure(public val error: KompactDecodeError) : KompactDecodeResult<Nothing>
}

public sealed interface KompactDecodeError {
    public val status: KompactStatusCode

    public data class InvalidPacketLength(
        public val expectedLength: Int,
        public val actualLength: Int,
    ) : KompactDecodeError {
        override val status: KompactStatusCode = KompactStatusCode.INVALID_PACKET_LENGTH
    }

    public data class ReservedSchemaId(public val version: UByte) : KompactDecodeError {
        override val status: KompactStatusCode = KompactStatusCode.RESERVED_SCHEMA_ID
    }

    public data class UnknownSchemaId(public val schemaId: UShort, public val version: UByte) :
        KompactDecodeError {
        override val status: KompactStatusCode = KompactStatusCode.UNKNOWN_SCHEMA_ID
    }

    public data class UnsupportedLayoutVersion(
        public val schemaId: UShort,
        public val version: UByte,
    ) : KompactDecodeError {
        override val status: KompactStatusCode = KompactStatusCode.UNSUPPORTED_LAYOUT_VERSION
    }

    public data class NonzeroTailBits(
        public val schemaId: UShort,
        public val version: UByte,
        public val bitOffset: Int,
    ) : KompactDecodeError {
        override val status: KompactStatusCode = KompactStatusCode.NONZERO_TAIL_BITS
    }

    public data class UnknownEnumCode(
        public val schemaId: UShort,
        public val version: UByte,
        public val fieldPath: String,
        public val bitOffset: Int,
        public val arrayIndex: Int? = null,
    ) : KompactDecodeError {
        override val status: KompactStatusCode = KompactStatusCode.UNKNOWN_ENUM_CODE
    }

    public data class NonzeroReservedBits(
        public val schemaId: UShort,
        public val version: UByte,
        public val fieldPath: String,
        public val bitOffset: Int,
        public val arrayIndex: Int? = null,
    ) : KompactDecodeError {
        override val status: KompactStatusCode = KompactStatusCode.NONZERO_RESERVED_BITS
    }

    public data class NonzeroAbsentOptional(
        public val schemaId: UShort,
        public val version: UByte,
        public val fieldPath: String,
        public val bitOffset: Int,
        public val arrayIndex: Int? = null,
    ) : KompactDecodeError {
        override val status: KompactStatusCode = KompactStatusCode.NONZERO_ABSENT_OPTIONAL
    }

    public data class InternalInvariantFailure(public val operation: String) : KompactDecodeError {
        override val status: KompactStatusCode = KompactStatusCode.INTERNAL_INVARIANT_FAILURE
    }
}
