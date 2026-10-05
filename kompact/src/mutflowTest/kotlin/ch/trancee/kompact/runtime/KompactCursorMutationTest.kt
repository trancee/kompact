package ch.trancee.kompact.runtime

import io.github.anschnapp.mutflow.MutFlow
import kotlin.test.Test

class KompactCursorMutationTest {
    private val commonTests = KompactCursorTest()
    private val repeatTests = KompactCursorRepeatsTest()

    @Test
    fun byteRangeConstructorInitiallyBorrowsTheWholeBuffer() = MutFlow.underTest { commonTests.byteRangeConstructorInitiallyBorrowsTheWholeBuffer() }

    @Test
    fun resetRejectsOutOfBoundsWithoutChangingTheBoundRegion() = MutFlow.underTest { commonTests.resetRejectsOutOfBoundsWithoutChangingTheBoundRegion() }

    @Test
    fun readBitsStopsAtTheBoundAndPreservesTheLastValueOnFailure() = MutFlow.underTest { commonTests.readBitsStopsAtTheBoundAndPreservesTheLastValueOnFailure() }

    @Test
    fun uncheckedWritePreservesNeighboringBitsAndRejectsOutOfBoundsAtomically() = MutFlow.underTest { commonTests.uncheckedWritePreservesNeighboringBitsAndRejectsOutOfBoundsAtomically() }

    @Test
    fun checkedWritesRejectValuesOutsideTheirDeclaredWidthWithoutMutation() = MutFlow.underTest { commonTests.checkedWritesRejectValuesOutsideTheirDeclaredWidthWithoutMutation() }

    @Test
    fun nestedReadUsesASeparateBoundedCursorAndDoesNotEnterTheFollowingField() = MutFlow.underTest { commonTests.nestedReadUsesASeparateBoundedCursorAndDoesNotEnterTheFollowingField() }

    @Test
    fun malformedNestedReadLeavesParentAndChildBindingsUnchanged() = MutFlow.underTest { commonTests.malformedNestedReadLeavesParentAndChildBindingsUnchanged() }

    @Test
    fun nestedWriteReservesKnownLengthAndBoundsTheChildCursor() = MutFlow.underTest { commonTests.nestedWriteReservesKnownLengthAndBoundsTheChildCursor() }

    @Test
    fun byteRangeReadAndWriteBorrowAndAppendWithoutAResultWrapper() = MutFlow.underTest { commonTests.byteRangeReadAndWriteBorrowAndAppendWithoutAResultWrapper() }

    @Test
    fun borrowedUtf8RangeCanBeCopiedIntoCallerOwnedStorage() = MutFlow.underTest { commonTests.borrowedUtf8RangeCanBeCopiedIntoCallerOwnedStorage() }

    @Test
    fun utf8ByteRangeValidatesBeforeChangingCursorOrRange() = MutFlow.underTest { commonTests.utf8ByteRangeValidatesBeforeChangingCursorOrRange() }

    @Test
    fun variableRepeatReportsInsufficientWorkspaceWithoutAdvancing() = MutFlow.underTest { commonTests.variableRepeatReportsInsufficientWorkspaceWithoutAdvancing() }

    @Test
    fun variableRepeatIndexesPayloadsInCallerOwnedWorkspace() = MutFlow.underTest { commonTests.variableRepeatIndexesPayloadsInCallerOwnedWorkspace() }

    @Test
    fun variableRepeatRequiresOneCheckpointPer64Elements() = MutFlow.underTest { commonTests.variableRepeatRequiresOneCheckpointPer64Elements() }

    @Test
    fun fixedRepeatIndexesElementsWithoutAllocatingAnIndexArray() = MutFlow.underTest { commonTests.fixedRepeatIndexesElementsWithoutAllocatingAnIndexArray() }

    @Test
    fun fixedRepeatRejectsInsufficientWorkspaceWithoutChangingWorkspaceOrCursor() = MutFlow.underTest { commonTests.fixedRepeatRejectsInsufficientWorkspaceWithoutChangingWorkspaceOrCursor() }

    @Test
    fun variableRepeatRejects65ElementsWhenOnlyOneCheckpointIsAvailable() = MutFlow.underTest { commonTests.variableRepeatRejects65ElementsWhenOnlyOneCheckpointIsAvailable() }

    @Test
    fun truncatedVariableRepeatDoesNotAdvanceOrReplaceWorkspace() = MutFlow.underTest { commonTests.truncatedVariableRepeatDoesNotAdvanceOrReplaceWorkspace() }

    @Test
    fun variableRepeatLookupAtIndex63ReturnsItsPayload() = MutFlow.underTest { repeatTests.variableRepeatLookupAtIndex63ReturnsItsPayload() }

    @Test
    fun variableRepeatLookupAtIndex64ReturnsItsPayload() = MutFlow.underTest { repeatTests.variableRepeatLookupAtIndex64ReturnsItsPayload() }

