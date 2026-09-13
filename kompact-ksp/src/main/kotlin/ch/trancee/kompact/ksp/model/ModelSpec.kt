package ch.trancee.kompact.ksp.model

/**
 * Parsed metadata for a `@KompactModel`-annotated value class.
 *
 * Holds the class identity (package, simple name) and its ordered field list.
 */
internal data class ModelSpec(
    val packageName: String,
    val className: String,
    val fields: List<KompactFieldInfo>,
) {
    /** Total bit-size of the packed layout — the end of the last field. */
    val totalBits: Int get() = fields.maxOfOrNull { it.endBit } ?: 0

    /** Minimum buffer size in bytes required by the layout. */
    val minBufferSize: Int get() = (totalBits + 7) / 8
}
