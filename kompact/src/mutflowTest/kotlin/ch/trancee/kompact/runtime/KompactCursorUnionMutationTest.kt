package ch.trancee.kompact.runtime

// One MutFlow class scope containing the union of the existing adapter behaviors.
import io.github.anschnapp.mutflow.MutFlow
import kotlin.test.Test

class KompactCursorUnionMutationTest {
    private val cursorCommonTests = KompactCursorTest()
    private val cursorRepeatTests = KompactCursorRepeatsTest()
    private val boundaryCommonTests = KompactCursorBoundaryTest()
    private val boundaryBitTests = KompactRuntimeBitPrimitivesTest()
    private val byteRangeCommonTests = KompactCursorByteRangeTest()
    private val byteRangeBoundaryTests = KompactCursorByteRangeBoundaryTest()

    @Test
    fun byteRangeConstructorInitiallyBorrowsTheWholeBuffer() = MutFlow.underTest { cursorCommonTests.byteRangeConstructorInitiallyBorrowsTheWholeBuffer() }

    @Test
    fun resetRejectsOutOfBoundsWithoutChangingTheBoundRegion() = MutFlow.underTest { cursorCommonTests.resetRejectsOutOfBoundsWithoutChangingTheBoundRegion() }

    @Test
    fun readBitsStopsAtTheBoundAndPreservesTheLastValueOnFailure() = MutFlow.underTest { cursorCommonTests.readBitsStopsAtTheBoundAndPreservesTheLastValueOnFailure() }

    @Test
    fun uncheckedWritePreservesNeighboringBitsAndRejectsOutOfBoundsAtomically() = MutFlow.underTest { cursorCommonTests.uncheckedWritePreservesNeighboringBitsAndRejectsOutOfBoundsAtomically() }

    @Test
    fun checkedWritesRejectValuesOutsideTheirDeclaredWidthWithoutMutation() = MutFlow.underTest { cursorCommonTests.checkedWritesRejectValuesOutsideTheirDeclaredWidthWithoutMutation() }

    @Test
    fun nestedReadUsesASeparateBoundedCursorAndDoesNotEnterTheFollowingField() = MutFlow.underTest { cursorCommonTests.nestedReadUsesASeparateBoundedCursorAndDoesNotEnterTheFollowingField() }

    @Test
    fun malformedNestedReadLeavesParentAndChildBindingsUnchanged() = MutFlow.underTest { cursorCommonTests.malformedNestedReadLeavesParentAndChildBindingsUnchanged() }

    @Test
    fun nestedWriteReservesKnownLengthAndBoundsTheChildCursor() = MutFlow.underTest { cursorCommonTests.nestedWriteReservesKnownLengthAndBoundsTheChildCursor() }

    @Test
    fun byteRangeReadAndWriteBorrowAndAppendWithoutAResultWrapper() = MutFlow.underTest { cursorCommonTests.byteRangeReadAndWriteBorrowAndAppendWithoutAResultWrapper() }

    @Test
    fun borrowedUtf8RangeCanBeCopiedIntoCallerOwnedStorage() = MutFlow.underTest { cursorCommonTests.borrowedUtf8RangeCanBeCopiedIntoCallerOwnedStorage() }

    @Test
    fun utf8ByteRangeValidatesBeforeChangingCursorOrRange() = MutFlow.underTest { cursorCommonTests.utf8ByteRangeValidatesBeforeChangingCursorOrRange() }

    @Test
    fun variableRepeatReportsInsufficientWorkspaceWithoutAdvancing() = MutFlow.underTest { cursorCommonTests.variableRepeatReportsInsufficientWorkspaceWithoutAdvancing() }

    @Test
    fun variableRepeatIndexesPayloadsInCallerOwnedWorkspace() = MutFlow.underTest { cursorCommonTests.variableRepeatIndexesPayloadsInCallerOwnedWorkspace() }

    @Test
    fun variableRepeatRequiresOneCheckpointPer64Elements() = MutFlow.underTest { cursorCommonTests.variableRepeatRequiresOneCheckpointPer64Elements() }

