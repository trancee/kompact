package ch.trancee.kompact.ksp.gen

// Compiled into the test source set only for the opt-in mutation task.
import io.github.anschnapp.mutflow.MutFlow
import io.github.anschnapp.mutflow.junit.MutFlowTest
import kotlin.test.Test

@MutFlowTest
class FramedScalarHolderGeneratorMutationTest {
    private val sourceTests = FramedScalarHolderGeneratorTest()
    private val actualTests = FramedClassGeneratorActualTest()
    private val framedTests = FramedClassGeneratorTest()

    @Test
    fun delegates_01() = MutFlow.underTest { sourceTests.scalarDecodedValuesCoverEveryKindAndSignedWidthBoundary() }

    @Test
    fun delegates_02() = MutFlow.underTest { sourceTests.scalarHolderGenerationCoversEveryScalarKindAndValidationShape() }

    @Test
    fun delegates_03() = MutFlow.underTest { sourceTests.signedBoundaryWidthsKeepDecodeAndValidationAssociatedWithTheirFields() }
    @Test
    fun delegates_05() = MutFlow.underTest { framedTests.borrowedHolderGenerationCoversRangesRepeatsAndNestedFields() }

    @Test
    fun delegates_04() = MutFlow.underTest { actualTests.platformActualsExposeFramedDecodeCreateCopyAndCompletionContracts() }

}
