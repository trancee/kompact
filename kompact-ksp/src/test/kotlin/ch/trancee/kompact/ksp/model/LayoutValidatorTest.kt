package ch.trancee.kompact.ksp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutValidatorTest {
    private fun field(
        name: String,
        bitOffset: Int,
        bitWidth: Int,
        kotlinType: String = "Int",
        signed: Boolean = false,
        lengthPrefixWidth: Int = 8,
        isNested: Boolean = false,
        repeatCountWidth: Int = 8,
        enumWidth: Int = 0,
    ) = KompactFieldInfo(
        name = name,
        kotlinType = kotlinType,
        bitOffset = bitOffset,
        bitWidth = bitWidth,
        signed = signed,
        lengthPrefixWidth = lengthPrefixWidth,
        isNested = isNested,
        repeatCountWidth = repeatCountWidth,
        enumWidth = enumWidth,
        defaultValue = "",
    )

    // --- Invariant #1: No overlaps ---

    @Test
    fun `valid layout passes all checks`() {
        val fields =
            listOf(
                field("battery", 0, 4),
                field("speed", 4, 10),
                field("fault", 14, 1, "Boolean"),
            )
        val errors = LayoutValidator.validateAll(fields)
        assertTrue(errors.isEmpty(), "Expected no errors, got: $errors")
    }

    @Test
    fun `overlapping fields are rejected by validateNoOverlaps`() {
        val fields =
            listOf(
                field("a", 0, 8),
                field("b", 4, 8), // overlaps: [4..12) vs [0..8)
            )
        val errors = LayoutValidator.validateNoOverlaps(fields)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("overlaps"), "Expected overlap message, got: ${errors[0]}")
    }

    @Test
    fun `non-overlapping adjacent fields pass overlap check`() {
        val fields =
            listOf(
                field("a", 0, 4),
                field("b", 4, 4), // [0..4) and [4..8) — adjacent, no overlap
            )
        val errors = LayoutValidator.validateNoOverlaps(fields)
        assertTrue(errors.isEmpty(), "Expected no overlap errors, got: $errors")
    }

    @Test
    fun `non-overlapping fields with reversed order pass overlap check`() {
        // rangeA = [8..<16), rangeB = [0..<8) — this.first < other.right → 8 < 8 → false
        // This covers the short-circuit of the && in overlaps()
        val fields =
            listOf(
                field("a", 8, 8),
                field("b", 0, 8),
            )
        val errors = LayoutValidator.validateNoOverlaps(fields)
        assertTrue(errors.isEmpty(), "Expected no overlap errors for reversed non-overlapping fields, got: $errors")
    }

    @Test
    fun `overlapping fields are rejected by validateAll`() {
        val fields =
            listOf(
                field("a", 0, 8),
                field("b", 4, 8), // overlaps
            )
        val errors = LayoutValidator.validateAll(fields)
        assertTrue(errors.any { it.contains("overlap") }, "Expected overlap error in validateAll, got: $errors")
    }

    // --- Invariant #2: Bit-width sum ≤ declared width ---

    @Test
    fun `gap between fields is allowed by validateBitWidthSum`() {
        val fields =
            listOf(
                field("a", 0, 4),
                field("b", 8, 4), // gap at bits 4-7 — allowed per Ticket 06 invariant #2
            )
        val errors = LayoutValidator.validateBitWidthSum(fields)
        assertTrue(errors.isEmpty(), "Gap bits should be allowed, got: $errors")
    }

    @Test
    fun `layout with reserved gap bits passes validateBitWidthSum`() {
        // VehicleTelemetry-style: 4 + 10 + 1 = 15 bits used, bits 15 reserved
        // totalBits = max(endBit) = 15, declaredWidth = 15 — all fields fit
        val fields =
            listOf(
                field("battery", 0, 4),
                field("speed", 4, 10),
                field("fault", 14, 1, "Boolean"),
            )
        val errors = LayoutValidator.validateBitWidthSum(fields)
        assertTrue(errors.isEmpty(), "Gap bits should be allowed, got: $errors")
    }

    @Test
    fun `fields with negative bitOffset are rejected`() {
        val fields = listOf(field("a", -1, 4))
        val errors = LayoutValidator.validateBitWidthSum(fields)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("bitOffset"), "Expected negative offset error, got: ${errors[0]}")
    }

    @Test
    fun `negative bitOffset is rejected`() {
        val fields = listOf(field("a", -1, 4))
        val errors = LayoutValidator.validateBitWidthSum(fields)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("bitOffset"), "Expected negative offset error, got: ${errors[0]}")
    }

    @Test
    fun `gap fields pass validateAll`() {
        val fields =
            listOf(
                field("a", 0, 4, "Int"),
                field("b", 16, 4, "Int"), // 12-bit gap — allowed
            )
        val errors = LayoutValidator.validateAll(fields)
        assertTrue(errors.isEmpty(), "Gaps should be allowed in validateAll, got: $errors")
    }

    // --- Per-type width validation (supporting check) ---

    @Test
    fun `Int with bitWidth 32 is rejected`() {
        val fields = listOf(field("a", 0, 32, "Int"))
        val errors = LayoutValidator.validateWidths(fields)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Int"), "Expected Int type error, got: ${errors[0]}")
    }

    @Test
    fun `Int with bitWidth 31 is accepted`() {
        val fields = listOf(field("a", 0, 31, "Int"))
        val errors = LayoutValidator.validateWidths(fields)
        assertTrue(errors.isEmpty(), "Expected no width errors, got: $errors")
    }

    @Test
    fun `Long with bitWidth 32 is accepted`() {
        val fields = listOf(field("a", 0, 32, "Long"))
        val errors = LayoutValidator.validateWidths(fields)
        assertTrue(errors.isEmpty(), "Expected no width errors, got: $errors")
    }

    @Test
    fun `Long with bitWidth 64 is accepted`() {
        val fields = listOf(field("a", 0, 64, "Long"))
        val errors = LayoutValidator.validateWidths(fields)
        assertTrue(errors.isEmpty(), "Expected no width errors, got: $errors")
    }

    @Test
    fun `Boolean with bitWidth 1 is accepted`() {
        val fields = listOf(field("flag", 0, 1, "Boolean"))
        val errors = LayoutValidator.validateWidths(fields)
        assertTrue(errors.isEmpty(), "Expected no width errors, got: $errors")
    }

    @Test
    fun `Boolean with bitWidth 2 is rejected`() {
        val fields = listOf(field("flag", 0, 2, "Boolean"))
        val errors = LayoutValidator.validateWidths(fields)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Boolean"), "Expected Boolean type error, got: ${errors[0]}")
    }

    @Test
    fun `Float with bitWidth 32 is accepted`() {
        val fields = listOf(field("temp", 0, 32, "Float"))
        val errors = LayoutValidator.validateWidths(fields)
        assertTrue(errors.isEmpty(), "Expected no width errors, got: $errors")
    }

    @Test
    fun `Double with bitWidth 64 is accepted`() {
        val fields = listOf(field("pressure", 0, 64, "Double"))
        val errors = LayoutValidator.validateWidths(fields)
        assertTrue(errors.isEmpty(), "Expected no width errors, got: $errors")
    }

    @Test
    fun `Float with bitWidth 16 is rejected`() {
        val fields = listOf(field("temp", 0, 16, "Float"))
        val errors = LayoutValidator.validateWidths(fields)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Float"), "Expected Float type error, got: ${errors[0]}")
    }

    @Test
    fun `String with mismatched lengthPrefixWidth is rejected`() {
        val fields =
            listOf(
                KompactFieldInfo(
                    name = "name",
                    kotlinType = "String",
                    bitOffset = 0,
                    bitWidth = 4,
                    signed = false,
                    lengthPrefixWidth = 8,
                    isNested = false,
                    repeatCountWidth = 8,
                    enumWidth = 0,
                    defaultValue = "",
                ),
            )
        val errors = LayoutValidator.validateWidths(fields)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("String"), "Expected String width error, got: ${errors[0]}")
    }

    @Test
    fun `ByteArray with mismatched lengthPrefixWidth is rejected`() {
        val fields =
            listOf(
                KompactFieldInfo(
                    name = "data",
                    kotlinType = "ByteArray",
                    bitOffset = 0,
                    bitWidth = 16,
                    signed = false,
                    lengthPrefixWidth = 8,
                    isNested = false,
                    repeatCountWidth = 8,
                    enumWidth = 0,
                    defaultValue = "",
                ),
            )
        val errors = LayoutValidator.validateWidths(fields)
        assertEquals(1, errors.size)
        assertTrue(
            errors[0].contains("ByteArray"),
            "Expected ByteArray width error, got: ${errors[0]}",
        )
    }

    @Test
    fun `unknown Kotlin type passes width validation`() {
        val fields = listOf(field("custom", 0, 16, "MyCustomType"))
        val errors = LayoutValidator.validateWidths(fields)
        assertTrue(errors.isEmpty(), "Unknown type should pass best-effort, got: $errors")
    }

    @Test
    fun `Double with incorrect bitWidth is rejected`() {
        val fields = listOf(field("pressure", 0, 32, "Double"))
        val errors = LayoutValidator.validateWidths(fields)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Double"), "Expected Double type error, got: ${errors[0]}")
    }

    @Test
    fun `Long with bitWidth 0 is rejected`() {
        val fields = listOf(field("bad", 0, 0, "Long"))
        val errors = LayoutValidator.validateWidths(fields)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Long"), "Expected Long type error, got: ${errors[0]}")
    }

    @Test
    fun `Long with bitWidth 65 is rejected`() {
        val fields = listOf(field("huge", 0, 65, "Long"))
        val errors = LayoutValidator.validateWidths(fields)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Long"), "Expected Long type error, got: ${errors[0]}")
    }

    @Test
    fun `Int with bitWidth 0 is rejected`() {
        val fields = listOf(field("zero", 0, 0, "Int"))
        val errors = LayoutValidator.validateWidths(fields)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Int"), "Expected Int type error, got: ${errors[0]}")
    }

    @Test
    fun `Int with bitWidth 1 is accepted`() {
        val fields = listOf(field("one", 0, 1, "Int"))
        val errors = LayoutValidator.validateWidths(fields)
        assertTrue(errors.isEmpty(), "Expected no width errors, got: $errors")
    }

    @Test
    fun `Long with bitWidth 1 is accepted`() {
        val fields = listOf(field("one", 0, 1, "Long"))
        val errors = LayoutValidator.validateWidths(fields)
        assertTrue(errors.isEmpty(), "Expected no width errors, got: $errors")
    }

    // --- Invariant #3: Length-prefix width ∈ {8, 16, 32} ---

    @Test
    fun `valid lengthPrefixWidth values are accepted`() {
        val widths = listOf(0, 8, 16, 32)
        widths.forEach { w ->
            val fields = listOf(field("a", 0, 4, lengthPrefixWidth = w))
            val errors = LayoutValidator.validateLengthPrefixWidth(fields)
            assertTrue(errors.isEmpty(), "lengthPrefixWidth=$w should be valid, got: $errors")
        }
    }

    @Test
    fun `invalid lengthPrefixWidth is rejected`() {
        val fields = listOf(field("a", 0, 4, lengthPrefixWidth = 7))
        val errors = LayoutValidator.validateLengthPrefixWidth(fields)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("lengthPrefixWidth"), "Expected lengthPrefixWidth error, got: ${errors[0]}")
    }

    @Test
    fun `lengthPrefixWidth of 64 is rejected`() {
        val fields = listOf(field("a", 0, 4, lengthPrefixWidth = 64))
        val errors = LayoutValidator.validateLengthPrefixWidth(fields)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("lengthPrefixWidth"), "Expected lengthPrefixWidth error, got: ${errors[0]}")
    }

    // --- Invariant #4: Nested total-length ≤ declared capacity ---

    @Test
    fun `nested field with valid lengthPrefixWidth passes`() {
        val fields = listOf(field("a", 0, 4, isNested = true, lengthPrefixWidth = 16))
        val errors = LayoutValidator.validateNestedTotalLength(fields)
        assertTrue(errors.isEmpty(), "Nested field with valid prefix width should pass, got: $errors")
    }

    @Test
    fun `nested field without lengthPrefixWidth is rejected`() {
        val fields = listOf(field("a", 0, 4, isNested = true, lengthPrefixWidth = 0))
        val errors = LayoutValidator.validateNestedTotalLength(fields)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("nested"), "Expected nested error, got: ${errors[0]}")
    }

    // --- Invariant #5: Repeated count width ∈ {8, 16, 32} ---

    @Test
    fun `valid repeatCountWidth values are accepted`() {
        val widths = listOf(0, 8, 16, 32)
        widths.forEach { w ->
            val fields = listOf(field("a", 0, 4, repeatCountWidth = w))
            val errors = LayoutValidator.validateRepeatCountWidth(fields)
            assertTrue(errors.isEmpty(), "repeatCountWidth=$w should be valid, got: $errors")
        }
    }

    @Test
    fun `invalid repeatCountWidth is rejected`() {
        val fields = listOf(field("a", 0, 4, repeatCountWidth = 3))
        val errors = LayoutValidator.validateRepeatCountWidth(fields)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("repeatCountWidth"), "Expected repeatCountWidth error, got: ${errors[0]}")
    }

    // --- Invariant #6: Enum width ≥ ordinal bit-width; codes fit ---

    @Test
    fun `valid enumWidth values are accepted`() {
        val widths = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8)
        widths.forEach { w ->
            val fields = listOf(field("a", 0, w, enumWidth = w))
            val errors = LayoutValidator.validateEnumWidth(fields)
            assertTrue(errors.isEmpty(), "enumWidth=$w should be valid, got: $errors")
        }
    }

    @Test
    fun `enumWidth above 8 is rejected`() {
        val fields = listOf(field("a", 0, 1, enumWidth = 9))
        val errors = LayoutValidator.validateEnumWidth(fields)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("enumWidth"), "Expected enumWidth error, got: ${errors[0]}")
    }

    @Test
    fun `enumWidth of 0 is accepted (not an enum)`() {
        val fields = listOf(field("a", 0, 4, enumWidth = 0))
        val errors = LayoutValidator.validateEnumWidth(fields)
        assertTrue(errors.isEmpty(), "enumWidth=0 (non-enum) should pass, got: $errors")
    }

    // --- Combined validation ---

    @Test
    fun `combined errors from all validators`() {
        val fields =
            listOf(
                field("a", 0, 64, "Int", lengthPrefixWidth = 7), // width too large for Int + invalid lengthPrefixWidth
                field("b", 4, 4), // overlaps with a
                field("c", 16, 4, enumWidth = 9), // invalid enumWidth
            )
        val errors = LayoutValidator.validateAll(fields)
        // validateWidths: 1 (Int bitWidth 64)
        // validateNoOverlaps: 2 (b overlaps a, c overlaps a)
        // validateLengthPrefixWidth: 1 (lengthPrefixWidth=7)
        // validateEnumWidth: 1 (enumWidth=9)
        // validateBitWidthSum, validateNestedTotalLength, validateRepeatCountWidth: 0
        assertEquals(5, errors.size)
    }

    @Test
    fun `empty field list passes all checks`() {
        val errors = LayoutValidator.validateAll(emptyList())
        assertTrue(errors.isEmpty(), "Expected no errors for empty layout, got: $errors")
    }
}
