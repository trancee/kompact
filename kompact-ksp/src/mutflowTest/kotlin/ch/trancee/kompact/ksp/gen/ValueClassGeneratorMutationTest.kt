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
    fun expectValueClassDeclaresExpectKeyword() = MutFlow.underTest { sourceTests.`expect value class declares expect keyword`() }

    @Test
    fun expectValueClassDeclaresCompanionCreateFactory() = MutFlow.underTest { sourceTests.`expect value class declares companion create factory`() }

    @Test
    fun encodeFunctionWritesEachFieldAtItsDeclaredOffset() = MutFlow.underTest { sourceTests.`encode function writes each field at its declared offset`() }

    @Test
    fun commonEncoderEmitsReusableHolderCursorOperations() = MutFlow.underTest { sourceTests.`common encoder emits reusable holder cursor operations`() }

    @Test
    fun jvmActualHasJvmInlineAnnotation() = MutFlow.underTest { sourceTests.`jvm actual has JvmInline annotation`() }

    @Test
    fun jvmActualCompanionObjectIsMarkedActual() = MutFlow.underTest { sourceTests.`jvm actual companion object is marked actual`() }

    @Test
    fun iosActualCompanionObjectIsMarkedActual() = MutFlow.underTest { sourceTests.`ios actual companion object is marked actual`() }

    @Test
    fun jvmActualDefaultViewIsImmutableValNoWriteThroughSetter() = MutFlow.underTest { sourceTests.`jvm actual default view is immutable (val, no write-through setter)`() }

    @Test
    fun jvmActualDefaultViewEmitsCopyBuilderDelegatingToEncode() = MutFlow.underTest { sourceTests.`jvm actual default view emits copy builder delegating to encode`() }

    @Test
    fun expectDefaultViewCopyCarriesFieldDefaultsForCallers() = MutFlow.underTest { sourceTests.`expect default view copy carries field defaults for callers`() }

    @Test
    fun generateWithMutableModelEmitsMutableSiblingWithWriteThroughVarSetters() = MutFlow.underTest { sourceTests.`generate with mutable model emits Mutable sibling with write-through var setters`() }

    @Test
    fun jvmActualUsesRawReadBitsForIntFields() = MutFlow.underTest { sourceTests.`jvm actual uses raw readBits for Int fields`() }

    @Test
    fun signedIntGetterSignExtendsItsDeclaredBitWidth() = MutFlow.underTest { sourceTests.`signed Int getter sign extends its declared bit width`() }

    @Test
    fun signedLongGetterSignExtendsItsDeclaredBitWidth() = MutFlow.underTest { sourceTests.`signed Long getter sign extends its declared bit width`() }

    @Test
    fun createEncoderWritesFieldsAtDeclaredOffsetsIncludingGaps() = MutFlow.underTest { sourceTests.`create encoder writes fields at declared offsets including gaps`() }

    @Test
    fun jvmActualUsesReadBitsBooleanForBooleanFields() = MutFlow.underTest { sourceTests.`jvm actual uses readBitsBoolean for Boolean fields`() }

    @Test
    fun jvmActualCreateDelegatesToEncodeFunction() = MutFlow.underTest { sourceTests.`jvm actual create delegates to encode function`() }

    @Test
    fun iosActualDoesNOTHaveJvmInline() = MutFlow.underTest { sourceTests.`ios actual does NOT have JvmInline`() }

    @Test
    fun iosActualUsesRawReadBits() = MutFlow.underTest { sourceTests.`ios actual uses raw readBits`() }

    @Test
    fun longFieldUsesReadBitsLong() = MutFlow.underTest { sourceTests.`Long field uses readBitsLong`() }

    @Test
    fun floatFieldUsesFloatFromBits() = MutFlow.underTest { sourceTests.`Float field uses Float fromBits`() }

    @Test
    fun doubleFieldUsesDoubleFromBits() = MutFlow.underTest { sourceTests.`Double field uses Double fromBits`() }

    @Test
    fun jvmActualEmitsRawAsAConstructorBackingValDefect3() = MutFlow.underTest { sourceTests.`jvm actual emits raw as a constructor backing val (defect #3)`() }

    @Test
    fun iosActualEmitsRawAsAConstructorBackingValDefect3() = MutFlow.underTest { sourceTests.`ios actual emits raw as a constructor backing val (defect #3)`() }

    @Test
    fun expectEmitsRawWithoutTheInvalidActualModifierDefect3() = MutFlow.underTest { sourceTests.`expect emits raw without the invalid actual modifier (defect #3)`() }
    @Test
    fun fixedLayoutStringFieldSuggestsFramedMode() = MutFlow.underTest { edgeCaseTests.fixedLayoutStringFieldSuggestsFramedMode() }

    @Test
    fun invalidLayoutThrowsBeforeGeneration() = MutFlow.underTest { edgeCaseTests.`invalid layout throws before generation`() }

    @Test
    fun commonEncoderRejectsFramedModels() = MutFlow.underTest { edgeCaseTests.`common encoder rejects framed models`() }

    @Test
    fun signedIntEncoderWritesItsLowBitsDirectlyAtTheDeclaredOffset() = MutFlow.underTest { edgeCaseTests.`signed Int encoder writes its low bits directly at the declared offset`() }

    @Test
    fun f001InitGuardUsesCorrectMinBufferSize() = MutFlow.underTest { edgeCaseTests.`F-001 init guard uses correct min buffer size`() }

    @Test
    fun fixedLayoutByteArrayFieldDirectsCallersToFramedMode() = MutFlow.underTest { edgeCaseTests.`fixed-layout ByteArray field directs callers to framed mode`() }

    @Test
    fun unknownTypeFailsGenerationWithDescriptiveError() = MutFlow.underTest { edgeCaseTests.`unknown type fails generation with descriptive error`() }

    @Test
    fun modelWithNoFieldsGeneratesValidExpect() = MutFlow.underTest { edgeCaseTests.`model with no fields generates valid expect`() }

    @Test
    fun modelWithNoFieldsGeneratesValidJvmActual() = MutFlow.underTest { edgeCaseTests.`model with no fields generates valid jvm actual`() }

    @Test
    fun fixedLayoutExpectEncoderDirectsStringFieldsToFramedMode() = MutFlow.underTest { edgeCaseTests.`fixed-layout expect encoder directs String fields to framed mode`() }

    @Test
    fun fixedLayoutExpectEncoderDirectsByteArrayFieldsToFramedMode() = MutFlow.underTest { edgeCaseTests.`fixed-layout expect encoder directs ByteArray fields to framed mode`() }

    @Test
    fun expectWithUnknownTypeFailsGenerationWithDescriptiveError() = MutFlow.underTest { edgeCaseTests.`expect with unknown type fails generation with descriptive error`() }

    @Test
    fun signed31BitIntGetterPreservesItsSignBit() = MutFlow.underTest { boundaryTests.signed31BitIntGetterPreservesItsSignBit() }

    @Test
    fun signed63BitLongGetterPreservesItsSignBit() = MutFlow.underTest { boundaryTests.signed63BitLongGetterPreservesItsSignBit() }

    @Test
    fun mutableSiblingDoesNotExposeImmutableCopyBuilder() = MutFlow.underTest { sourceTests.mutableSiblingDoesNotExposeImmutableCopyBuilder() }

    @Test
    fun generatedFactoryEncoderAndCopyPreserveEveryFieldAndActualBody() = MutFlow.underTest { sourceTests.generatedFactoryEncoderAndCopyPreserveEveryFieldAndActualBody() }

    @Test
    fun generatedSignednessAnnotationRemainsAttachedToItsField() = MutFlow.underTest { sourceTests.generatedSignednessAnnotationRemainsAttachedToItsField() }

    @Test
    fun generatedFieldAnnotationsPreserveSignednessOnlyForSignedFields() = MutFlow.underTest { sourceTests.generatedFieldAnnotationsPreserveSignednessOnlyForSignedFields() }

    @Test
    fun commonEncoderSignExtendsNarrowSignedLongHolderFields() = MutFlow.underTest { sourceTests.commonEncoderSignExtendsNarrowSignedLongHolderFields() }

}
