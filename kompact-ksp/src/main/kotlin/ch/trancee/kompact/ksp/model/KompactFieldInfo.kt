package ch.trancee.kompact.ksp.model

/**
 * Parsed metadata for a single `@KompactField`-annotated property.
 *
 * Extracted from the annotation at the call site or from the KSP
 * `KSAnnotation` — the two entry points feed the same validators
 * and generator, so the core logic is unit-testable without KSP.
 */
internal data class KompactFieldInfo(
    val name: String,
    val kotlinType: String,
    val bitOffset: Int,
    val bitWidth: Int,
    val signed: Boolean,
    val lengthPrefixWidth: Int,
    val isNested: Boolean,
    val repeatCountWidth: Int,
    val enumWidth: Int,
    val defaultValue: String,
) {
    /** The bit range [bitOffset, bitOffset + bitWidth) — exclusive upper bound. */
    val endBit: Int get() = bitOffset + bitWidth
}
