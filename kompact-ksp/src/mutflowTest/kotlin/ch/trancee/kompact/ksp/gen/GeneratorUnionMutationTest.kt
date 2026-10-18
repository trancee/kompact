package ch.trancee.kompact.ksp.gen

// One MutFlow class scope containing the union of the existing adapter behaviors.
import io.github.anschnapp.mutflow.MutFlow
import kotlin.test.Test
import io.github.anschnapp.mutflow.junit.MutFlowTest

@MutFlowTest
class GeneratorUnionMutationTest {
    private val framedClassSourceTests = FramedClassGeneratorTest()
    private val framedClassRepeatPrefixWidthTests = FramedRepeatPrefixWidthTest()
    private val framedClassViewTests = FramedClassGeneratorViewTest()
    private val framedClassScalarTests = FramedScalarHolderGeneratorTest()
    private val framedScalarSourceTests = FramedScalarHolderGeneratorTest()
    private val framedScalarViewTests = FramedClassGeneratorViewTest()
    private val framedScalarFramedTests = FramedClassGeneratorTest()
    private val valueClassSourceTests = ValueClassGeneratorTest()
    private val valueClassEdgeCaseTests = ValueClassGeneratorEdgeCaseTest()
    private val valueClassBoundaryTests = ValueClassGeneratorBoundaryTest()
    private val valueHolderSourceTests = ValueHolderGeneratorTest()

    @Test
    fun readExpressionForBlobMakesCopyExplicit() = MutFlow.underTest { framedClassSourceTests.readExpression_forBlobMakesCopyExplicit() }

    @Test
    fun readExpressionRejectsUnsupportedFramedFieldType() = MutFlow.underTest { framedClassSourceTests.readExpression_rejectsUnsupportedFramedFieldType() }

    @Test
    fun writeExpressionRejectsUnsupportedFramedFieldType() = MutFlow.underTest { framedClassSourceTests.writeExpression_rejectsUnsupportedFramedFieldType() }

    @Test
    fun repeatedExpressionsRejectNestedOrUnsupportedElementShapes() = MutFlow.underTest { framedClassSourceTests.repeatedExpressions_rejectNestedOrUnsupportedElementShapes() }

    @Test
    fun scalarReadExpressionCoversEveryKindAndSignedWidthBand() = MutFlow.underTest { framedClassSourceTests.scalarReadExpression_coversEveryKindAndSignedWidthBand() }

    @Test
    fun framedClassSourceTestsBorrowedHolderGenerationCoversRangesRepeatsAndNestedFields() = MutFlow.underTest { framedClassSourceTests.borrowedHolderGenerationCoversRangesRepeatsAndNestedFields() }

    @Test
    fun framedClassScalarTestsScalarDecodedValuesCoverEveryKindAndSignedWidthBoundary() = MutFlow.underTest { framedClassScalarTests.scalarDecodedValuesCoverEveryKindAndSignedWidthBoundary() }

    @Test
    fun framedClassScalarTestsScalarHolderGenerationCoversEveryScalarKindAndValidationShape() = MutFlow.underTest { framedClassScalarTests.scalarHolderGenerationCoversEveryScalarKindAndValidationShape() }

    @Test
    fun framedClassScalarTestsSignedBoundaryWidthsKeepDecodeAndValidationAssociatedWithTheirFields() = MutFlow.underTest { framedClassScalarTests.signedBoundaryWidthsKeepDecodeAndValidationAssociatedWithTheirFields() }

    @Test
    fun framedClassViewTestsCommonViewExposesFramedDecodeCreateCopyAndCompletionContracts() = MutFlow.underTest { framedClassViewTests.commonViewExposesFramedDecodeCreateCopyAndCompletionContracts() }

    @Test
    fun borrowedStringPreflightValidatesUtf8WhileBlobPreflightDoesNot() = MutFlow.underTest { framedClassSourceTests.borrowedStringPreflightValidatesUtf8WhileBlobPreflightDoesNot() }

    @Test
    fun repeatPrefixWidthsAreUsedByPreflightAndDecodeOperations() = MutFlow.underTest { framedClassRepeatPrefixWidthTests.repeatPrefixWidthsAreUsedByPreflightAndDecodeOperations() }

