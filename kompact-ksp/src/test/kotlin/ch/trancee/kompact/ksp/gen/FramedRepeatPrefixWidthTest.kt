package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.KompactFieldType
import ch.trancee.kompact.ksp.model.KompactScalarKind
import ch.trancee.kompact.ksp.model.ModelSpec
import kotlin.test.Test
import kotlin.test.assertTrue

class FramedRepeatPrefixWidthTest {
    @Test
    fun repeatPrefixWidthsAreUsedByPreflightAndDecodeOperations() {
        val fixed =
            field(
                "numbers",
                KompactFieldType.Repeated(KompactFieldType.Scalar(KompactScalarKind.INT)),
                0,
                5,
            ).copy(repeatCountWidth = 16)
        val variable =
            field("texts", KompactFieldType.Repeated(KompactFieldType.StringType), 1, 0)
                .copy(repeatCountWidth = 32, lengthPrefixWidth = 16)
        val spec =
            ModelSpec(
                packageName = "example",
                className = "RepeatWidths",
                fields = listOf(fixed, variable),
                framed = true,
            )

        val decoder = FramedHolderGenerator.buildBorrowedDecodeInto(spec).toString()

        assertTrue(decoder.contains("probeCursor.skipFixedRepeat(16, 5, this.numbersWorkspace.capacity)"))
        assertTrue(decoder.contains("cursor.readFixedRepeat(16, 5, this.numbersWorkspace)"))
        assertTrue(decoder.contains("probeCursor.skipVariableRepeat(32, 16, this.textsWorkspace.capacity)"))
        assertTrue(decoder.contains("cursor.readVariableRepeat(32, 16, this.textsWorkspace)"))
    }

    private fun field(
        name: String,
        type: KompactFieldType,
        order: Int,
        bitWidth: Int,
    ) = KompactFieldInfo(
        name = name,
        type = type,
        order = order,
        bitOffset = 0,
        bitWidth = bitWidth,
        signed = false,
        lengthPrefixWidth = 8,
        isNested = false,
        repeatCountWidth = 8,
        enumWidth = 0,
        defaultValue = "",
    )
}
