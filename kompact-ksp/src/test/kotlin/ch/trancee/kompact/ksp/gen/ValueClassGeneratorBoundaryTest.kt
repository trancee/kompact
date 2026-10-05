package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.ModelSpec
import ch.trancee.kompact.ksp.model.scalarType
import kotlin.test.Test
import kotlin.test.assertTrue

class ValueClassGeneratorBoundaryTest {
    @Test
    fun signed31BitIntGetterPreservesItsSignBit() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "SignedIntBoundaryModel",
                fields = listOf(field("value", 31, "Int")),
            )

        val output = ValueClassGenerator.generateJvmActual(spec)

        assertTrue(
            output.contains("(KompactRuntime.readBits(raw, 0, 31) shl 1) shr 1"),
            "The signed Int getter must sign-extend its declared 31-bit field, got:\n$output",
        )
    }

    @Test
    fun signed63BitLongGetterPreservesItsSignBit() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "SignedLongBoundaryModel",
                fields = listOf(field("value", 63, "Long")),
            )

        val output = ValueClassGenerator.generateJvmActual(spec)

        assertTrue(
            output.contains("(KompactRuntime.readBitsLong(raw, 0, 63) shl 1) shr 1"),
            "The signed Long getter must sign-extend its declared 63-bit field, got:\n$output",
        )
    }

    private fun field(
        name: String,
        bitWidth: Int,
        kotlinType: String,
    ): KompactFieldInfo =
        KompactFieldInfo(
            name = name,
            type = scalarType(kotlinType),
            order = null,
            bitOffset = 0,
            bitWidth = bitWidth,
            signed = true,
            lengthPrefixWidth = 8,
            isNested = false,
            repeatCountWidth = 8,
            enumWidth = 0,
            defaultValue = "",
        )
}
