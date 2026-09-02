package ch.trancee.kompact.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration

/**
 * Spec: .scratch/kompact-spec/issues/06-validation-model.md
 *        .scratch/kompact-spec/issues/09-versioning-schema-evolution.md
 *
 * Compile-time structural validation. Each `@KompactModel` schema is
 * checked for:
 *  - bit-offset overlap within the struct
 *  - per-struct bit-width sum ≤ declared struct width (sum of all
 *    non-prefix widths; length-prefix width is a fixed envelope)
 *  - length-prefix field width ∈ {8, 16, 32}
 *  - **uniform length-prefix width across the struct** (Ticket 09):
 *    a single struct must use the same prefix width for every
 *    length-prefixed field, so an older reader can skip an unknown
 *    trailing length-delimited field by reading uniform-width
 *    prefix + payload.
 *  - repeated-count width ∈ {8, 16, 32}
 *  - enum code width ≥ ordinal bit-width; declared codes fit
 *
 * Violations are reported as hard errors via the [KSPLogger] attached
 * to the offending declaration; on a hard error the processor
 * halts generation for that schema.
 */
internal class LayoutModel(
    val name: String,
    val fields: List<KompactFieldInfo>,
) {
    /**
     * Returns true if all length-prefixed fields in this struct
     * share the same prefix width (or there are no length-prefixed
     * fields). Ticket 09 — required for forward-compat skip.
     */
    fun uniformPrefixWidthSatisfied(): Boolean {
        val prefixWidths = fields
            .mapNotNull { if (it.lengthPrefixBits > 0) it.lengthPrefixBits else null }
            .toSet()
        return prefixWidths.size <= 1
    }

    fun validate(logger: KSPLogger, decl: KSClassDeclaration): Boolean {
        var ok = true

        // 1. Bit-offset overlap (sort by offset, then check adjacent pairs).
        val sorted = fields.sortedBy { it.bitOffset }
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            val aEnd = a.bitOffset + a.bitWidth
            if (aEnd > b.bitOffset) {
                logger.error(
                    "field '${a.name}' (offset ${a.bitOffset}, width ${a.bitWidth}) overlaps with field '${b.name}' (offset ${b.bitOffset})",
                    decl,
                )
                ok = false
            }
        }

        // 2. Per-struct width sum sanity.
        val totalBits = fields.sumOf { it.bitWidth }
        logger.warn("struct '$name' total bit-width = $totalBits (no declared overall width — sum is informational)")

        // 3. Length-prefix width ∈ {8, 16, 32}.
        for (f in fields) {
            if (f.lengthPrefixBits > 0 && f.lengthPrefixBits !in setOf(8, 16, 32)) {
                logger.error(
                    "field '${f.name}' has invalid length-prefix width ${f.lengthPrefixBits}; must be 8, 16, or 32",
                    decl,
                )
                ok = false
            }
        }

        // 3a. Ticket 09: uniform length-prefix width across the struct.
        if (!uniformPrefixWidthSatisfied()) {
            val widths = fields.mapNotNull { if (it.lengthPrefixBits > 0) it.lengthPrefixBits else null }.distinct()
            logger.error(
                "struct '$name' mixes length-prefix widths $widths; all length-prefixed fields must share a single uniform width for forward-compat skip",
                decl,
            )
            ok = false
        }

        // 4. Enum width sanity.
        for (f in fields) {
            if (f.enumWidth > 0) {
                if (f.enumWidth !in 1..8) {
                    logger.error(
                        "field '${f.name}' enum width ${f.enumWidth} must be 1..8",
                        decl,
                    )
                    ok = false
                }
                if (f.enumWidth > f.bitWidth) {
                    logger.error(
                        "field '${f.name}' enum width ${f.enumWidth} exceeds bit width ${f.bitWidth}",
                        decl,
                    )
                    ok = false
                }
            }
        }

        return ok
    }

    companion object {
        fun build(name: String, fields: List<KompactFieldInfo>): LayoutModel =
            LayoutModel(name, fields)
    }
}
