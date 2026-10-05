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
    fun readExpressionForBlobMakesCopyExplicit() = MutFlow.underTest { sourceTests.readExpression_forBlobMakesCopyExplicit() }

    @Test
    fun readExpressionRejectsUnsupportedFramedFieldType() = MutFlow.underTest { sourceTests.readExpression_rejectsUnsupportedFramedFieldType() }

    @Test
    fun writeExpressionRejectsUnsupportedFramedFieldType() = MutFlow.underTest { sourceTests.writeExpression_rejectsUnsupportedFramedFieldType() }

    @Test
    fun repeatedExpressionsRejectNestedOrUnsupportedElementShapes() = MutFlow.underTest { sourceTests.repeatedExpressions_rejectNestedOrUnsupportedElementShapes() }

    @Test
    fun scalarReadExpressionCoversEveryKindAndSignedWidthBand() = MutFlow.underTest { sourceTests.scalarReadExpression_coversEveryKindAndSignedWidthBand() }

    @Test
    fun borrowedHolderGenerationCoversRangesRepeatsAndNestedFields() = MutFlow.underTest { sourceTests.borrowedHolderGenerationCoversRangesRepeatsAndNestedFields() }

    @Test
    fun scalarDecodedValuesCoverEveryKindAndSignedWidthBoundary() = MutFlow.underTest { scalarTests.scalarDecodedValuesCoverEveryKindAndSignedWidthBoundary() }

    @Test
    fun scalarHolderGenerationCoversEveryScalarKindAndValidationShape() = MutFlow.underTest { scalarTests.scalarHolderGenerationCoversEveryScalarKindAndValidationShape() }

    @Test
    fun signedBoundaryWidthsKeepDecodeAndValidationAssociatedWithTheirFields() = MutFlow.underTest { scalarTests.signedBoundaryWidthsKeepDecodeAndValidationAssociatedWithTheirFields() }

    @Test
    fun platformActualsExposeFramedDecodeCreateCopyAndCompletionContracts() = MutFlow.underTest { actualTests.platformActualsExposeFramedDecodeCreateCopyAndCompletionContracts() }

    @Test
    fun borrowedStringPreflightValidatesUtf8WhileBlobPreflightDoesNot() = MutFlow.underTest { sourceTests.borrowedStringPreflightValidatesUtf8WhileBlobPreflightDoesNot() }

    @Test
    fun repeatPrefixWidthsAreUsedByPreflightAndDecodeOperations() = MutFlow.underTest { repeatPrefixWidthTests.repeatPrefixWidthsAreUsedByPreflightAndDecodeOperations() }

    @Test
    fun holderGenerationOmitsUnsupportedFieldsAndSupportsEmptySchemas() = MutFlow.underTest { sourceTests.holderGenerationOmitsUnsupportedFieldsAndSupportsEmptySchemas() }

    @Test
    fun capitalizedFirstCharHandlesEmptyAndUnicodeNames() = MutFlow.underTest { sourceTests.capitalizedFirstCharHandlesEmptyAndUnicodeNames() }
}
