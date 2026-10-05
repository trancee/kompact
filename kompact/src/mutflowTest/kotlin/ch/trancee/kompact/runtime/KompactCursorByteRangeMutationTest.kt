package ch.trancee.kompact.runtime

import io.github.anschnapp.mutflow.MutFlow
import kotlin.test.Test

class KompactCursorByteRangeMutationTest {
    private val commonTests = KompactCursorByteRangeTest()
    private val boundaryTests = KompactCursorByteRangeBoundaryTest()

    @Test
    fun delegates_12() = MutFlow.underTest { boundaryTests.emptyPayloadFitsExactlyAfterItsLengthPrefix() }

    @Test
    fun delegates_13() = MutFlow.underTest { boundaryTests.maximumEightBitPayloadFitsExactlyAtTheBufferBoundary() }

    @Test
    fun delegates_14() = MutFlow.underTest { boundaryTests.copyToRejectsPositiveOffsetOverrunWithoutChangingDestination() }

    @Test
    fun delegates_15() = MutFlow.underTest { commonTests.rawRangeCaptureAndWritePreserveNonzeroByteOffsets() }

    @Test
    fun delegates_16() = MutFlow.underTest { commonTests.byteRangePreflightRejectsSixteenBitLengthAbovePrefixMaximum() }

    @Test
    fun delegates_17() = MutFlow.underTest { commonTests.nestedReadAndWriteAcceptMaximumEightBitPayloadAtExactCapacity() }

    @Test
    fun delegates_18() = MutFlow.underTest { commonTests.captureDiagnosticsUseTheSelectedNonzeroByteRegion() }

    @Test
    fun delegates_19() = MutFlow.underTest { commonTests.prefixedWriteAllowsAdjacentRangesAndRejectsOverlapAtNonzeroCursorPosition() }

    @Test
    fun delegates_20() = MutFlow.underTest { commonTests.intMaxLengthPrefixesKeepBadLengthDiagnosticsAndBindingsUnchanged() }

    @Test
    fun delegates_01() = MutFlow.underTest { commonTests.byteRangeResetAndCopyRejectInvalidBoundsWithoutChangingTheView() }

    @Test
    fun delegates_09() = MutFlow.underTest { commonTests.byteRangeResetAcceptsEmptyRangeAtExactBufferEnd() }

    @Test
    fun delegates_10() = MutFlow.underTest { commonTests.byteRangeCopyAcceptsExactCapacity() }

    @Test
    fun delegates_11() = MutFlow.underTest { commonTests.emptyByteRangeCopiesToAnEmptyDestination() }

    @Test
    fun delegates_02() = MutFlow.underTest { commonTests.rawRangePreflightAndCopyRejectAlignmentAliasingAndBounds() }

    @Test
    fun delegates_03() = MutFlow.underTest { commonTests.regionCaptureRequiresByteAlignmentAndOptionallyValidUtf8() }

    @Test
    fun delegates_04() = MutFlow.underTest { commonTests.nestedReadsRejectInvalidCursorsWidthsAlignmentAndTruncation() }

    @Test
    fun delegates_05() = MutFlow.underTest { commonTests.nestedWritesPreflightLengthAlignmentAndCapacityBeforeMutation() }

    @Test
    fun delegates_06() = MutFlow.underTest { commonTests.prefixedReadsValidateWidthAlignmentLengthUtf8AndRangeAtomicity() }

    @Test
    fun delegates_07() = MutFlow.underTest { commonTests.byteRangeSkipAndWritePreflightRejectMalformedArgumentsAtomically() }

    @Test
    fun delegates_08() = MutFlow.underTest { commonTests.byteRangeWritesAndCursorDiagnosticsPreserveTheCheckedContract() }
}
