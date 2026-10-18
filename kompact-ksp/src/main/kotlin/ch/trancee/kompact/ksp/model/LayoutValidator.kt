package ch.trancee.kompact.ksp.model

/**
 * Compile-time layout validation.
 *
 * The processor proves these invariants at compile time so the generated
 * getters can skip the runtime `fits()` bounds check — the bit offsets
 * are static and guaranteed in-bounds for a buffer of the declared size.
 *
 * All `validate*` functions return a list of human-readable error messages.
 * An empty list means the layout is valid.
 *
 * Invariant matrix:
 * 1. Bit-offset overlaps within a struct — [validateNoOverlaps]
 * 2. Per-struct bit-width sum ≤ declared width — [validateBitWidthSum]
 * 3. Length-prefix field width ∈ {8,16,32} — [validateLengthPrefixWidth]
 * 4. Repeated count width ∈ {8,16,32} — [validateRepeatCountWidth]
 *
 * Additionally, [validateWidths] enforces per-type bit-width constraints
 * (Boolean=1, Int 1..31, Long 1..64, Float=32, Double=64) and rejects nested
 * model types outside framed schemas.
 */
internal object LayoutValidator {
    /** Valid fixed-width length-prefix / count-prefix bit widths (invariants #3/#5). */
    internal val VALID_PREFIX_WIDTHS: Set<Int> = setOf(8, 16, 32)

    /**
     * Invariant #1: no two fields overlap within a struct.
     *
     * @return list of error messages (empty if valid)
     */
    fun validateNoOverlaps(fields: List<KompactFieldInfo>): List<String> {
        val errors = mutableListOf<String>()
        for (i in fields.indices) {
            for (j in (i + 1)..<fields.size) {
                val a = fields[i]
                val b = fields[j]
                // Half-open [bitOffset, endBit) intervals intersect iff each starts before the other ends.
                if (a.bitOffset < b.endBit && b.bitOffset < a.endBit) {
                    errors.add(
                        "Field '${a.name}' [${a.bitOffset}..<${a.endBit}] overlaps " +
                            "field '${b.name}' [${b.bitOffset}..<${b.endBit}]",
                    )
                }
            }
        }
        return errors
    }

    /**
     * Invariant #2: per-struct bit-width sum ≤ declared width.
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
     * Invariant #3: length-prefix field width ∈ {8, 16, 32}.
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
                        "not in {8, 16, 32} (invariant #3)",
                )
            }
        }
        return errors
    }

    /**
     * Invariant #4: repeated field count width ∈ {8, 16, 32}.
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
                        "not in {8, 16, 32} (invariant #4)",
                )
            }
        }
        return errors
    }

    /**
     * Per-type bit-width constraints (supporting check).
     *
     * - `Boolean`: exactly 1 bit
     * - `Int`: 1..31 bits for packed models; framed models also permit 32
     * - `Long`: 1..64 bits (fits in the return value of `readBitsLong`)
     * - `Float`: exactly 32 bits
     * - `Double`: exactly 64 bits
     * - `String`/`ByteArray`: bitWidth must equal lengthPrefixWidth (the prefix IS the declared width)
     *
     * @return list of error messages (empty if valid)
     */
    fun validateWidths(
        fields: List<KompactFieldInfo>,
        framed: Boolean = false,
    ): List<String> {
        val errors = mutableListOf<String>()
        for (f in fields) {
            val valid =
                when (val type = f.type) {
                    is KompactFieldType.Scalar -> {
                        when (type.kind) {
                            KompactScalarKind.BOOLEAN -> f.bitWidth == 1
                            KompactScalarKind.INT -> f.bitWidth in 1..(if (framed) 32 else 31)
                            KompactScalarKind.LONG -> f.bitWidth in 1..64
                            KompactScalarKind.FLOAT -> f.bitWidth == 32
                            KompactScalarKind.DOUBLE -> f.bitWidth == 64
                        }
                    }

                    KompactFieldType.StringType, KompactFieldType.Blob -> {
                        if (framed) f.bitWidth == 0 else f.bitWidth == f.lengthPrefixWidth
                    }

                    is KompactFieldType.Nested -> {
                        framed && f.bitWidth == 0
                    }

                    is KompactFieldType.Repeated -> {
                        !framed || isValidFramedWidth(type.elementType, f.bitWidth)
                    }

                    is KompactFieldType.Unsupported -> {
                        true
                    } // diagnosed by generation for legacy models
                }
            if (!valid) {
                errors.add(
                    "Field '${f.name}' has type ${f.type.displayName} with bitWidth=${f.bitWidth} " +
                        "which is not valid for that type",
                )
            }
        }
        return errors
    }

    /**
     * Full validation pass — runs the four layout invariants plus the
     * per-type width check, and returns combined errors. Exits before any
     * code is generated so the build fails on a structurally-invalid schema.
     */
    fun validateAll(
        fields: List<KompactFieldInfo>,
        framed: Boolean = false,
        mutable: Boolean = false,
    ): List<String> {
        if (framed) return FramedLayoutValidator.validate(fields, mutable)
        return buildList {
            addAll(validateNoOverlaps(fields)) // invariant #1
            addAll(validateBitWidthSum(fields)) // invariant #2
            addAll(validateLengthPrefixWidth(fields)) // invariant #3
            addAll(validateRepeatCountWidth(fields)) // invariant #4
            addAll(validateWidths(fields)) // supporting per-type check
        }
    }

    private fun isValidFramedWidth(
        type: KompactFieldType,
        width: Int,
    ): Boolean =
        when (type) {
            is KompactFieldType.Scalar -> {
                when (type.kind) {
                    KompactScalarKind.BOOLEAN -> width == 1
                    KompactScalarKind.INT -> width in 1..32
                    KompactScalarKind.LONG -> width in 1..64
                    KompactScalarKind.FLOAT -> width == 32
                    KompactScalarKind.DOUBLE -> width == 64
                }
            }

            KompactFieldType.StringType, KompactFieldType.Blob, is KompactFieldType.Nested -> {
                width == 0
            }

            is KompactFieldType.Repeated, is KompactFieldType.Unsupported -> {
                false
            }
        }
}