    @Test
    fun holderGenerationOmitsUnsupportedFieldsAndSupportsEmptySchemas() = MutFlow.underTest { framedClassSourceTests.holderGenerationOmitsUnsupportedFieldsAndSupportsEmptySchemas() }

    @Test
    fun capitalizedFirstCharHandlesEmptyAndUnicodeNames() = MutFlow.underTest { framedClassSourceTests.capitalizedFirstCharHandlesEmptyAndUnicodeNames() }

    @Test
    fun framedScalarSourceTestsScalarDecodedValuesCoverEveryKindAndSignedWidthBoundary() = MutFlow.underTest { framedScalarSourceTests.scalarDecodedValuesCoverEveryKindAndSignedWidthBoundary() }

    @Test
    fun framedScalarSourceTestsScalarHolderGenerationCoversEveryScalarKindAndValidationShape() = MutFlow.underTest { framedScalarSourceTests.scalarHolderGenerationCoversEveryScalarKindAndValidationShape() }

    @Test
    fun framedScalarSourceTestsSignedBoundaryWidthsKeepDecodeAndValidationAssociatedWithTheirFields() = MutFlow.underTest { framedScalarSourceTests.signedBoundaryWidthsKeepDecodeAndValidationAssociatedWithTheirFields() }

    @Test
    fun framedScalarFramedTestsBorrowedHolderGenerationCoversRangesRepeatsAndNestedFields() = MutFlow.underTest { framedScalarFramedTests.borrowedHolderGenerationCoversRangesRepeatsAndNestedFields() }

    @Test
    fun framedScalarViewTestsCommonViewExposesFramedDecodeCreateCopyAndCompletionContracts() = MutFlow.underTest { framedScalarViewTests.commonViewExposesFramedDecodeCreateCopyAndCompletionContracts() }

    @Test
    fun viewIsARegularCommonJvmInlineValueClassNamedAfterTheSchema() = MutFlow.underTest { valueClassSourceTests.`view is a regular common JvmInline value class named after the schema`() }

    @Test
    fun viewCompanionCreateFactoryDelegatesToEncodeFunction() = MutFlow.underTest { valueClassSourceTests.`view companion create factory delegates to encode function`() }

    @Test
    fun encodeFunctionWritesEachFieldAtItsDeclaredOffset() = MutFlow.underTest { valueClassSourceTests.`encode function writes each field at its declared offset`() }

    @Test
    fun viewFileEmitsReusableHolderCursorOperations() = MutFlow.underTest { valueClassSourceTests.`view file emits reusable holder cursor operations`() }

    @Test
    fun defaultViewIsImmutableValNoWriteThroughSetter() = MutFlow.underTest { valueClassSourceTests.`default view is immutable (val, no write-through setter)`() }

    @Test
    fun defaultViewCopyDefaultsEveryFieldAndDelegatesToEncode() = MutFlow.underTest { valueClassSourceTests.`default view copy defaults every field and delegates to encode`() }

    @Test
    fun generateWithMutableModelEmitsMutableSiblingWithWriteThroughVarSetters() = MutFlow.underTest { valueClassSourceTests.`generate with mutable model emits Mutable sibling with write-through var setters`() }

    @Test
    fun viewUsesRawReadBitsForIntFields() = MutFlow.underTest { valueClassSourceTests.`view uses raw readBits for Int fields`() }

    @Test
    fun signedIntGetterSignExtendsItsDeclaredBitWidth() = MutFlow.underTest { valueClassSourceTests.`signed Int getter sign extends its declared bit width`() }

    @Test
    fun signedLongGetterSignExtendsItsDeclaredBitWidth() = MutFlow.underTest { valueClassSourceTests.`signed Long getter sign extends its declared bit width`() }

    @Test
    fun createEncoderWritesFieldsAtDeclaredOffsetsIncludingGaps() = MutFlow.underTest { valueClassSourceTests.`create encoder writes fields at declared offsets including gaps`() }

    @Test
    fun viewUsesReadBitsBooleanForBooleanFields() = MutFlow.underTest { valueClassSourceTests.`view uses readBitsBoolean for Boolean fields`() }

    @Test
    fun longFieldUsesReadBitsLong() = MutFlow.underTest { valueClassSourceTests.`Long field uses readBitsLong`() }

