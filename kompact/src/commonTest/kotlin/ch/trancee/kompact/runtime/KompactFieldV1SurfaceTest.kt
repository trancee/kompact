@file:OptIn(ch.trancee.kompact.annotations.KompactPreview::class)

package ch.trancee.kompact.runtime

import ch.trancee.kompact.annotations.KompactField
import kotlin.test.Test

/**
 * `@KompactField` annotation surface — compile-time existence check.
 *
 * `@KompactField` is SOURCE-retained, so reflection can't see its members at
 * runtime; the contract is that this file *compiles*, which pins every v1
 * member name. The runtime assert only confirms the class loads.
 */
@Suppress("unused", "unused_parameter")
private class KompactFieldV1Probe(
    @KompactField(
        bitOffset = 0,
        bitWidth = 4,
        signed = false,
        lengthPrefixWidth = 16,
        repeatCountWidth = 8,
        order = -1,
    )
    val tag: Int,
    // Scalar default: only bitOffset/bitWidth declared.
    @KompactField(bitOffset = 4, bitWidth = 1)
    val valid: Boolean,
)

class KompactFieldV1SurfaceTest {
    @Test
    @Suppress("UNUSED_EXPRESSION")
    fun v1AnnotationMembersExist_compileTimeContract() {
        // If any of bitOffset/bitWidth/signed/lengthPrefixWidth/repeatCountWidth/
        // order is absent on @KompactField, the probe above fails to compile —
        // this assert only confirms the class loads. Nesting is inferred from
        // the field type (ADR-0011), so there is no isNested member.
        KompactFieldV1Probe::class
    }
}
