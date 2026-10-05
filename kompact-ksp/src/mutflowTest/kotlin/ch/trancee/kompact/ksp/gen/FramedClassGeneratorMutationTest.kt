package ch.trancee.kompact.ksp.gen

// Compiled into the test source set only for the opt-in mutation task.
import io.github.anschnapp.mutflow.MutFlow
import io.github.anschnapp.mutflow.junit.MutFlowTest
import kotlin.test.Test

@MutFlowTest
class FramedClassGeneratorMutationTest {
    private val sourceTests = FramedClassGeneratorTest()
    private val repeatPrefixWidthTests = FramedRepeatPrefixWidthTest()
    private val actualTests = FramedClassGeneratorActualTest()
    private val scalarTests = FramedScalarHolderGeneratorTest()

    @Test
    fun delegates_01() = MutFlow.underTest { sourceTests.readExpression_forBlobMakesCopyExplicit() }

    @Test
    fun delegates_02() = MutFlow.underTest { sourceTests.readExpression_rejectsUnsupportedFramedFieldType() }

    @Test
    fun delegates_03() = MutFlow.underTest { sourceTests.writeExpression_rejectsUnsupportedFramedFieldType() }

    @Test
    fun delegates_04() = MutFlow.underTest { sourceTests.repeatedExpressions_rejectNestedOrUnsupportedElementShapes() }

    @Test
    fun delegates_05() = MutFlow.underTest { sourceTests.scalarReadExpression_coversEveryKindAndSignedWidthBand() }

    @Test
    fun delegates_06() = MutFlow.underTest { sourceTests.borrowedHolderGenerationCoversRangesRepeatsAndNestedFields() }

    @Test
    fun delegates_12() = MutFlow.underTest { scalarTests.scalarDecodedValuesCoverEveryKindAndSignedWidthBoundary() }

    @Test
    fun delegates_13() = MutFlow.underTest { scalarTests.scalarHolderGenerationCoversEveryScalarKindAndValidationShape() }

    @Test
    fun delegates_14() = MutFlow.underTest { scalarTests.signedBoundaryWidthsKeepDecodeAndValidationAssociatedWithTheirFields() }

    @Test
    fun delegates_11() = MutFlow.underTest { actualTests.platformActualsExposeFramedDecodeCreateCopyAndCompletionContracts() }

    @Test
    fun delegates_10() = MutFlow.underTest { sourceTests.borrowedStringPreflightValidatesUtf8WhileBlobPreflightDoesNot() }

    @Test
    fun delegates_09() = MutFlow.underTest { repeatPrefixWidthTests.repeatPrefixWidthsAreUsedByPreflightAndDecodeOperations() }

    @Test
    fun delegates_07() = MutFlow.underTest { sourceTests.holderGenerationOmitsUnsupportedFieldsAndSupportsEmptySchemas() }

    @Test
    fun delegates_08() = MutFlow.underTest { sourceTests.capitalizedFirstCharHandlesEmptyAndUnicodeNames() }
}