    @Test
    fun floatFieldUsesFloatFromBits() = MutFlow.underTest { valueClassSourceTests.`Float field uses Float fromBits`() }

    @Test
    fun doubleFieldUsesDoubleFromBits() = MutFlow.underTest { valueClassSourceTests.`Double field uses Double fromBits`() }

    @Test
    fun viewEmitsRawAsAConstructorBackingValDefect3() = MutFlow.underTest { valueClassSourceTests.`view emits raw as a constructor backing val (defect #3)`() }

    @Test
    fun fixedLayoutStringFieldSuggestsFramedMode() = MutFlow.underTest { valueClassEdgeCaseTests.fixedLayoutStringFieldSuggestsFramedMode() }

    @Test
    fun invalidLayoutThrowsBeforeGeneration() = MutFlow.underTest { valueClassEdgeCaseTests.`invalid layout throws before generation`() }

    @Test
    fun framedModelsDelegateToTheFramedViewGenerator() = MutFlow.underTest { valueClassEdgeCaseTests.`framed models delegate to the framed view generator`() }

    @Test
    fun signedIntEncoderWritesItsLowBitsDirectlyAtTheDeclaredOffset() = MutFlow.underTest { valueClassEdgeCaseTests.`signed Int encoder writes its low bits directly at the declared offset`() }

    @Test
    fun f001InitGuardUsesCorrectMinBufferSize() = MutFlow.underTest { valueClassEdgeCaseTests.`F-001 init guard uses correct min buffer size`() }

    @Test
    fun fixedLayoutByteArrayFieldDirectsCallersToFramedMode() = MutFlow.underTest { valueClassEdgeCaseTests.`fixed-layout ByteArray field directs callers to framed mode`() }

    @Test
    fun unknownTypeFailsGenerationWithDescriptiveError() = MutFlow.underTest { valueClassEdgeCaseTests.`unknown type fails generation with descriptive error`() }

    @Test
    fun modelWithNoFieldsGeneratesAViewWithoutACreateFactory() = MutFlow.underTest { valueClassEdgeCaseTests.`model with no fields generates a view without a create factory`() }

    @Test
    fun signed31BitIntGetterPreservesItsSignBit() = MutFlow.underTest { valueClassBoundaryTests.signed31BitIntGetterPreservesItsSignBit() }

    @Test
    fun signed63BitLongGetterPreservesItsSignBit() = MutFlow.underTest { valueClassBoundaryTests.signed63BitLongGetterPreservesItsSignBit() }

    @Test
    fun mutableSiblingDoesNotExposeImmutableCopyBuilder() = MutFlow.underTest { valueClassSourceTests.mutableSiblingDoesNotExposeImmutableCopyBuilder() }

    @Test
    fun generatedFactoryEncoderAndCopyPreserveEveryField() = MutFlow.underTest { valueClassSourceTests.generatedFactoryEncoderAndCopyPreserveEveryField() }

    @Test
    fun generatedSignednessAnnotationRemainsAttachedToItsField() = MutFlow.underTest { valueClassSourceTests.generatedSignednessAnnotationRemainsAttachedToItsField() }

    @Test
    fun generatedFieldAnnotationsPreserveSignednessOnlyForSignedFields() = MutFlow.underTest { valueClassSourceTests.generatedFieldAnnotationsPreserveSignednessOnlyForSignedFields() }

    @Test
    fun commonEncoderSignExtendsNarrowSignedLongHolderFields() = MutFlow.underTest { valueClassSourceTests.commonEncoderSignExtendsNarrowSignedLongHolderFields() }

    @Test
    fun commonEncoderCoversScalarKindsSignedWidthsAndReservedBits() = MutFlow.underTest { valueHolderSourceTests.commonEncoderCoversScalarKindsSignedWidthsAndReservedBits() }

    @Test
    fun generatedHolderConsumesOnlyRealGapsAndTheExactDeclaredFrame() = MutFlow.underTest { valueHolderSourceTests.generatedHolderConsumesOnlyRealGapsAndTheExactDeclaredFrame() }

    @Test
    fun decodeCommitsSignedValuesAndEncodeValidatesBeforeWritingReservedBits() = MutFlow.underTest { valueHolderSourceTests.decodeCommitsSignedValuesAndEncodeValidatesBeforeWritingReservedBits() }
}
