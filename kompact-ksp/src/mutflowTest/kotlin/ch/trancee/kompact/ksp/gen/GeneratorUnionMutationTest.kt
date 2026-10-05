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
    fun delegates_union_001() = MutFlow.underTest { framedClassSourceTests.readExpression_forBlobMakesCopyExplicit() }

    @Test
    fun delegates_union_002() = MutFlow.underTest { framedClassSourceTests.readExpression_rejectsUnsupportedFramedFieldType() }

    @Test
    fun delegates_union_003() = MutFlow.underTest { framedClassSourceTests.writeExpression_rejectsUnsupportedFramedFieldType() }

    @Test
    fun delegates_union_004() = MutFlow.underTest { framedClassSourceTests.repeatedExpressions_rejectNestedOrUnsupportedElementShapes() }

    @Test
    fun delegates_union_005() = MutFlow.underTest { framedClassSourceTests.scalarReadExpression_coversEveryKindAndSignedWidthBand() }

    @Test
    fun delegates_union_006() = MutFlow.underTest { framedClassSourceTests.borrowedHolderGenerationCoversRangesRepeatsAndNestedFields() }

    @Test
    fun delegates_union_007() = MutFlow.underTest { framedClassScalarTests.scalarDecodedValuesCoverEveryKindAndSignedWidthBoundary() }

    @Test
    fun delegates_union_008() = MutFlow.underTest { framedClassScalarTests.scalarHolderGenerationCoversEveryScalarKindAndValidationShape() }

    @Test
    fun delegates_union_009() = MutFlow.underTest { framedClassScalarTests.signedBoundaryWidthsKeepDecodeAndValidationAssociatedWithTheirFields() }

    @Test
    fun delegates_union_010() = MutFlow.underTest { framedClassActualTests.platformActualsExposeFramedDecodeCreateCopyAndCompletionContracts() }

    @Test
    fun delegates_union_011() = MutFlow.underTest { framedClassSourceTests.borrowedStringPreflightValidatesUtf8WhileBlobPreflightDoesNot() }

    @Test
    fun delegates_union_012() = MutFlow.underTest { framedClassRepeatPrefixWidthTests.repeatPrefixWidthsAreUsedByPreflightAndDecodeOperations() }

    @Test
    fun delegates_union_013() = MutFlow.underTest { framedClassSourceTests.holderGenerationOmitsUnsupportedFieldsAndSupportsEmptySchemas() }

    @Test
    fun delegates_union_014() = MutFlow.underTest { framedClassSourceTests.capitalizedFirstCharHandlesEmptyAndUnicodeNames() }

    @Test
    fun delegates_union_015() = MutFlow.underTest { framedScalarSourceTests.scalarDecodedValuesCoverEveryKindAndSignedWidthBoundary() }

    @Test
    fun delegates_union_016() = MutFlow.underTest { framedScalarSourceTests.scalarHolderGenerationCoversEveryScalarKindAndValidationShape() }

    @Test
    fun delegates_union_017() = MutFlow.underTest { framedScalarSourceTests.signedBoundaryWidthsKeepDecodeAndValidationAssociatedWithTheirFields() }

    @Test
    fun delegates_union_018() = MutFlow.underTest { framedScalarFramedTests.borrowedHolderGenerationCoversRangesRepeatsAndNestedFields() }

    @Test
    fun delegates_union_019() = MutFlow.underTest { framedScalarActualTests.platformActualsExposeFramedDecodeCreateCopyAndCompletionContracts() }

    @Test
    fun delegates_union_020() = MutFlow.underTest { valueClassSourceTests.`expect value class declares expect keyword`() }

    @Test
    fun delegates_union_021() = MutFlow.underTest { valueClassSourceTests.`expect value class declares companion create factory`() }

    @Test
    fun delegates_union_022() = MutFlow.underTest { valueClassSourceTests.`encode function writes each field at its declared offset`() }

    @Test
    fun delegates_union_023() = MutFlow.underTest { valueClassSourceTests.`common encoder emits reusable holder cursor operations`() }

    @Test
    fun delegates_union_024() = MutFlow.underTest { valueClassSourceTests.`jvm actual has JvmInline annotation`() }

    @Test
    fun delegates_union_025() = MutFlow.underTest { valueClassSourceTests.`jvm actual companion object is marked actual`() }

    @Test
    fun delegates_union_026() = MutFlow.underTest { valueClassSourceTests.`ios actual companion object is marked actual`() }

    @Test
    fun delegates_union_027() = MutFlow.underTest { valueClassSourceTests.`jvm actual default view is immutable (val, no write-through setter)`() }

    @Test
    fun delegates_union_028() = MutFlow.underTest { valueClassSourceTests.`jvm actual default view emits copy builder delegating to encode`() }

    @Test
    fun delegates_union_029() = MutFlow.underTest { valueClassSourceTests.`expect default view copy carries field defaults for callers`() }

    @Test
    fun delegates_union_030() = MutFlow.underTest { valueClassSourceTests.`generate with mutable model emits Mutable sibling with write-through var setters`() }

    @Test
    fun delegates_union_031() = MutFlow.underTest { valueClassSourceTests.`jvm actual uses raw readBits for Int fields`() }

    @Test
    fun delegates_union_032() = MutFlow.underTest { valueClassSourceTests.`signed Int getter sign extends its declared bit width`() }

    @Test
    fun delegates_union_033() = MutFlow.underTest { valueClassSourceTests.`signed Long getter sign extends its declared bit width`() }

    @Test
    fun delegates_union_034() = MutFlow.underTest { valueClassSourceTests.`create encoder writes fields at declared offsets including gaps`() }

    @Test
    fun delegates_union_035() = MutFlow.underTest { valueClassSourceTests.`jvm actual uses readBitsBoolean for Boolean fields`() }

    @Test
    fun delegates_union_036() = MutFlow.underTest { valueClassSourceTests.`jvm actual create delegates to encode function`() }

    @Test
    fun delegates_union_037() = MutFlow.underTest { valueClassSourceTests.`ios actual does NOT have JvmInline`() }

    @Test
    fun delegates_union_038() = MutFlow.underTest { valueClassSourceTests.`ios actual uses raw readBits`() }

    @Test
    fun delegates_union_039() = MutFlow.underTest { valueClassSourceTests.`Long field uses readBitsLong`() }

    @Test
    fun delegates_union_040() = MutFlow.underTest { valueClassSourceTests.`Float field uses Float fromBits`() }

    @Test
    fun delegates_union_041() = MutFlow.underTest { valueClassSourceTests.`Double field uses Double fromBits`() }

    @Test
    fun delegates_union_042() = MutFlow.underTest { valueClassSourceTests.`jvm actual emits raw as a constructor backing val (defect #3)`() }

    @Test
    fun delegates_union_043() = MutFlow.underTest { valueClassSourceTests.`ios actual emits raw as a constructor backing val (defect #3)`() }

    @Test
    fun delegates_union_044() = MutFlow.underTest { valueClassSourceTests.`expect emits raw without the invalid actual modifier (defect #3)`() }

    @Test
    fun delegates_union_045() = MutFlow.underTest { valueClassEdgeCaseTests.fixedLayoutStringFieldSuggestsFramedMode() }

    @Test
    fun delegates_union_046() = MutFlow.underTest { valueClassEdgeCaseTests.`invalid layout throws before generation`() }

    @Test
    fun delegates_union_047() = MutFlow.underTest { valueClassEdgeCaseTests.`common encoder rejects framed models`() }

    @Test
    fun delegates_union_048() = MutFlow.underTest { valueClassEdgeCaseTests.`signed Int encoder writes its low bits directly at the declared offset`() }

    @Test
    fun delegates_union_049() = MutFlow.underTest { valueClassEdgeCaseTests.`F-001 init guard uses correct min buffer size`() }

    @Test
    fun delegates_union_050() = MutFlow.underTest { valueClassEdgeCaseTests.`fixed-layout ByteArray field directs callers to framed mode`() }

    @Test
    fun delegates_union_051() = MutFlow.underTest { valueClassEdgeCaseTests.`unknown type fails generation with descriptive error`() }

    @Test
    fun delegates_union_052() = MutFlow.underTest { valueClassEdgeCaseTests.`model with no fields generates valid expect`() }

    @Test
    fun delegates_union_053() = MutFlow.underTest { valueClassEdgeCaseTests.`model with no fields generates valid jvm actual`() }

    @Test
    fun delegates_union_054() = MutFlow.underTest { valueClassEdgeCaseTests.`fixed-layout expect encoder directs String fields to framed mode`() }

    @Test
    fun delegates_union_055() = MutFlow.underTest { valueClassEdgeCaseTests.`fixed-layout expect encoder directs ByteArray fields to framed mode`() }

    @Test
    fun delegates_union_056() = MutFlow.underTest { valueClassEdgeCaseTests.`expect with unknown type fails generation with descriptive error`() }

    @Test
    fun delegates_union_057() = MutFlow.underTest { valueClassBoundaryTests.signed31BitIntGetterPreservesItsSignBit() }

    @Test
    fun delegates_union_058() = MutFlow.underTest { valueClassBoundaryTests.signed63BitLongGetterPreservesItsSignBit() }

    @Test
    fun delegates_union_059() = MutFlow.underTest { valueClassSourceTests.mutableSiblingDoesNotExposeImmutableCopyBuilder() }

    @Test
    fun delegates_union_060() = MutFlow.underTest { valueClassSourceTests.generatedFactoryEncoderAndCopyPreserveEveryFieldAndActualBody() }

    @Test
    fun delegates_union_061() = MutFlow.underTest { valueClassSourceTests.generatedSignednessAnnotationRemainsAttachedToItsField() }

    @Test
    fun delegates_union_062() = MutFlow.underTest { valueClassSourceTests.generatedFieldAnnotationsPreserveSignednessOnlyForSignedFields() }

    @Test
    fun delegates_union_063() = MutFlow.underTest { valueClassSourceTests.commonEncoderSignExtendsNarrowSignedLongHolderFields() }

    @Test
    fun delegates_union_064() = MutFlow.underTest { valueHolderSourceTests.commonEncoderCoversScalarKindsSignedWidthsAndReservedBits() }

    @Test
    fun delegates_union_065() = MutFlow.underTest { valueHolderSourceTests.generatedHolderConsumesOnlyRealGapsAndTheExactDeclaredFrame() }

    @Test
    fun delegates_union_066() = MutFlow.underTest { valueHolderSourceTests.decodeCommitsSignedValuesAndEncodeValidatesBeforeWritingReservedBits() }
}
