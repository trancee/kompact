package ch.trancee.kompact.runtime

import kotlin.test.Test

/**
 * Ticket 04/06 v1 annotation surface — compile-time existence check.
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
        lengthPrefixWidth = 16,
        isNested = true,
        repeatCountWidth = 8,
        enumWidth = 4,
        defaultValue = "0",
        isVersionField = true,
    )
    val tag: Int,

    // Scalar default: only bitOffset/bitWidth declared.
    @KompactField(bitOffset = 4, bitWidth = 1)
    val valid: Boolean,
)

class KompactFieldV1SurfaceTest {

    @Test
    fun v1AnnotationMembersExist_compileTimeContract() {
        // If any of lengthPrefixWidth/isNested/repeatCountWidth/enumWidth/
        // defaultValue/isVersionField is absent on @KompactField, the probe
        // above fails to compile — this assert only confirms the class loads.
        KompactFieldV1Probe::class
    }
}
