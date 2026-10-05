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
    fun delegates_01() = MutFlow.underTest { sourceTests.`expect value class declares expect keyword`() }

    @Test
    fun delegates_02() = MutFlow.underTest { sourceTests.`expect value class declares companion create factory`() }

    @Test
    fun delegates_03() = MutFlow.underTest { sourceTests.`encode function writes each field at its declared offset`() }

    @Test
    fun delegates_04() = MutFlow.underTest { sourceTests.`common encoder emits reusable holder cursor operations`() }

    @Test
    fun delegates_05() = MutFlow.underTest { sourceTests.`jvm actual has JvmInline annotation`() }

    @Test
    fun delegates_06() = MutFlow.underTest { sourceTests.`jvm actual companion object is marked actual`() }

    @Test
    fun delegates_07() = MutFlow.underTest { sourceTests.`ios actual companion object is marked actual`() }

    @Test
    fun delegates_08() = MutFlow.underTest { sourceTests.`jvm actual default view is immutable (val, no write-through setter)`() }

    @Test
    fun delegates_09() = MutFlow.underTest { sourceTests.`jvm actual default view emits copy builder delegating to encode`() }

    @Test
    fun delegates_10() = MutFlow.underTest { sourceTests.`expect default view copy carries field defaults for callers`() }

    @Test
    fun delegates_11() = MutFlow.underTest { sourceTests.`generate with mutable model emits Mutable sibling with write-through var setters`() }

    @Test
    fun delegates_12() = MutFlow.underTest { sourceTests.`jvm actual uses raw readBits for Int fields`() }

    @Test
    fun delegates_13() = MutFlow.underTest { sourceTests.`signed Int getter sign extends its declared bit width`() }

    @Test
    fun delegates_14() = MutFlow.underTest { sourceTests.`signed Long getter sign extends its declared bit width`() }

    @Test
    fun delegates_15() = MutFlow.underTest { sourceTests.`create encoder writes fields at declared offsets including gaps`() }

    @Test
    fun delegates_16() = MutFlow.underTest { sourceTests.`jvm actual uses readBitsBoolean for Boolean fields`() }

    @Test
    fun delegates_17() = MutFlow.underTest { sourceTests.`jvm actual create delegates to encode function`() }

    @Test
    fun delegates_18() = MutFlow.underTest { sourceTests.`ios actual does NOT have JvmInline`() }

    @Test
    fun delegates_19() = MutFlow.underTest { sourceTests.`ios actual uses raw readBits`() }

    @Test
    fun delegates_20() = MutFlow.underTest { sourceTests.`Long field uses readBitsLong`() }

    @Test
    fun delegates_21() = MutFlow.underTest { sourceTests.`Float field uses Float fromBits`() }

    @Test
    fun delegates_22() = MutFlow.underTest { sourceTests.`Double field uses Double fromBits`() }

    @Test
    fun delegates_23() = MutFlow.underTest { sourceTests.`jvm actual emits raw as a constructor backing val (defect #3)`() }

    @Test
    fun delegates_24() = MutFlow.underTest { sourceTests.`ios actual emits raw as a constructor backing val (defect #3)`() }

    @Test
    fun delegates_25() = MutFlow.underTest { sourceTests.`expect emits raw without the invalid actual modifier (defect #3)`() }
    @Test
    fun delegates_26() = MutFlow.underTest { edgeCaseTests.fixedLayoutStringFieldSuggestsFramedMode() }

    @Test
    fun delegates_27() = MutFlow.underTest { edgeCaseTests.`invalid layout throws before generation`() }

    @Test
    fun delegates_28() = MutFlow.underTest { edgeCaseTests.`common encoder rejects framed models`() }

    @Test
    fun delegates_29() = MutFlow.underTest { edgeCaseTests.`signed Int encoder writes its low bits directly at the declared offset`() }

    @Test
    fun delegates_30() = MutFlow.underTest { edgeCaseTests.`F-001 init guard uses correct min buffer size`() }

    @Test
    fun delegates_31() = MutFlow.underTest { edgeCaseTests.`fixed-layout ByteArray field directs callers to framed mode`() }

    @Test
    fun delegates_32() = MutFlow.underTest { edgeCaseTests.`unknown type fails generation with descriptive error`() }

    @Test
    fun delegates_33() = MutFlow.underTest { edgeCaseTests.`model with no fields generates valid expect`() }

    @Test
    fun delegates_34() = MutFlow.underTest { edgeCaseTests.`model with no fields generates valid jvm actual`() }

    @Test
    fun delegates_35() = MutFlow.underTest { edgeCaseTests.`fixed-layout expect encoder directs String fields to framed mode`() }

    @Test
    fun delegates_36() = MutFlow.underTest { edgeCaseTests.`fixed-layout expect encoder directs ByteArray fields to framed mode`() }

    @Test
    fun delegates_37() = MutFlow.underTest { edgeCaseTests.`expect with unknown type fails generation with descriptive error`() }

    @Test
    fun delegates_38() = MutFlow.underTest { boundaryTests.signed31BitIntGetterPreservesItsSignBit() }

    @Test
    fun delegates_39() = MutFlow.underTest { boundaryTests.signed63BitLongGetterPreservesItsSignBit() }

    @Test
    fun delegates_42() = MutFlow.underTest { sourceTests.mutableSiblingDoesNotExposeImmutableCopyBuilder() }

    @Test
    fun delegates_44() = MutFlow.underTest { sourceTests.generatedFactoryEncoderAndCopyPreserveEveryFieldAndActualBody() }

    @Test
    fun delegates_43() = MutFlow.underTest { sourceTests.generatedSignednessAnnotationRemainsAttachedToItsField() }

    @Test
    fun delegates_41() = MutFlow.underTest { sourceTests.generatedFieldAnnotationsPreserveSignednessOnlyForSignedFields() }

    @Test
    fun delegates_40() = MutFlow.underTest { sourceTests.commonEncoderSignExtendsNarrowSignedLongHolderFields() }

}
