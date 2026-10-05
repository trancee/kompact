package ch.trancee.kompact.runtime

import io.github.anschnapp.mutflow.MutFlow
import kotlin.test.Test

class KompactCursorByteRangeMutationTest {
    private val commonTests = KompactCursorByteRangeTest()
    private val boundaryTests = KompactCursorByteRangeBoundaryTest()

    @Test
    fun emptyPayloadFitsExactlyAfterItsLengthPrefix() = MutFlow.underTest { boundaryTests.emptyPayloadFitsExactlyAfterItsLengthPrefix() }

    @Test
    fun maximumEightBitPayloadFitsExactlyAtTheBufferBoundary() = MutFlow.underTest { boundaryTests.maximumEightBitPayloadFitsExactlyAtTheBufferBoundary() }

    @Test
    fun copyToRejectsPositiveOffsetOverrunWithoutChangingDestination() = MutFlow.underTest { boundaryTests.copyToRejectsPositiveOffsetOverrunWithoutChangingDestination() }

    @Test
    fun rawRangeCaptureAndWritePreserveNonzeroByteOffsets() = MutFlow.underTest { commonTests.rawRangeCaptureAndWritePreserveNonzeroByteOffsets() }

    @Test
    fun byteRangePreflightRejectsSixteenBitLengthAbovePrefixMaximum() = MutFlow.underTest { commonTests.byteRangePreflightRejectsSixteenBitLengthAbovePrefixMaximum() }

    @Test
    fun nestedReadAndWriteAcceptMaximumEightBitPayloadAtExactCapacity() = MutFlow.underTest { commonTests.nestedReadAndWriteAcceptMaximumEightBitPayloadAtExactCapacity() }

    @Test
    fun captureDiagnosticsUseTheSelectedNonzeroByteRegion() = MutFlow.underTest { commonTests.captureDiagnosticsUseTheSelectedNonzeroByteRegion() }

    @Test
    fun prefixedWriteAllowsAdjacentRangesAndRejectsOverlapAtNonzeroCursorPosition() = MutFlow.underTest { commonTests.prefixedWriteAllowsAdjacentRangesAndRejectsOverlapAtNonzeroCursorPosition() }

    @Test
    fun intMaxLengthPrefixesKeepBadLengthDiagnosticsAndBindingsUnchanged() = MutFlow.underTest { commonTests.intMaxLengthPrefixesKeepBadLengthDiagnosticsAndBindingsUnchanged() }

    @Test
    fun byteRangeResetAndCopyRejectInvalidBoundsWithoutChangingTheView() = MutFlow.underTest { commonTests.byteRangeResetAndCopyRejectInvalidBoundsWithoutChangingTheView() }

    @Test
    fun byteRangeResetAcceptsEmptyRangeAtExactBufferEnd() = MutFlow.underTest { commonTests.byteRangeResetAcceptsEmptyRangeAtExactBufferEnd() }

    @Test
    fun byteRangeCopyAcceptsExactCapacity() = MutFlow.underTest { commonTests.byteRangeCopyAcceptsExactCapacity() }

    @Test
    fun emptyByteRangeCopiesToAnEmptyDestination() = MutFlow.underTest { commonTests.emptyByteRangeCopiesToAnEmptyDestination() }

    @Test
    fun rawRangePreflightAndCopyRejectAlignmentAliasingAndBounds() = MutFlow.underTest { commonTests.rawRangePreflightAndCopyRejectAlignmentAliasingAndBounds() }

    @Test
    fun regionCaptureRequiresByteAlignmentAndOptionallyValidUtf8() = MutFlow.underTest { commonTests.regionCaptureRequiresByteAlignmentAndOptionallyValidUtf8() }

    @Test
    fun nestedReadsRejectInvalidCursorsWidthsAlignmentAndTruncation() = MutFlow.underTest { commonTests.nestedReadsRejectInvalidCursorsWidthsAlignmentAndTruncation() }

    @Test
    fun nestedWritesPreflightLengthAlignmentAndCapacityBeforeMutation() = MutFlow.underTest { commonTests.nestedWritesPreflightLengthAlignmentAndCapacityBeforeMutation() }

    @Test
    fun prefixedReadsValidateWidthAlignmentLengthUtf8AndRangeAtomicity() = MutFlow.underTest { commonTests.prefixedReadsValidateWidthAlignmentLengthUtf8AndRangeAtomicity() }

    @Test
    fun byteRangeSkipAndWritePreflightRejectMalformedArgumentsAtomically() = MutFlow.underTest { commonTests.byteRangeSkipAndWritePreflightRejectMalformedArgumentsAtomically() }

    @Test
    fun byteRangeWritesAndCursorDiagnosticsPreserveTheCheckedContract() = MutFlow.underTest { commonTests.byteRangeWritesAndCursorDiagnosticsPreserveTheCheckedContract() }
}
