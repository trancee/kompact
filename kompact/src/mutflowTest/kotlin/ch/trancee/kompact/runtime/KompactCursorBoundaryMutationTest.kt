package ch.trancee.kompact.runtime

import io.github.anschnapp.mutflow.MutFlow
import kotlin.test.Test

class KompactCursorBoundaryMutationTest {
    private val commonTests = KompactCursorBoundaryTest()
    private val bitTests = KompactRuntimeBitPrimitivesTest()

    @Test
    fun signedAndUnsignedValuesRespectTheSixtyThreeBitBoundary() = MutFlow.underTest { commonTests.signedAndUnsignedValuesRespectTheSixtyThreeBitBoundary() }

    @Test
    fun readsAndWritesMatchIndependentBitOrderAcrossOffsetsAndWidths() = MutFlow.underTest { bitTests.readsAndWritesMatchIndependentBitOrderAcrossOffsetsAndWidths() }

    @Test
    fun resetAcceptsValidSubrangeAndClearsPriorDiagnostics() = MutFlow.underTest { commonTests.resetAcceptsValidSubrangeAndClearsPriorDiagnostics() }

    @Test
    fun signedLongOverloadRejectsNegativeUnsignedValuesAtFullWidth() = MutFlow.underTest { commonTests.signedLongOverloadRejectsNegativeUnsignedValuesAtFullWidth() }

    @Test
    fun resetByteRangeAcceptsZeroStartAndKeepsOriginalNegativeBoundDiagnostic() = MutFlow.underTest { commonTests.resetByteRangeAcceptsZeroStartAndKeepsOriginalNegativeBoundDiagnostic() }

    @Test
    fun resetDiagnosticForEqualStartAndPositionUsesTheOutOfBufferEnd() = MutFlow.underTest { commonTests.resetDiagnosticForEqualStartAndPositionUsesTheOutOfBufferEnd() }

    @Test
    fun resetKeepsNegativePositionDiagnosticWhenStartIsZero() = MutFlow.underTest { commonTests.resetKeepsNegativePositionDiagnosticWhenStartIsZero() }

    @Test
    fun resetByteRangeOverflowReportsByteBoundWithoutReplacingRegion() = MutFlow.underTest { commonTests.resetByteRangeOverflowReportsByteBoundWithoutReplacingRegion() }

    @Test
    fun resetReportsEachInvalidBoundAndRetainsThePreviousRegion() = MutFlow.underTest { commonTests.resetReportsEachInvalidBoundAndRetainsThePreviousRegion() }

    @Test
    fun resetByteRangeRejectsInvalidOrderingAndBufferBounds() = MutFlow.underTest { commonTests.resetByteRangeRejectsInvalidOrderingAndBufferBounds() }

    @Test
    fun resetByteRangeAcceptsEmptyRegionAtExactBufferEnd() = MutFlow.underTest { commonTests.resetByteRangeAcceptsEmptyRegionAtExactBufferEnd() }

    @Test
    fun checkedReadsAndSkipsRejectInvalidWidthsAndBoundsWithoutAdvancing() = MutFlow.underTest { commonTests.checkedReadsAndSkipsRejectInvalidWidthsAndBoundsWithoutAdvancing() }

    @Test
    fun rawWritesRejectInvalidWidthsAndPreserveDataOnBoundsFailure() = MutFlow.underTest { commonTests.rawWritesRejectInvalidWidthsAndPreserveDataOnBoundsFailure() }

    @Test
    fun successfulWriteClearsPriorCursorDiagnostics() = MutFlow.underTest { commonTests.successfulWriteClearsPriorCursorDiagnostics() }

    @Test
    fun signedValidationCoversNarrowAndFullWidthValues() = MutFlow.underTest { commonTests.signedValidationCoversNarrowAndFullWidthValues() }

    @Test
    fun signedAndUnsignedWritesValidateBeforeChangingTheBuffer() = MutFlow.underTest { commonTests.signedAndUnsignedWritesValidateBeforeChangingTheBuffer() }

    @Test
    fun unsignedLongValidationDistinguishesWidth62FromWidth63AndAcceptsZero() = MutFlow.underTest { commonTests.unsignedLongValidationDistinguishesWidth62FromWidth63AndAcceptsZero() }

    @Test
    fun fullWidthUnsignedLongValidationAcceptsEveryBitPattern() = MutFlow.underTest { commonTests.fullWidthUnsignedLongValidationAcceptsEveryBitPattern() }

    @Test
    fun unsignedValidationCoversLongAndUnsignedLongDomains() = MutFlow.underTest { commonTests.unsignedValidationCoversLongAndUnsignedLongDomains() }

    @Test
    fun zeroWritesHandleEmptyLargeAndInsufficientRegions() = MutFlow.underTest { commonTests.zeroWritesHandleEmptyLargeAndInsufficientRegions() }
}
