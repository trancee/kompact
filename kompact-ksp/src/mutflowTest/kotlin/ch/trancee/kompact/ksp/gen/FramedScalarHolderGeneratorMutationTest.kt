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
    fun scalarDecodedValuesCoverEveryKindAndSignedWidthBoundary() = MutFlow.underTest { sourceTests.scalarDecodedValuesCoverEveryKindAndSignedWidthBoundary() }

    @Test
    fun scalarHolderGenerationCoversEveryScalarKindAndValidationShape() = MutFlow.underTest { sourceTests.scalarHolderGenerationCoversEveryScalarKindAndValidationShape() }

    @Test
    fun signedBoundaryWidthsKeepDecodeAndValidationAssociatedWithTheirFields() = MutFlow.underTest { sourceTests.signedBoundaryWidthsKeepDecodeAndValidationAssociatedWithTheirFields() }
    @Test
    fun borrowedHolderGenerationCoversRangesRepeatsAndNestedFields() = MutFlow.underTest { framedTests.borrowedHolderGenerationCoversRangesRepeatsAndNestedFields() }

    @Test
    fun platformActualsExposeFramedDecodeCreateCopyAndCompletionContracts() = MutFlow.underTest { actualTests.platformActualsExposeFramedDecodeCreateCopyAndCompletionContracts() }

}
