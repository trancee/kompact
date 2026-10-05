package ch.trancee.kompact.runtime

import io.github.anschnapp.mutflow.MutFlow
import kotlin.test.Test

class KompactCursorBoundaryMutationTest {
    private val commonTests = KompactCursorBoundaryTest()
    private val bitTests = KompactRuntimeBitPrimitivesTest()

    @Test
    fun delegates_15() = MutFlow.underTest { commonTests.signedAndUnsignedValuesRespectTheSixtyThreeBitBoundary() }

    @Test
    fun delegates_14() = MutFlow.underTest { bitTests.readsAndWritesMatchIndependentBitOrderAcrossOffsetsAndWidths() }

    @Test
    fun delegates_16() = MutFlow.underTest { commonTests.resetAcceptsValidSubrangeAndClearsPriorDiagnostics() }

    @Test
    fun delegates_18() = MutFlow.underTest { commonTests.signedLongOverloadRejectsNegativeUnsignedValuesAtFullWidth() }

    @Test
    fun delegates_19() = MutFlow.underTest { commonTests.resetByteRangeAcceptsZeroStartAndKeepsOriginalNegativeBoundDiagnostic() }

    @Test
    fun delegates_20() = MutFlow.underTest { commonTests.resetDiagnosticForEqualStartAndPositionUsesTheOutOfBufferEnd() }

    @Test
    fun delegates_21() = MutFlow.underTest { commonTests.resetKeepsNegativePositionDiagnosticWhenStartIsZero() }

    @Test
    fun delegates_17() = MutFlow.underTest { commonTests.resetByteRangeOverflowReportsByteBoundWithoutReplacingRegion() }

    @Test
    fun delegates_01() = MutFlow.underTest { commonTests.resetReportsEachInvalidBoundAndRetainsThePreviousRegion() }

    @Test
    fun delegates_02() = MutFlow.underTest { commonTests.resetByteRangeRejectsInvalidOrderingAndBufferBounds() }

    @Test
    fun delegates_09() = MutFlow.underTest { commonTests.resetByteRangeAcceptsEmptyRegionAtExactBufferEnd() }

    @Test
    fun delegates_03() = MutFlow.underTest { commonTests.checkedReadsAndSkipsRejectInvalidWidthsAndBoundsWithoutAdvancing() }

    @Test
    fun delegates_04() = MutFlow.underTest { commonTests.rawWritesRejectInvalidWidthsAndPreserveDataOnBoundsFailure() }

    @Test
    fun delegates_11() = MutFlow.underTest { commonTests.successfulWriteClearsPriorCursorDiagnostics() }

    @Test
    fun delegates_05() = MutFlow.underTest { commonTests.signedValidationCoversNarrowAndFullWidthValues() }

    @Test
    fun delegates_06() = MutFlow.underTest { commonTests.signedAndUnsignedWritesValidateBeforeChangingTheBuffer() }

    @Test
    fun delegates_12() = MutFlow.underTest { commonTests.unsignedLongValidationDistinguishesWidth62FromWidth63AndAcceptsZero() }

    @Test
    fun delegates_13() = MutFlow.underTest { commonTests.fullWidthUnsignedLongValidationAcceptsEveryBitPattern() }

    @Test
    fun delegates_07() = MutFlow.underTest { commonTests.unsignedValidationCoversLongAndUnsignedLongDomains() }

    @Test
    fun delegates_08() = MutFlow.underTest { commonTests.zeroWritesHandleEmptyLargeAndInsufficientRegions() }
}
