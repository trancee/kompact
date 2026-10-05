package ch.trancee.kompact.ksp.gen

// One MutFlow class scope containing the union of the existing adapter behaviors.
import io.github.anschnapp.mutflow.MutFlow
import kotlin.test.Test
import io.github.anschnapp.mutflow.junit.MutFlowTest

@MutFlowTest
class GeneratorUnionMutationTest {
    private val framedClassSourceTests = FramedClassGeneratorTest()
    private val framedClassRepeatPrefixWidthTests = FramedRepeatPrefixWidthTest()
    private val framedClassActualTests = FramedClassGeneratorActualTest()
    private val framedClassScalarTests = FramedScalarHolderGeneratorTest()
    private val framedScalarSourceTests = FramedScalarHolderGeneratorTest()
    private val framedScalarActualTests = FramedClassGeneratorActualTest()
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
    fun framedClassActualTestsPlatformActualsExposeFramedDecodeCreateCopyAndCompletionContracts() = MutFlow.underTest { framedClassActualTests.platformActualsExposeFramedDecodeCreateCopyAndCompletionContracts() }

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
    fun framedScalarActualTestsPlatformActualsExposeFramedDecodeCreateCopyAndCompletionContracts() = MutFlow.underTest { framedScalarActualTests.platformActualsExposeFramedDecodeCreateCopyAndCompletionContracts() }

    @Test
    fun expectValueClassDeclaresExpectKeyword() = MutFlow.underTest { valueClassSourceTests.`expect value class declares expect keyword`() }

    @Test
    fun expectValueClassDeclaresCompanionCreateFactory() = MutFlow.underTest { valueClassSourceTests.`expect value class declares companion create factory`() }

    @Test
    fun encodeFunctionWritesEachFieldAtItsDeclaredOffset() = MutFlow.underTest { valueClassSourceTests.`encode function writes each field at its declared offset`() }

    @Test
    fun commonEncoderEmitsReusableHolderCursorOperations() = MutFlow.underTest { valueClassSourceTests.`common encoder emits reusable holder cursor operations`() }

    @Test
    fun jvmActualHasJvmInlineAnnotation() = MutFlow.underTest { valueClassSourceTests.`jvm actual has JvmInline annotation`() }

    @Test
    fun jvmActualCompanionObjectIsMarkedActual() = MutFlow.underTest { valueClassSourceTests.`jvm actual companion object is marked actual`() }

    @Test
    fun iosActualCompanionObjectIsMarkedActual() = MutFlow.underTest { valueClassSourceTests.`ios actual companion object is marked actual`() }

    @Test
    fun jvmActualDefaultViewIsImmutableValNoWriteThroughSetter() = MutFlow.underTest { valueClassSourceTests.`jvm actual default view is immutable (val, no write-through setter)`() }

    @Test
    fun jvmActualDefaultViewEmitsCopyBuilderDelegatingToEncode() = MutFlow.underTest { valueClassSourceTests.`jvm actual default view emits copy builder delegating to encode`() }

    @Test
    fun expectDefaultViewCopyCarriesFieldDefaultsForCallers() = MutFlow.underTest { valueClassSourceTests.`expect default view copy carries field defaults for callers`() }

    @Test
    fun generateWithMutableModelEmitsMutableSiblingWithWriteThroughVarSetters() = MutFlow.underTest { valueClassSourceTests.`generate with mutable model emits Mutable sibling with write-through var setters`() }

    @Test
    fun jvmActualUsesRawReadBitsForIntFields() = MutFlow.underTest { valueClassSourceTests.`jvm actual uses raw readBits for Int fields`() }

    @Test
    fun signedIntGetterSignExtendsItsDeclaredBitWidth() = MutFlow.underTest { valueClassSourceTests.`signed Int getter sign extends its declared bit width`() }

    @Test
    fun signedLongGetterSignExtendsItsDeclaredBitWidth() = MutFlow.underTest { valueClassSourceTests.`signed Long getter sign extends its declared bit width`() }

    @Test
    fun createEncoderWritesFieldsAtDeclaredOffsetsIncludingGaps() = MutFlow.underTest { valueClassSourceTests.`create encoder writes fields at declared offsets including gaps`() }

    @Test
    fun jvmActualUsesReadBitsBooleanForBooleanFields() = MutFlow.underTest { valueClassSourceTests.`jvm actual uses readBitsBoolean for Boolean fields`() }

    @Test
    fun jvmActualCreateDelegatesToEncodeFunction() = MutFlow.underTest { valueClassSourceTests.`jvm actual create delegates to encode function`() }

