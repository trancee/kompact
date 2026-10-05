package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.ModelSpec
import ch.trancee.kompact.ksp.model.scalarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertTrue(output.contains("flag: Boolean = false"))
        assertTrue(output.contains("signedInt: Int = 0"))
        assertTrue(output.contains("public var flag: Boolean = false"))
        assertTrue(output.contains("public var signedInt: Int = 0"))
        assertTrue(output.contains("public var unsignedInt: Int = 0"))
        assertTrue(output.contains("public var signedLong: Long = 0L"))
        assertTrue(output.contains("public var fullLong: Long = 0L"))
        assertTrue(output.contains("public var unsignedLong: Long = 0L"))
        assertTrue(output.contains("public var ratio: Float = 0.0f"))
        assertTrue(output.contains("public var measure: Double = 0.0"))
        assertTrue(output.contains("cursor.ensureAvailable(248)"))
        assertTrue(output.contains("(cursor.valueBits.toInt() shl 27) shr 27"))
        assertTrue(output.contains("(cursor.valueBits shl 59) shr 59"))
        assertTrue(output.contains("Float.fromBits(cursor.valueBits.toInt())"))
        assertTrue(output.contains("Double.fromBits(cursor.valueBits)"))
        assertTrue(output.contains("cursor.writeZeros(1)"))
        assertTrue(output.contains("fullLongDecoded = cursor.valueBits"))
        assertTrue(output.contains("cursor.writeZeros(6)"))
        assertEquals(2, Regex("cursor\\.writeZeros\\(1\\)").findAll(output).count())
    }

    @Test
    fun decodeCommitsSignedValuesAndEncodeValidatesBeforeWritingReservedBits() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "BoundaryHolder",
                fields =
                    listOf(
                        field("signedInt", 1, 31, "Int", signed = true),
                        field("unsignedInt", 40, 8, "Int"),
                        field("signedLong", 64, 63, "Long", signed = true),
                    ),
            )

        val output = ValueClassGenerator.generateCommonEncoder(spec)

        assertTrue(output.contains("signedIntDecoded = (cursor.valueBits.toInt() shl 1) shr 1"))
        assertTrue(output.contains("signedLongDecoded = (cursor.valueBits shl 1) shr 1"))
        assertTrue(output.contains("this.signedInt = signedIntDecoded"))
        assertTrue(output.contains("this.unsignedInt = unsignedIntDecoded"))
        assertTrue(output.contains("this.signedLong = signedLongDecoded"))
        assertTrue(output.contains("cursor.validateSigned(31, signedIntInput.toLong())"))
        assertTrue(output.contains("cursor.validateUnsigned(8, unsignedIntInput.toLong())"))
        assertTrue(output.contains("cursor.validateSigned(63, signedLongInput)"))
        assertTrue(output.contains("cursor.skipBits(1)"))
        assertTrue(output.contains("cursor.skipBits(8)"))
        assertTrue(output.contains("cursor.skipBits(16)"))
        assertTrue(output.contains("cursor.skipBits(1)"))
        assertTrue(output.contains("cursor.writeZeros(1)"))
        assertTrue(output.contains("cursor.writeZeros(8)"))
        assertTrue(output.contains("cursor.writeZeros(16)"))
        assertTrue(output.contains("cursor.writeZeros(1)"))
        val firstWrite = output.indexOf("cursor.writeZeros(1)")
        assertTrue(firstWrite > output.indexOf("cursor.validateSigned(31, signedIntInput.toLong())"))
        assertTrue(firstWrite > output.indexOf("cursor.validateUnsigned(8, unsignedIntInput.toLong())"))
        assertTrue(firstWrite > output.indexOf("cursor.validateSigned(63, signedLongInput)"))
    }

    @Test
    fun generatedHolderConsumesOnlyRealGapsAndTheExactDeclaredFrame() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "SparseBoundaryHolder",
                fields =
                    listOf(
                        field("first", 0, 2, "Int", signed = true),
                        field("adjacent", 2, 3, "Int"),
                        field("flag", 6, 1, "Boolean"),
                    ),
            )

        val output = ValueClassGenerator.generateCommonEncoder(spec)
        val decoded = output.substringAfter("fun SparseBoundaryHolder.decodeInto")
            .substringBefore("fun SparseBoundaryHolder.encodeFrom")
        val encoded = output.substringAfter("fun SparseBoundaryHolder.encodeFrom")

        assertTrue(decoded.contains("cursor.ensureAvailable(8)"))
        assertTrue(decoded.contains("cursor.skipBits(1)"))
        assertEquals(2, Regex("cursor\\.skipBits\\(1\\)").findAll(decoded).count())
        assertFalse(decoded.contains("cursor.skipBits(0)"))
        assertTrue(encoded.contains("cursor.ensureAvailable(8)"))
        assertTrue(encoded.contains("cursor.writeZeros(1)"))
        assertEquals(2, Regex("cursor\\.writeZeros\\(1\\)").findAll(encoded).count())
        assertFalse(encoded.contains("cursor.writeZeros(0)"))
        assertTrue(encoded.indexOf("cursor.validateSigned(2, firstInput.toLong())") < encoded.indexOf("cursor.writeZeros(1)"))
        assertTrue(encoded.contains("cursor.validateUnsigned(3, adjacentInput.toLong())"))
        assertTrue(encoded.contains("cursor.writeBitsUnchecked(2, firstInput.toLong())"))
        assertTrue(encoded.contains("cursor.writeBitsUnchecked(3, adjacentInput.toLong())"))
        assertTrue(encoded.contains("cursor.writeBitsUnchecked(1, if (flagInput) 1L else 0L)"))
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