    @Test
    fun fixedRepeatIndexesElementsWithoutAllocatingAnIndexArray() = MutFlow.underTest { cursorCommonTests.fixedRepeatIndexesElementsWithoutAllocatingAnIndexArray() }

    @Test
    fun fixedRepeatRejectsInsufficientWorkspaceWithoutChangingWorkspaceOrCursor() = MutFlow.underTest { cursorCommonTests.fixedRepeatRejectsInsufficientWorkspaceWithoutChangingWorkspaceOrCursor() }

    @Test
    fun variableRepeatRejects65ElementsWhenOnlyOneCheckpointIsAvailable() = MutFlow.underTest { cursorCommonTests.variableRepeatRejects65ElementsWhenOnlyOneCheckpointIsAvailable() }

    @Test
    fun truncatedVariableRepeatDoesNotAdvanceOrReplaceWorkspace() = MutFlow.underTest { cursorCommonTests.truncatedVariableRepeatDoesNotAdvanceOrReplaceWorkspace() }

    @Test
    fun variableRepeatLookupAtIndex63ReturnsItsPayload() = MutFlow.underTest { cursorRepeatTests.variableRepeatLookupAtIndex63ReturnsItsPayload() }

    @Test
    fun variableRepeatLookupAtIndex64ReturnsItsPayload() = MutFlow.underTest { cursorRepeatTests.variableRepeatLookupAtIndex64ReturnsItsPayload() }

    @Test
    fun variableRepeatLookupAtIndex65ReturnsItsPayload() = MutFlow.underTest { cursorRepeatTests.variableRepeatLookupAtIndex65ReturnsItsPayload() }

    @Test
    fun fixedRepeatLookupAtIndex63ReturnsItsPayload() = MutFlow.underTest { cursorRepeatTests.fixedRepeatLookupAtIndex63ReturnsItsPayload() }

    @Test
    fun fixedRepeatLookupAtIndex64ReturnsItsPayload() = MutFlow.underTest { cursorRepeatTests.fixedRepeatLookupAtIndex64ReturnsItsPayload() }

    @Test
    fun fixedRepeatLookupAtIndex65ReturnsItsPayload() = MutFlow.underTest { cursorRepeatTests.fixedRepeatLookupAtIndex65ReturnsItsPayload() }

    @Test
    fun outOfRangeRepeatLookupRetainsElementBindingAndReportsDiagnostics() = MutFlow.underTest { cursorRepeatTests.outOfRangeRepeatLookupRetainsElementBindingAndReportsDiagnostics() }

    @Test
    fun variableRepeatSkipRejectsInvalidWidthsAlignmentAndMalformedPrefixes() = MutFlow.underTest { cursorRepeatTests.variableRepeatSkipRejectsInvalidWidthsAlignmentAndMalformedPrefixes() }

    @Test
    fun fixedRepeatSkipChecksCountWidthCapacityAndPayloadBounds() = MutFlow.underTest { cursorRepeatTests.fixedRepeatSkipChecksCountWidthCapacityAndPayloadBounds() }

    @Test
    fun variableRepeatReadIsFailureAtomicAndIndexesEmptyAndNonemptyRepeats() = MutFlow.underTest { cursorRepeatTests.variableRepeatReadIsFailureAtomicAndIndexesEmptyAndNonemptyRepeats() }

    @Test
    fun fixedRepeatReadValidatesArgumentsCapacityAndElementBounds() = MutFlow.underTest { cursorRepeatTests.fixedRepeatReadValidatesArgumentsCapacityAndElementBounds() }

    @Test
    fun repeatedElementReadRejectsWrongBindingAndOutOfRangeIndexes() = MutFlow.underTest { cursorRepeatTests.repeatedElementReadRejectsWrongBindingAndOutOfRangeIndexes() }

    @Test
    fun indexedElementReadChecksFixedAndVariableOffsetsAgainstWorkspaceBounds() = MutFlow.underTest { cursorRepeatTests.indexedElementReadChecksFixedAndVariableOffsetsAgainstWorkspaceBounds() }

