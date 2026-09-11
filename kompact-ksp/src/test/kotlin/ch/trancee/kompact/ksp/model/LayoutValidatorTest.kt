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
    ) = KompactFieldInfo(
        name = name,
        kotlinType = kotlinType,
        bitOffset = bitOffset,
        bitWidth = bitWidth,
        signed = signed,
        lengthPrefixWidth = 8,
        isNested = false,
        repeatCountWidth = 8,
        enumWidth = 0,
        defaultValue = "",
        isVersionField = false,
    )

    @Test
    fun `valid dense layout passes all checks`() {
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
    fun `overlapping fields are rejected`() {
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
    fun `gap between fields is rejected by dense packing check`() {
        val fields =
            listOf(
                field("a", 0, 4),
                field("b", 8, 4), // gap at bits 4-7
            )
        val errors = LayoutValidator.validateDensePacking(fields)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("dense packing"), "Expected dense-packing message, got: ${errors[0]}")
    }

    @Test
    fun `adjacent fields pass dense packing`() {
        val fields =
            listOf(
                field("a", 0, 4),
                field("b", 4, 4),
                field("c", 8, 1, "Boolean"),
                field("d", 9, 7),
            )
        val errors = LayoutValidator.validateDensePacking(fields)
        assertTrue(errors.isEmpty(), "Expected no dense-packing errors, got: $errors")
    }

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
    fun `combined errors from all three validators`() {
        val fields =
            listOf(
                field("a", 0, 64, "Int"), // width too large for Int (1 width error)
                field("b", 4, 4), // overlaps with a (1 overlap error) + gap after a (1 packing error)
                field("c", 16, 4), // overlaps with a (1 overlap error) + gap after b (1 packing error)
            )
        val errors = LayoutValidator.validateAll(fields)
        // 5: 1 width + 2 overlaps + 2 gaps
        assertEquals(5, errors.size)
    }

    @Test
    fun `empty field list passes all checks`() {
        val errors = LayoutValidator.validateAll(emptyList())
        assertTrue(errors.isEmpty(), "Expected no errors for empty layout, got: $errors")
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
                    isVersionField = false,
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
                    isVersionField = false,
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
}
