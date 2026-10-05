package ch.trancee.kompact.ksp.gen

// Compiled into the test source set only for the opt-in mutation task.
import io.github.anschnapp.mutflow.MutFlow
import io.github.anschnapp.mutflow.junit.MutFlowTest
import kotlin.test.Test

@MutFlowTest
class ValueHolderGeneratorMutationTest {
    private val sourceTests = ValueHolderGeneratorTest()

    @Test
    fun commonEncoderCoversScalarKindsSignedWidthsAndReservedBits() = MutFlow.underTest { sourceTests.commonEncoderCoversScalarKindsSignedWidthsAndReservedBits() }

    @Test
    fun generatedHolderConsumesOnlyRealGapsAndTheExactDeclaredFrame() = MutFlow.underTest { sourceTests.generatedHolderConsumesOnlyRealGapsAndTheExactDeclaredFrame() }

    @Test
    fun decodeCommitsSignedValuesAndEncodeValidatesBeforeWritingReservedBits() = MutFlow.underTest { sourceTests.decodeCommitsSignedValuesAndEncodeValidatesBeforeWritingReservedBits() }
}
