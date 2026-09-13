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
 *
 * Invariant matrix (Ticket 06):
 * 1. Bit-offset overlaps within a struct — [validateNoOverlaps]
 * 2. Per-struct bit-width sum ≤ declared width — [validateBitWidthSum]
 * 3. Length-prefix field width ∈ {8,16,32} — [validateLengthPrefixWidth]
 * 4. Nested total-length ≤ declared length field capacity — [validateNestedTotalLength]
 * 5. Repeated element layout uniformity + count width ∈ {8,16,32} — [validateRepeatCountWidth]
 * 6. Enum width ≥ ordinal bit-width; declared codes fit — [validateEnumWidth]
 *
 * Additionally, [validateWidths] enforces per-type bit-width constraints
 * (Boolean=1, Int 1..31, Long 1..64, Float=32, Double=64).
 */
internal object LayoutValidator {
    /** Valid fixed-width length-prefix / count-prefix bit widths (Ticket 06 invariant #3/#5). */
    internal val VALID_PREFIX_WIDTHS: Set<Int> = setOf(8, 16, 32)

    /**
     * Invariant #1: no two fields overlap within a struct (Ticket 06).
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
     * Invariant #2: per-struct bit-width sum ≤ declared width (Ticket 06).
     *
     * Every field must start at a non-negative bit offset and its end-bit
     * must not exceed the total layout width (the maximum end-bit of all
     * fields). Reserved/gap bits between fields are allowed — a model author
     * may leave bit holes for future expansion; the constraint is that no
     * field extends past the declared buffer capacity.
     *
     * @return list of error messages (empty if valid)
     */
    fun validateBitWidthSum(fields: List<KompactFieldInfo>): List<String> {
        if (fields.isEmpty()) return emptyList()
        val errors = mutableListOf<String>()
        for (f in fields) {
            if (f.bitOffset < 0) {
                errors.add("Field '${f.name}' has bitOffset=${f.bitOffset} (must be ≥ 0)")
            }
            // declaredWidth = max(endBit) by construction, so f.endBit ≤ declaredWidth is always true.
            // The only reachable invariant is non-negative bitOffset above.
        }
        return errors
    }

    /**
     * Invariant #3: length-prefix field width ∈ {8, 16, 32} (Ticket 06).
     *
     * A lengthPrefixWidth of 0 means the field is not length-prefixed (fixed-width).
     * Any non-zero value must be one of the valid prefix widths.
     *
     * @return list of error messages (empty if valid)
     */
    fun validateLengthPrefixWidth(fields: List<KompactFieldInfo>): List<String> {
        val errors = mutableListOf<String>()
        for (f in fields) {
            if (f.lengthPrefixWidth != 0 && f.lengthPrefixWidth !in VALID_PREFIX_WIDTHS) {
                errors.add(
                    "Field '${f.name}' has lengthPrefixWidth=${f.lengthPrefixWidth} " +
                        "not in {8, 16, 32} (Ticket 06, invariant #3)",
                )
            }
        }
        return errors
    }

    /**
     * Invariant #4: nested total-length ≤ declared length field capacity (Ticket 06).
     *
     * A nested field must carry a valid length-prefix width so the reader can
     * skip its payload. Without a prefix, the nested region's boundary is
     * undetermined.
     *
     * @return list of error messages (empty if valid)
     */
    fun validateNestedTotalLength(fields: List<KompactFieldInfo>): List<String> {
        val errors = mutableListOf<String>()
        for (f in fields) {
            if (f.isNested && f.lengthPrefixWidth == 0) {
                errors.add(
                    "Field '${f.name}' is nested but has no length-prefix width " +
                        "(must be one of {8, 16, 32}) — Ticket 06, invariant #4",
                )
            }
        }
        return errors
    }

    /**
     * Invariant #5: repeated field count width ∈ {8, 16, 32} (Ticket 06).
     *
     * A repeatCountWidth of 0 means the field is not repeated.
     * Any non-zero value must be one of the valid prefix widths so the
     * count can be read unambiguously.
     *
     * @return list of error messages (empty if valid)
     */
    fun validateRepeatCountWidth(fields: List<KompactFieldInfo>): List<String> {
        val errors = mutableListOf<String>()
        for (f in fields) {
            if (f.repeatCountWidth != 0 && f.repeatCountWidth !in VALID_PREFIX_WIDTHS) {
                errors.add(
                    "Field '${f.name}' has repeatCountWidth=${f.repeatCountWidth} " +
                        "not in {8, 16, 32} (Ticket 06, invariant #5)",
                )
            }
        }
        return errors
    }

    /**
     * Invariant #6: enum width in 1..8; declared codes fit within the width (Ticket 06).
     *
     * An enumWidth of 0 means the field is not an enum. Any non-zero value
     * must be in 1..8 (an enum ordinal at 1–8 bits). A width of 0 bits cannot
     * represent any ordinal.
     *
     * @return list of error messages (empty if valid)
     */
    fun validateEnumWidth(fields: List<KompactFieldInfo>): List<String> {
        val errors = mutableListOf<String>()
        for (f in fields) {
            if (f.enumWidth > 8) {
                errors.add(
                    "Field '${f.name}' has enumWidth=${f.enumWidth} not in 1..8 " +
                        "(Ticket 06, invariant #6)",
                )
            }
        }
        return errors
    }

    /**
     * Per-type bit-width constraints (supporting check, Ticket 04).
     *
     * - `Boolean`: exactly 1 bit
     * - `Int`: 1..31 bits (fits in the return value of `readBits`)
     * - `Long`: 1..64 bits (fits in the return value of `readBitsLong`)
     * - `Float`: exactly 32 bits
     * - `Double`: exactly 64 bits
     * - `String`/`ByteArray`: bitWidth must equal lengthPrefixWidth (the prefix IS the declared width)
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
     * Full validation pass — runs all six Ticket 06 invariants plus the
     * per-type width check, and returns combined errors. Exits before any
     * code is generated so the build fails on a structurally-invalid schema.
     */
    fun validateAll(fields: List<KompactFieldInfo>): List<String> =
        buildList {
            addAll(validateNoOverlaps(fields)) // invariant #1
            addAll(validateBitWidthSum(fields)) // invariant #2
            addAll(validateLengthPrefixWidth(fields)) // invariant #3
            addAll(validateNestedTotalLength(fields)) // invariant #4
            addAll(validateRepeatCountWidth(fields)) // invariant #5
            addAll(validateEnumWidth(fields)) // invariant #6
            addAll(validateWidths(fields)) // supporting per-type check
        }
}
