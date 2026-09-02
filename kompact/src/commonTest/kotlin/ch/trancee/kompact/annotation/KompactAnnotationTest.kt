package ch.trancee.kompact.annotation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spec: .scratch/kompact-spec/issues/02-generation-strategy.md
 *
 * The annotations themselves are pure source-retention metadata; the
 * only observable contract is that they exist with the documented
 * parameters. The KSP processor reads them via reflection; this test
 * asserts the parameter surface.
 */
class KompactAnnotationTest {

    @Test
    fun kompactField_defaults_are_safe() {
        // No length prefix, no enum, unsigned, plain bit field.
        val ann = KompactField(bitOffset = 0, bitWidth = 8)
        assertEquals(0, ann.lengthPrefixBits)
        assertEquals(0, ann.enumWidth)
        assertFalse(ann.signed)
    }

    @Test
    fun kompactField_signed_is_recorded() {
        val ann = KompactField(bitOffset = 0, bitWidth = 8, signed = true)
        assertTrue(ann.signed)
    }

    @Test
    fun kompactField_length_prefix_and_enum_width_are_recorded() {
        val ann = KompactField(
            bitOffset = 16,
            bitWidth = 4,
            lengthPrefixBits = 16,
            enumWidth = 4,
        )
        assertEquals(16, ann.lengthPrefixBits)
        assertEquals(4, ann.enumWidth)
    }
}
