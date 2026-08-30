package ch.trancee.kompact.runtime

public enum class KompactStatusCode(public val value: UByte) {
    OK(0x00u),
    NULL_ARGUMENT(0x01u),
    INVALID_PACKET_LENGTH(0x02u),
    RESERVED_SCHEMA_ID(0x03u),
    UNKNOWN_SCHEMA_ID(0x04u),
    UNSUPPORTED_LAYOUT_VERSION(0x05u),
    NONZERO_TAIL_BITS(0x06u),
    UNKNOWN_ENUM_CODE(0x07u),
    NONZERO_RESERVED_BITS(0x08u),
    NONZERO_ABSENT_OPTIONAL(0x09u),
    VALUE_OUT_OF_RANGE(0x0Au),
    INDEX_OUT_OF_RANGE(0x0Bu),
    INTERNAL_INVARIANT_FAILURE(0x0Cu),
}

public sealed interface KompactWriteError {
    public val status: KompactStatusCode

    public data class ValueOutOfRange(public val bitWidth: Int) : KompactWriteError {
        override val status: KompactStatusCode = KompactStatusCode.VALUE_OUT_OF_RANGE
    }

    public data class IndexOutOfRange(public val index: Int) : KompactWriteError {
        override val status: KompactStatusCode = KompactStatusCode.INDEX_OUT_OF_RANGE
    }

    public data class UnknownEnumCode(public val fieldPath: String) : KompactWriteError {
        override val status: KompactStatusCode = KompactStatusCode.UNKNOWN_ENUM_CODE
    }

    public data class InternalInvariantFailure(public val operation: String) : KompactWriteError {
        override val status: KompactStatusCode = KompactStatusCode.INTERNAL_INVARIANT_FAILURE
    }
}