    @Test
    fun fixedRepeatIndexingStoresSparseCheckpointsAcrossBlocks() = MutFlow.underTest { cursorRepeatTests.fixedRepeatIndexingStoresSparseCheckpointsAcrossBlocks() }

    @Test
    fun variableRepeatAcceptsEmptyElementEndingExactlyAtTheRegionBoundary() = MutFlow.underTest { cursorRepeatTests.variableRepeatAcceptsEmptyElementEndingExactlyAtTheRegionBoundary() }

    @Test
    fun repeatCountsAndLengthsAtIntMaximumKeepTheirSpecificFailureStatus() = MutFlow.underTest { cursorRepeatTests.repeatCountsAndLengthsAtIntMaximumKeepTheirSpecificFailureStatus() }

    @Test
    fun variableRepeatCheckpointCapacityHandlesZeroAndExactSixtyFourCount() = MutFlow.underTest { cursorRepeatTests.variableRepeatCheckpointCapacityHandlesZeroAndExactSixtyFourCount() }

    @Test
    fun fixedRepeatCheckpointLoopAcceptsAnExactSixtyFourElementCapacity() = MutFlow.underTest { cursorRepeatTests.fixedRepeatCheckpointLoopAcceptsAnExactSixtyFourElementCapacity() }

    @Test
    fun repeatArgumentFailuresReportTheFirstInvalidWidth() = MutFlow.underTest { cursorRepeatTests.repeatArgumentFailuresReportTheFirstInvalidWidth() }

    @Test
    fun corruptedCheckpointAtTheEndReportsTheNextMissingPrefixOffset() = MutFlow.underTest { cursorRepeatTests.corruptedCheckpointAtTheEndReportsTheNextMissingPrefixOffset() }

    @Test
    fun corruptedCheckpointElementEndingAtTheRegionBoundaryReportsTheNextPrefix() = MutFlow.underTest { cursorRepeatTests.corruptedCheckpointElementEndingAtTheRegionBoundaryReportsTheNextPrefix() }

    @Test
    fun repeatedElementLookupAtIntMaxSkippedLengthKeepsBadPrefixDiagnostics() = MutFlow.underTest { cursorRepeatTests.repeatedElementLookupAtIntMaxSkippedLengthKeepsBadPrefixDiagnostics() }

    @Test
    fun repeatedElementLookupAtIntMaxPayloadLengthKeepsBadPrefixDiagnostics() = MutFlow.underTest { cursorRepeatTests.repeatedElementLookupAtIntMaxPayloadLengthKeepsBadPrefixDiagnostics() }

    @Test
    fun signedAndUnsignedValuesRespectTheSixtyThreeBitBoundary() = MutFlow.underTest { boundaryCommonTests.signedAndUnsignedValuesRespectTheSixtyThreeBitBoundary() }

    @Test
    fun readsAndWritesMatchIndependentBitOrderAcrossOffsetsAndWidths() = MutFlow.underTest { boundaryBitTests.readsAndWritesMatchIndependentBitOrderAcrossOffsetsAndWidths() }

    @Test
    fun resetAcceptsValidSubrangeAndClearsPriorDiagnostics() = MutFlow.underTest { boundaryCommonTests.resetAcceptsValidSubrangeAndClearsPriorDiagnostics() }

    @Test
    fun signedLongOverloadRejectsNegativeUnsignedValuesAtFullWidth() = MutFlow.underTest { boundaryCommonTests.signedLongOverloadRejectsNegativeUnsignedValuesAtFullWidth() }

    @Test
    fun resetByteRangeAcceptsZeroStartAndKeepsOriginalNegativeBoundDiagnostic() = MutFlow.underTest { boundaryCommonTests.resetByteRangeAcceptsZeroStartAndKeepsOriginalNegativeBoundDiagnostic() }

    @Test
    fun resetDiagnosticForEqualStartAndPositionUsesTheOutOfBufferEnd() = MutFlow.underTest { boundaryCommonTests.resetDiagnosticForEqualStartAndPositionUsesTheOutOfBufferEnd() }

