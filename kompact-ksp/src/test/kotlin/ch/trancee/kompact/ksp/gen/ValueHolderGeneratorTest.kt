package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.ModelSpec
import ch.trancee.kompact.ksp.model.scalarType
import kotlin.test.Test
import kotlin.test.assertTrue

class ValueHolderGeneratorTest {
    @Test
    fun commonEncoderCoversScalarKindsSignedWidthsAndReservedBits() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "CursorCoverage",
                fields =
                    listOf(
                        field("flag", 0, 1, "Boolean"),
                        field("signedInt", 2, 5, "Int", signed = true),
                        field("unsignedInt", 7, 5, "Int"),
                        field("signedLong", 12, 5, "Long", signed = true),
                        field("fullLong", 17, 64, "Long", signed = true),
                        field("unsignedLong", 81, 64, "Long"),
                        field("ratio", 145, 32, "Float"),
                        field("measure", 178, 64, "Double"),
                    ),
            )

        val output = ValueClassGenerator.generateCommonEncoder(spec)

        assertTrue(output.contains("class CursorCoverageHolder"))
        assertTrue(output.contains("(cursor.valueBits.toInt() shl 27) shr 27"))
        assertTrue(output.contains("(cursor.valueBits shl 59) shr 59"))
        assertTrue(output.contains("Float.fromBits(cursor.valueBits.toInt())"))
        assertTrue(output.contains("Double.fromBits(cursor.valueBits)"))
        assertTrue(output.contains("cursor.writeZeros(1)"))
    }

    private fun field(
        name: String,
        bitOffset: Int,
        bitWidth: Int,
        kotlinType: String,
        signed: Boolean = false,
    ) = KompactFieldInfo(
        name = name,
        type = scalarType(kotlinType),
        order = null,
        bitOffset = bitOffset,
        bitWidth = bitWidth,
        signed = signed,
        lengthPrefixWidth = 8,
        isNested = false,
        repeatCountWidth = 8,
        enumWidth = 0,
        defaultValue = "",
    )
}
