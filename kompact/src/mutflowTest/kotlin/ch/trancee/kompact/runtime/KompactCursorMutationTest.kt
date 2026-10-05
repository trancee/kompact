package ch.trancee.kompact.runtime

import io.github.anschnapp.mutflow.MutFlow
import kotlin.test.Test

class KompactCursorMutationTest {
    private val commonTests = KompactCursorTest()
    private val repeatTests = KompactCursorRepeatsTest()

    @Test
    fun delegates_01() = MutFlow.underTest { commonTests.byteRangeConstructorInitiallyBorrowsTheWholeBuffer() }

    @Test
    fun delegates_02() = MutFlow.underTest { commonTests.resetRejectsOutOfBoundsWithoutChangingTheBoundRegion() }

    @Test
    fun delegates_03() = MutFlow.underTest { commonTests.readBitsStopsAtTheBoundAndPreservesTheLastValueOnFailure() }

    @Test
    fun delegates_04() = MutFlow.underTest { commonTests.uncheckedWritePreservesNeighboringBitsAndRejectsOutOfBoundsAtomically() }

    @Test
    fun delegates_05() = MutFlow.underTest { commonTests.checkedWritesRejectValuesOutsideTheirDeclaredWidthWithoutMutation() }

    @Test
    fun delegates_06() = MutFlow.underTest { commonTests.nestedReadUsesASeparateBoundedCursorAndDoesNotEnterTheFollowingField() }

    @Test
    fun delegates_07() = MutFlow.underTest { commonTests.malformedNestedReadLeavesParentAndChildBindingsUnchanged() }

    @Test
    fun delegates_08() = MutFlow.underTest { commonTests.nestedWriteReservesKnownLengthAndBoundsTheChildCursor() }

    @Test
    fun delegates_09() = MutFlow.underTest { commonTests.byteRangeReadAndWriteBorrowAndAppendWithoutAResultWrapper() }

    @Test
    fun delegates_10() = MutFlow.underTest { commonTests.borrowedUtf8RangeCanBeCopiedIntoCallerOwnedStorage() }

    @Test
    fun delegates_11() = MutFlow.underTest { commonTests.utf8ByteRangeValidatesBeforeChangingCursorOrRange() }

    @Test
    fun delegates_12() = MutFlow.underTest { commonTests.variableRepeatReportsInsufficientWorkspaceWithoutAdvancing() }

    @Test
    fun delegates_13() = MutFlow.underTest { commonTests.variableRepeatIndexesPayloadsInCallerOwnedWorkspace() }

    @Test
    fun delegates_14() = MutFlow.underTest { commonTests.variableRepeatRequiresOneCheckpointPer64Elements() }

    @Test
    fun delegates_15() = MutFlow.underTest { commonTests.fixedRepeatIndexesElementsWithoutAllocatingAnIndexArray() }

    @Test
    fun delegates_16() = MutFlow.underTest { commonTests.fixedRepeatRejectsInsufficientWorkspaceWithoutChangingWorkspaceOrCursor() }

    @Test
    fun delegates_17() = MutFlow.underTest { commonTests.variableRepeatRejects65ElementsWhenOnlyOneCheckpointIsAvailable() }

    @Test
    fun delegates_18() = MutFlow.underTest { commonTests.truncatedVariableRepeatDoesNotAdvanceOrReplaceWorkspace() }

    @Test
    fun delegates_19() = MutFlow.underTest { repeatTests.variableRepeatLookupAtIndex63ReturnsItsPayload() }

    @Test
    fun delegates_20() = MutFlow.underTest { repeatTests.variableRepeatLookupAtIndex64ReturnsItsPayload() }

    @Test
    fun delegates_21() = MutFlow.underTest { repeatTests.variableRepeatLookupAtIndex65ReturnsItsPayload() }

    @Test
    fun delegates_22() = MutFlow.underTest { repeatTests.fixedRepeatLookupAtIndex63ReturnsItsPayload() }

    @Test
    fun delegates_23() = MutFlow.underTest { repeatTests.fixedRepeatLookupAtIndex64ReturnsItsPayload() }

    @Test
    fun delegates_24() = MutFlow.underTest { repeatTests.fixedRepeatLookupAtIndex65ReturnsItsPayload() }

    @Test
    fun delegates_25() = MutFlow.underTest { repeatTests.outOfRangeRepeatLookupRetainsElementBindingAndReportsDiagnostics() }
    @Test
    fun delegates_26() = MutFlow.underTest { repeatTests.variableRepeatSkipRejectsInvalidWidthsAlignmentAndMalformedPrefixes() }

    @Test
    fun delegates_27() = MutFlow.underTest { repeatTests.fixedRepeatSkipChecksCountWidthCapacityAndPayloadBounds() }

    @Test
    fun delegates_28() = MutFlow.underTest { repeatTests.variableRepeatReadIsFailureAtomicAndIndexesEmptyAndNonemptyRepeats() }

    @Test
    fun delegates_29() = MutFlow.underTest { repeatTests.fixedRepeatReadValidatesArgumentsCapacityAndElementBounds() }

    @Test
    fun delegates_30() = MutFlow.underTest { repeatTests.repeatedElementReadRejectsWrongBindingAndOutOfRangeIndexes() }

    @Test
    fun delegates_31() = MutFlow.underTest { repeatTests.indexedElementReadChecksFixedAndVariableOffsetsAgainstWorkspaceBounds() }

    @Test
    fun delegates_32() = MutFlow.underTest { repeatTests.fixedRepeatIndexingStoresSparseCheckpointsAcrossBlocks() }

    @Test
    fun delegates_33() = MutFlow.underTest { repeatTests.variableRepeatAcceptsEmptyElementEndingExactlyAtTheRegionBoundary() }

    @Test
    fun delegates_34() = MutFlow.underTest { repeatTests.repeatCountsAndLengthsAtIntMaximumKeepTheirSpecificFailureStatus() }

    @Test
    fun delegates_35() = MutFlow.underTest { repeatTests.variableRepeatCheckpointCapacityHandlesZeroAndExactSixtyFourCount() }

    @Test
    fun delegates_36() = MutFlow.underTest { repeatTests.fixedRepeatCheckpointLoopAcceptsAnExactSixtyFourElementCapacity() }

    @Test
    fun delegates_37() = MutFlow.underTest { repeatTests.repeatArgumentFailuresReportTheFirstInvalidWidth() }

    @Test
    fun delegates_38() = MutFlow.underTest { repeatTests.corruptedCheckpointAtTheEndReportsTheNextMissingPrefixOffset() }

    @Test
    fun delegates_39() = MutFlow.underTest { repeatTests.corruptedCheckpointElementEndingAtTheRegionBoundaryReportsTheNextPrefix() }

    @Test
    fun delegates_40() = MutFlow.underTest { repeatTests.repeatedElementLookupAtIntMaxSkippedLengthKeepsBadPrefixDiagnostics() }

    @Test
    fun delegates_41() = MutFlow.underTest { repeatTests.repeatedElementLookupAtIntMaxPayloadLengthKeepsBadPrefixDiagnostics() }

}