    @Test
    fun resetKeepsNegativePositionDiagnosticWhenStartIsZero() = MutFlow.underTest { boundaryCommonTests.resetKeepsNegativePositionDiagnosticWhenStartIsZero() }

    @Test
    fun resetByteRangeOverflowReportsByteBoundWithoutReplacingRegion() = MutFlow.underTest { boundaryCommonTests.resetByteRangeOverflowReportsByteBoundWithoutReplacingRegion() }

    @Test
    fun resetReportsEachInvalidBoundAndRetainsThePreviousRegion() = MutFlow.underTest { boundaryCommonTests.resetReportsEachInvalidBoundAndRetainsThePreviousRegion() }

    @Test
    fun resetByteRangeRejectsInvalidOrderingAndBufferBounds() = MutFlow.underTest { boundaryCommonTests.resetByteRangeRejectsInvalidOrderingAndBufferBounds() }

    @Test
    fun resetByteRangeAcceptsEmptyRegionAtExactBufferEnd() = MutFlow.underTest { boundaryCommonTests.resetByteRangeAcceptsEmptyRegionAtExactBufferEnd() }

    @Test
    fun checkedReadsAndSkipsRejectInvalidWidthsAndBoundsWithoutAdvancing() = MutFlow.underTest { boundaryCommonTests.checkedReadsAndSkipsRejectInvalidWidthsAndBoundsWithoutAdvancing() }

    @Test
    fun rawWritesRejectInvalidWidthsAndPreserveDataOnBoundsFailure() = MutFlow.underTest { boundaryCommonTests.rawWritesRejectInvalidWidthsAndPreserveDataOnBoundsFailure() }

    @Test
    fun successfulWriteClearsPriorCursorDiagnostics() = MutFlow.underTest { boundaryCommonTests.successfulWriteClearsPriorCursorDiagnostics() }

    @Test
    fun signedValidationCoversNarrowAndFullWidthValues() = MutFlow.underTest { boundaryCommonTests.signedValidationCoversNarrowAndFullWidthValues() }

    @Test
    fun signedAndUnsignedWritesValidateBeforeChangingTheBuffer() = MutFlow.underTest { boundaryCommonTests.signedAndUnsignedWritesValidateBeforeChangingTheBuffer() }

    @Test
    fun unsignedLongValidationDistinguishesWidth62FromWidth63AndAcceptsZero() = MutFlow.underTest { boundaryCommonTests.unsignedLongValidationDistinguishesWidth62FromWidth63AndAcceptsZero() }

    @Test
    fun fullWidthUnsignedLongValidationAcceptsEveryBitPattern() = MutFlow.underTest { boundaryCommonTests.fullWidthUnsignedLongValidationAcceptsEveryBitPattern() }

    @Test
    fun unsignedValidationCoversLongAndUnsignedLongDomains() = MutFlow.underTest { boundaryCommonTests.unsignedValidationCoversLongAndUnsignedLongDomains() }

    @Test
    fun zeroWritesHandleEmptyLargeAndInsufficientRegions() = MutFlow.underTest { boundaryCommonTests.zeroWritesHandleEmptyLargeAndInsufficientRegions() }

    @Test
    fun emptyPayloadFitsExactlyAfterItsLengthPrefix() = MutFlow.underTest { byteRangeBoundaryTests.emptyPayloadFitsExactlyAfterItsLengthPrefix() }

    @Test
    fun maximumEightBitPayloadFitsExactlyAtTheBufferBoundary() = MutFlow.underTest { byteRangeBoundaryTests.maximumEightBitPayloadFitsExactlyAtTheBufferBoundary() }

    @Test
    fun copyToRejectsPositiveOffsetOverrunWithoutChangingDestination() = MutFlow.underTest { byteRangeBoundaryTests.copyToRejectsPositiveOffsetOverrunWithoutChangingDestination() }

