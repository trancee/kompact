package ch.trancee.kompact.result

/**
 * Spec: .scratch/kompact-spec/issues/06-validation-model.md
 *        .scratch/kompact-spec/issues/08-runtime-error-model.md
 *
 * Compact error codes packed into the high bits of every result
 * value class's `Long` payload (Ticket 08). Code 0 = success; non-zero
 * discriminates the typed error. Byte offset is NOT on the fast path
 * (Ticket 08 tradeoff); the opt-in `decodeFull()` diagnostics path
 * attaches the offset.
 */
public object KompactError {
    public const val Ok: Int = 0
    public const val BoundsError: Int = 1
    public const val BadLengthPrefix: Int = 2
    public const val TruncatedNested: Int = 3
    public const val UnknownEnumCode: Int = 4
    public const val UnsupportedSchemaVersion: Int = 5
}