    @Test
    fun iosActualDoesNOTHaveJvmInline() = MutFlow.underTest { valueClassSourceTests.`ios actual does NOT have JvmInline`() }

    @Test
    fun iosActualUsesRawReadBits() = MutFlow.underTest { valueClassSourceTests.`ios actual uses raw readBits`() }

    @Test
    fun longFieldUsesReadBitsLong() = MutFlow.underTest { valueClassSourceTests.`Long field uses readBitsLong`() }

    @Test
    fun floatFieldUsesFloatFromBits() = MutFlow.underTest { valueClassSourceTests.`Float field uses Float fromBits`() }

    @Test
    fun doubleFieldUsesDoubleFromBits() = MutFlow.underTest { valueClassSourceTests.`Double field uses Double fromBits`() }

    @Test
    fun jvmActualEmitsRawAsAConstructorBackingValDefect3() = MutFlow.underTest { valueClassSourceTests.`jvm actual emits raw as a constructor backing val (defect #3)`() }

    @Test
    fun iosActualEmitsRawAsAConstructorBackingValDefect3() = MutFlow.underTest { valueClassSourceTests.`ios actual emits raw as a constructor backing val (defect #3)`() }

    @Test
    fun expectEmitsRawWithoutTheInvalidActualModifierDefect3() = MutFlow.underTest { valueClassSourceTests.`expect emits raw without the invalid actual modifier (defect #3)`() }

    @Test
    fun fixedLayoutStringFieldSuggestsFramedMode() = MutFlow.underTest { valueClassEdgeCaseTests.fixedLayoutStringFieldSuggestsFramedMode() }

    @Test
    fun invalidLayoutThrowsBeforeGeneration() = MutFlow.underTest { valueClassEdgeCaseTests.`invalid layout throws before generation`() }

    @Test
    fun commonEncoderRejectsFramedModels() = MutFlow.underTest { valueClassEdgeCaseTests.`common encoder rejects framed models`() }

    @Test
    fun signedIntEncoderWritesItsLowBitsDirectlyAtTheDeclaredOffset() = MutFlow.underTest { valueClassEdgeCaseTests.`signed Int encoder writes its low bits directly at the declared offset`() }

    @Test
    fun f001InitGuardUsesCorrectMinBufferSize() = MutFlow.underTest { valueClassEdgeCaseTests.`F-001 init guard uses correct min buffer size`() }

    @Test
    fun fixedLayoutByteArrayFieldDirectsCallersToFramedMode() = MutFlow.underTest { valueClassEdgeCaseTests.`fixed-layout ByteArray field directs callers to framed mode`() }

    @Test
    fun unknownTypeFailsGenerationWithDescriptiveError() = MutFlow.underTest { valueClassEdgeCaseTests.`unknown type fails generation with descriptive error`() }

    @Test
    fun modelWithNoFieldsGeneratesValidExpect() = MutFlow.underTest { valueClassEdgeCaseTests.`model with no fields generates valid expect`() }

    @Test
    fun modelWithNoFieldsGeneratesValidJvmActual() = MutFlow.underTest { valueClassEdgeCaseTests.`model with no fields generates valid jvm actual`() }

    @Test
    fun fixedLayoutExpectEncoderDirectsStringFieldsToFramedMode() = MutFlow.underTest { valueClassEdgeCaseTests.`fixed-layout expect encoder directs String fields to framed mode`() }

    @Test
    fun fixedLayoutExpectEncoderDirectsByteArrayFieldsToFramedMode() = MutFlow.underTest { valueClassEdgeCaseTests.`fixed-layout expect encoder directs ByteArray fields to framed mode`() }

    @Test
    fun expectWithUnknownTypeFailsGenerationWithDescriptiveError() = MutFlow.underTest { valueClassEdgeCaseTests.`expect with unknown type fails generation with descriptive error`() }

    @Test
    fun signed31BitIntGetterPreservesItsSignBit() = MutFlow.underTest { valueClassBoundaryTests.signed31BitIntGetterPreservesItsSignBit() }

    @Test
    fun signed63BitLongGetterPreservesItsSignBit() = MutFlow.underTest { valueClassBoundaryTests.signed63BitLongGetterPreservesItsSignBit() }

    @Test
    fun mutableSiblingDoesNotExposeImmutableCopyBuilder() = MutFlow.underTest { valueClassSourceTests.mutableSiblingDoesNotExposeImmutableCopyBuilder() }

    @Test
    fun generatedFactoryEncoderAndCopyPreserveEveryFieldAndActualBody() = MutFlow.underTest { valueClassSourceTests.generatedFactoryEncoderAndCopyPreserveEveryFieldAndActualBody() }

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