    @Test
    fun rawRangeCaptureAndWritePreserveNonzeroByteOffsets() = MutFlow.underTest { byteRangeCommonTests.rawRangeCaptureAndWritePreserveNonzeroByteOffsets() }

    @Test
    fun byteRangePreflightRejectsSixteenBitLengthAbovePrefixMaximum() = MutFlow.underTest { byteRangeCommonTests.byteRangePreflightRejectsSixteenBitLengthAbovePrefixMaximum() }

    @Test
    fun nestedReadAndWriteAcceptMaximumEightBitPayloadAtExactCapacity() = MutFlow.underTest { byteRangeCommonTests.nestedReadAndWriteAcceptMaximumEightBitPayloadAtExactCapacity() }

    @Test
    fun captureDiagnosticsUseTheSelectedNonzeroByteRegion() = MutFlow.underTest { byteRangeCommonTests.captureDiagnosticsUseTheSelectedNonzeroByteRegion() }

    @Test
    fun prefixedWriteAllowsAdjacentRangesAndRejectsOverlapAtNonzeroCursorPosition() = MutFlow.underTest { byteRangeCommonTests.prefixedWriteAllowsAdjacentRangesAndRejectsOverlapAtNonzeroCursorPosition() }

    @Test
    fun intMaxLengthPrefixesKeepBadLengthDiagnosticsAndBindingsUnchanged() = MutFlow.underTest { byteRangeCommonTests.intMaxLengthPrefixesKeepBadLengthDiagnosticsAndBindingsUnchanged() }

    @Test
    fun byteRangeResetAndCopyRejectInvalidBoundsWithoutChangingTheView() = MutFlow.underTest { byteRangeCommonTests.byteRangeResetAndCopyRejectInvalidBoundsWithoutChangingTheView() }

    @Test
    fun byteRangeResetAcceptsEmptyRangeAtExactBufferEnd() = MutFlow.underTest { byteRangeCommonTests.byteRangeResetAcceptsEmptyRangeAtExactBufferEnd() }

    @Test
    fun byteRangeCopyAcceptsExactCapacity() = MutFlow.underTest { byteRangeCommonTests.byteRangeCopyAcceptsExactCapacity() }

    @Test
    fun emptyByteRangeCopiesToAnEmptyDestination() = MutFlow.underTest { byteRangeCommonTests.emptyByteRangeCopiesToAnEmptyDestination() }

    @Test
    fun rawRangePreflightAndCopyRejectAlignmentAliasingAndBounds() = MutFlow.underTest { byteRangeCommonTests.rawRangePreflightAndCopyRejectAlignmentAliasingAndBounds() }

    @Test
    fun regionCaptureRequiresByteAlignmentAndOptionallyValidUtf8() = MutFlow.underTest { byteRangeCommonTests.regionCaptureRequiresByteAlignmentAndOptionallyValidUtf8() }

    @Test
    fun nestedReadsRejectInvalidCursorsWidthsAlignmentAndTruncation() = MutFlow.underTest { byteRangeCommonTests.nestedReadsRejectInvalidCursorsWidthsAlignmentAndTruncation() }

    @Test
    fun nestedWritesPreflightLengthAlignmentAndCapacityBeforeMutation() = MutFlow.underTest { byteRangeCommonTests.nestedWritesPreflightLengthAlignmentAndCapacityBeforeMutation() }

    @Test
    fun prefixedReadsValidateWidthAlignmentLengthUtf8AndRangeAtomicity() = MutFlow.underTest { byteRangeCommonTests.prefixedReadsValidateWidthAlignmentLengthUtf8AndRangeAtomicity() }

    @Test
    fun byteRangeSkipAndWritePreflightRejectMalformedArgumentsAtomically() = MutFlow.underTest { byteRangeCommonTests.byteRangeSkipAndWritePreflightRejectMalformedArgumentsAtomically() }

    @Test
    fun byteRangeWritesAndCursorDiagnosticsPreserveTheCheckedContract() = MutFlow.underTest { byteRangeCommonTests.byteRangeWritesAndCursorDiagnosticsPreserveTheCheckedContract() }
}
