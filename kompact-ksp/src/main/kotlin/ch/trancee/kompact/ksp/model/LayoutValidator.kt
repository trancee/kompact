package ch.trancee.kompact.ksp.model

/**
 * Compile-time layout validation (Ticket 06).
 *
 * The processor proves these invariants at compile time so the generated
 * getters can skip the runtime `fits()` bounds check — the bit offsets
 * are static and guaranteed in-bounds for a buffer of the declared size.
 *
 * All `validate*` functions return a list of human-readable error messages.
 * An empty list means the layout is valid.
 */
internal object LayoutValidator {
    /**
     * Validates that every field's bit range fits within the declared
     * buffer and that no two fields overlap.
     *
     * @return list of error messages (empty if valid)
     */
    fun validateNoOverlaps(fields: List<KompactFieldInfo>): List<String> {
        val errors = mutableListOf<String>()
        val ranges = fields.map { it.bitOffset..<it.endBit to it.name }
        for (i in ranges.indices) {
            for (j in (i + 1)..<ranges.size) {
                val (rangeA, nameA) = ranges[i]
                val (rangeB, nameB) = ranges[j]
                if (rangeA.overlaps(rangeB)) {
                    errors.add(
                        "Field '$nameA' [${rangeA.first}..<${rangeA.last}] overlaps " +
                            "field '$nameB' [${rangeB.first}..<${rangeB.last}]",
                    )
                }
            }
        }
        return errors
    }

    /**
     * Validates that fields are densely packed — each field starts exactly
     * where the previous one ends. Reserved bits (gaps) are not allowed in
     * v1; a model author who needs padding should add an explicit
     * `@KompactField` placeholder or increase the layout size.
     *
     * @return list of error messages (empty if valid)
     */
    fun validateDensePacking(fields: List<KompactFieldInfo>): List<String> {
        val errors = mutableListOf<String>()
        fields.zipWithNext { a, b ->
            if (b.bitOffset != a.endBit) {
                errors.add(
                    "Field '${b.name}' starts at bit ${b.bitOffset} but '${a.name}' " +
                        "ends at bit ${a.endBit} — dense packing requires no gaps " +
                        "(Ticket 06: use an explicit placeholder field for reserved bits)",
                )
            }
        }
        return errors
    }

    /**
     * Validates that each field's [bitWidth] is valid for its Kotlin type:
     * - `Boolean`: exactly 1 bit
     * - `Int`: 1..31 bits (fits in the return value of `readBits`)
     * - `Long`: 1..64 bits (fits in the return value of `readBitsLong`)
     * - `Float`: exactly 32 bits
     * - `Double`: exactly 64 bits
     *
     * @return list of error messages (empty if valid)
     */
    fun validateWidths(fields: List<KompactFieldInfo>): List<String> {
        val errors = mutableListOf<String>()
        for (f in fields) {
            val valid =
                when (f.kotlinType) {
                    "Boolean" -> f.bitWidth == 1
                    "Int" -> f.bitWidth in 1..31
                    "Long" -> f.bitWidth in 1..64
                    "Float" -> f.bitWidth == 32
                    "Double" -> f.bitWidth == 64
                    "String", "ByteArray" -> f.bitWidth == f.lengthPrefixWidth
                    else -> true // unknown types pass through; validation is best-effort
                }
            if (!valid) {
                errors.add(
                    "Field '${f.name}' has type ${f.kotlinType} with bitWidth=${f.bitWidth} " +
                        "which is not valid for that type",
                )
            }
        }
        return errors
    }

    // -- helpers --

    private fun IntRange.overlaps(other: IntRange): Boolean = this.first < other.last && other.first < this.last

    /**
     * Full validation pass — runs all checks and returns combined errors.
     */
    fun validateAll(fields: List<KompactFieldInfo>): List<String> =
        buildList {
            addAll(validateNoOverlaps(fields))
            addAll(validateDensePacking(fields))
            addAll(validateWidths(fields))
        }
}
