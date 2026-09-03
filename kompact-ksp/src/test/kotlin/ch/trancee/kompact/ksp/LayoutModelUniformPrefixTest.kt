package ch.trancee.kompact.ksp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spec: .scratch/kompact-spec/issues/09-versioning-schema-evolution.md
 *
 * Forward-compat skip requires a **uniform** length-prefix width
 * across the stream so an older reader can skip an unknown trailing
 * length-delimited field by reading the uniform-width prefix + payload.
 * `LayoutModel` must hard-error if a single struct mixes 8-bit and
 * 16-bit prefixes.
 */
class LayoutModelUniformPrefixTest {

    @Test
    fun uniform_width_single_field_ok() {
        val fields = listOf(
            KompactFieldInfo("a", 0, 8, lengthPrefixBits = 16, enumWidth = 0, signed = false),
        )
        val r = validate(fields)
        assertTrue(r, "single 16-bit prefix should be valid")
    }

    @Test
    fun uniform_width_multiple_8bit_ok() {
        val fields = listOf(
            KompactFieldInfo("a", 0, 8, lengthPrefixBits = 8, enumWidth = 0, signed = false),
            KompactFieldInfo("b", 8, 8, lengthPrefixBits = 8, enumWidth = 0, signed = false),
        )
        val r = validate(fields)
        assertTrue(r, "multiple 8-bit prefixes should be valid (uniform)")
    }

    @Test
    fun mixed_widths_rejected() {
        val fields = listOf(
            KompactFieldInfo("a", 0, 8, lengthPrefixBits = 8, enumWidth = 0, signed = false),
            KompactFieldInfo("b", 8, 8, lengthPrefixBits = 16, enumWidth = 0, signed = false),
        )
        val r = validate(fields)
        assertFalse(r, "8-bit + 16-bit prefixes in same struct should be rejected")
    }

    @Test
    fun no_length_prefixed_fields_uniformity_vacuous() {
        val fields = listOf(
            KompactFieldInfo("a", 0, 8, lengthPrefixBits = 0, enumWidth = 0, signed = false),
            KompactFieldInfo("b", 8, 8, lengthPrefixBits = 0, enumWidth = 0, signed = false),
        )
        val r = validate(fields)
        assertTrue(r, "no length-prefixed fields means uniformity is vacuously satisfied")
    }

    private fun validate(fields: List<KompactFieldInfo>): Boolean {
        // Reuse LayoutModel.validate via a non-throwing capture.
        val model = LayoutModel("test", fields)
        return model.uniformPrefixWidthSatisfied()
    }
}
