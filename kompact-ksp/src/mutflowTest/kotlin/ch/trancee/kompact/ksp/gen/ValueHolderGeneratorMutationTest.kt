package ch.trancee.kompact.ksp.gen

// Compiled into the test source set only for the opt-in mutation task.
import io.github.anschnapp.mutflow.MutFlow
import io.github.anschnapp.mutflow.junit.MutFlowTest
import kotlin.test.Test

@MutFlowTest
class ValueHolderGeneratorMutationTest {
    private val sourceTests = ValueHolderGeneratorTest()

    @Test
    fun delegates_01() = MutFlow.underTest { sourceTests.commonEncoderCoversScalarKindsSignedWidthsAndReservedBits() }

    @Test
    fun delegates_03() = MutFlow.underTest { sourceTests.generatedHolderConsumesOnlyRealGapsAndTheExactDeclaredFrame() }

    @Test
    fun delegates_02() = MutFlow.underTest { sourceTests.decodeCommitsSignedValuesAndEncodeValidatesBeforeWritingReservedBits() }
}
