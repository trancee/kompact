package ch.trancee.kompact.ksp.gen

// Compiled into the test source set only for the opt-in mutation task.
import io.github.anschnapp.mutflow.MutFlow
import io.github.anschnapp.mutflow.junit.MutFlowTest
import kotlin.test.Test

@MutFlowTest
class ValueClassGeneratorMutationTest {
    private val sourceTests = ValueClassGeneratorTest()
    private val edgeCaseTests = ValueClassGeneratorEdgeCaseTest()
    private val boundaryTests = ValueClassGeneratorBoundaryTest()

    @Test
    fun viewIsARegularCommonJvmInlineValueClassNamedAfterTheSchema() = MutFlow.underTest { sourceTests.`view is a regular common JvmInline value class named after the schema`() }

    @Test
    fun viewCompanionCreateFactoryDelegatesToEncodeFunction() = MutFlow.underTest { sourceTests.`view companion create factory delegates to encode function`() }

    @Test
    fun encodeFunctionWritesEachFieldAtItsDeclaredOffset() = MutFlow.underTest { sourceTests.`encode function writes each field at its declared offset`() }

    @Test
    fun viewFileEmitsReusableHolderCursorOperations() = MutFlow.underTest { sourceTests.`view file emits reusable holder cursor operations`() }

    @Test
    fun defaultViewIsImmutableValNoWriteThroughSetter() = MutFlow.underTest { sourceTests.`default view is immutable (val, no write-through setter)`() }

    @Test
    fun defaultViewCopyDefaultsEveryFieldAndDelegatesToEncode() = MutFlow.underTest { sourceTests.`default view copy defaults every field and delegates to encode`() }

    @Test
    fun generateWithMutableModelEmitsMutableSiblingWithWriteThroughVarSetters() = MutFlow.underTest { sourceTests.`generate with mutable model emits Mutable sibling with write-through var setters`() }

    @Test
    fun viewUsesRawReadBitsForIntFields() = MutFlow.underTest { sourceTests.`view uses raw readBits for Int fields`() }

    @Test
    fun signedIntGetterSignExtendsItsDeclaredBitWidth() = MutFlow.underTest { sourceTests.`signed Int getter sign extends its declared bit width`() }

    @Test
    fun signedLongGetterSignExtendsItsDeclaredBitWidth() = MutFlow.underTest { sourceTests.`signed Long getter sign extends its declared bit width`() }

    @Test
    fun createEncoderWritesFieldsAtDeclaredOffsetsIncludingGaps() = MutFlow.underTest { sourceTests.`create encoder writes fields at declared offsets including gaps`() }

    @Test
    fun viewUsesReadBitsBooleanForBooleanFields() = MutFlow.underTest { sourceTests.`view uses readBitsBoolean for Boolean fields`() }

    @Test
    fun longFieldUsesReadBitsLong() = MutFlow.underTest { sourceTests.`Long field uses readBitsLong`() }

    @Test
    fun floatFieldUsesFloatFromBits() = MutFlow.underTest { sourceTests.`Float field uses Float fromBits`() }

    @Test
    fun doubleFieldUsesDoubleFromBits() = MutFlow.underTest { sourceTests.`Double field uses Double fromBits`() }

    @Test
    fun viewEmitsRawAsAConstructorBackingValDefect3() = MutFlow.underTest { sourceTests.`view emits raw as a constructor backing val (defect #3)`() }

    @Test
    fun fixedLayoutStringFieldSuggestsFramedMode() = MutFlow.underTest { edgeCaseTests.fixedLayoutStringFieldSuggestsFramedMode() }

    @Test
    fun invalidLayoutThrowsBeforeGeneration() = MutFlow.underTest { edgeCaseTests.`invalid layout throws before generation`() }

    @Test
    fun framedModelsDelegateToTheFramedViewGenerator() = MutFlow.underTest { edgeCaseTests.`framed models delegate to the framed view generator`() }

    @Test
    fun signedIntEncoderWritesItsLowBitsDirectlyAtTheDeclaredOffset() = MutFlow.underTest { edgeCaseTests.`signed Int encoder writes its low bits directly at the declared offset`() }

    @Test
    fun f001InitGuardUsesCorrectMinBufferSize() = MutFlow.underTest { edgeCaseTests.`F-001 init guard uses correct min buffer size`() }

    @Test
    fun fixedLayoutByteArrayFieldDirectsCallersToFramedMode() = MutFlow.underTest { edgeCaseTests.`fixed-layout ByteArray field directs callers to framed mode`() }

    @Test
    fun unknownTypeFailsGenerationWithDescriptiveError() = MutFlow.underTest { edgeCaseTests.`unknown type fails generation with descriptive error`() }

    @Test
    fun modelWithNoFieldsGeneratesAViewWithoutACreateFactory() = MutFlow.underTest { edgeCaseTests.`model with no fields generates a view without a create factory`() }

    @Test
    fun signed31BitIntGetterPreservesItsSignBit() = MutFlow.underTest { boundaryTests.signed31BitIntGetterPreservesItsSignBit() }

    @Test
    fun signed63BitLongGetterPreservesItsSignBit() = MutFlow.underTest { boundaryTests.signed63BitLongGetterPreservesItsSignBit() }

    @Test
    fun mutableSiblingDoesNotExposeImmutableCopyBuilder() = MutFlow.underTest { sourceTests.mutableSiblingDoesNotExposeImmutableCopyBuilder() }

    @Test
    fun generatedFactoryEncoderAndCopyPreserveEveryField() = MutFlow.underTest { sourceTests.generatedFactoryEncoderAndCopyPreserveEveryField() }

    @Test
    fun generatedSignednessAnnotationRemainsAttachedToItsField() = MutFlow.underTest { sourceTests.generatedSignednessAnnotationRemainsAttachedToItsField() }

    @Test
    fun generatedFieldAnnotationsPreserveSignednessOnlyForSignedFields() = MutFlow.underTest { sourceTests.generatedFieldAnnotationsPreserveSignednessOnlyForSignedFields() }

    @Test
    fun commonEncoderSignExtendsNarrowSignedLongHolderFields() = MutFlow.underTest { sourceTests.commonEncoderSignExtendsNarrowSignedLongHolderFields() }

}