    @Test
    fun variableRepeatLookupAtIndex65ReturnsItsPayload() = MutFlow.underTest { repeatTests.variableRepeatLookupAtIndex65ReturnsItsPayload() }

    @Test
    fun fixedRepeatLookupAtIndex63ReturnsItsPayload() = MutFlow.underTest { repeatTests.fixedRepeatLookupAtIndex63ReturnsItsPayload() }

    @Test
    fun fixedRepeatLookupAtIndex64ReturnsItsPayload() = MutFlow.underTest { repeatTests.fixedRepeatLookupAtIndex64ReturnsItsPayload() }

    @Test
    fun fixedRepeatLookupAtIndex65ReturnsItsPayload() = MutFlow.underTest { repeatTests.fixedRepeatLookupAtIndex65ReturnsItsPayload() }

    @Test
    fun outOfRangeRepeatLookupRetainsElementBindingAndReportsDiagnostics() = MutFlow.underTest { repeatTests.outOfRangeRepeatLookupRetainsElementBindingAndReportsDiagnostics() }
    @Test
    fun variableRepeatSkipRejectsInvalidWidthsAlignmentAndMalformedPrefixes() = MutFlow.underTest { repeatTests.variableRepeatSkipRejectsInvalidWidthsAlignmentAndMalformedPrefixes() }

    @Test
    fun fixedRepeatSkipChecksCountWidthCapacityAndPayloadBounds() = MutFlow.underTest { repeatTests.fixedRepeatSkipChecksCountWidthCapacityAndPayloadBounds() }

    @Test
    fun variableRepeatReadIsFailureAtomicAndIndexesEmptyAndNonemptyRepeats() = MutFlow.underTest { repeatTests.variableRepeatReadIsFailureAtomicAndIndexesEmptyAndNonemptyRepeats() }

    @Test
    fun fixedRepeatReadValidatesArgumentsCapacityAndElementBounds() = MutFlow.underTest { repeatTests.fixedRepeatReadValidatesArgumentsCapacityAndElementBounds() }

    @Test
    fun repeatedElementReadRejectsWrongBindingAndOutOfRangeIndexes() = MutFlow.underTest { repeatTests.repeatedElementReadRejectsWrongBindingAndOutOfRangeIndexes() }

    @Test
    fun indexedElementReadChecksFixedAndVariableOffsetsAgainstWorkspaceBounds() = MutFlow.underTest { repeatTests.indexedElementReadChecksFixedAndVariableOffsetsAgainstWorkspaceBounds() }

    @Test
    fun fixedRepeatIndexingStoresSparseCheckpointsAcrossBlocks() = MutFlow.underTest { repeatTests.fixedRepeatIndexingStoresSparseCheckpointsAcrossBlocks() }

    @Test
    fun variableRepeatAcceptsEmptyElementEndingExactlyAtTheRegionBoundary() = MutFlow.underTest { repeatTests.variableRepeatAcceptsEmptyElementEndingExactlyAtTheRegionBoundary() }

    @Test
    fun repeatCountsAndLengthsAtIntMaximumKeepTheirSpecificFailureStatus() = MutFlow.underTest { repeatTests.repeatCountsAndLengthsAtIntMaximumKeepTheirSpecificFailureStatus() }

    @Test
    fun variableRepeatCheckpointCapacityHandlesZeroAndExactSixtyFourCount() = MutFlow.underTest { repeatTests.variableRepeatCheckpointCapacityHandlesZeroAndExactSixtyFourCount() }

    @Test
    fun fixedRepeatCheckpointLoopAcceptsAnExactSixtyFourElementCapacity() = MutFlow.underTest { repeatTests.fixedRepeatCheckpointLoopAcceptsAnExactSixtyFourElementCapacity() }

    @Test
    fun repeatArgumentFailuresReportTheFirstInvalidWidth() = MutFlow.underTest { repeatTests.repeatArgumentFailuresReportTheFirstInvalidWidth() }

    @Test
    fun corruptedCheckpointAtTheEndReportsTheNextMissingPrefixOffset() = MutFlow.underTest { repeatTests.corruptedCheckpointAtTheEndReportsTheNextMissingPrefixOffset() }

    @Test
    fun corruptedCheckpointElementEndingAtTheRegionBoundaryReportsTheNextPrefix() = MutFlow.underTest { repeatTests.corruptedCheckpointElementEndingAtTheRegionBoundaryReportsTheNextPrefix() }

    @Test
    fun repeatedElementLookupAtIntMaxSkippedLengthKeepsBadPrefixDiagnostics() = MutFlow.underTest { repeatTests.repeatedElementLookupAtIntMaxSkippedLengthKeepsBadPrefixDiagnostics() }

    @Test
    fun repeatedElementLookupAtIntMaxPayloadLengthKeepsBadPrefixDiagnostics() = MutFlow.underTest { repeatTests.repeatedElementLookupAtIntMaxPayloadLengthKeepsBadPrefixDiagnostics() }

}
