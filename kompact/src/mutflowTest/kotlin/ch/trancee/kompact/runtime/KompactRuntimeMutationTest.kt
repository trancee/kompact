package ch.trancee.kompact.runtime

import io.github.anschnapp.mutflow.MutFlow
import kotlin.test.Test

class KompactRuntimeMutationTest {
    private val commonTests = KompactRuntimeTest()

    @Test
    fun readBits_lowNibbleOfSingleByte() = MutFlow.underTest { commonTests.readBits_lowNibbleOfSingleByte() }

    @Test
    fun readBits_highNibbleOfSingleByteClear() = MutFlow.underTest { commonTests.readBits_highNibbleOfSingleByteClear() }

    @Test
    fun readBits_highNibbleOfSingleByteSet() = MutFlow.underTest { commonTests.readBits_highNibbleOfSingleByteSet() }

    @Test
    fun readBits_masksSignedBytesLsbFirst() = MutFlow.underTest { commonTests.readBits_masksSignedBytesLsbFirst() }

    @Test
    fun readBits_assemblesLeastSignificantBitsFirst() =
        MutFlow.underTest { commonTests.readBits_assemblesLeastSignificantBitsFirst() }

    @Test
    fun readBits_crossByteBoundary() = MutFlow.underTest { commonTests.readBits_crossByteBoundary() }

    @Test
    fun readBits_allOnesCrossByteMax() = MutFlow.underTest { commonTests.readBits_allOnesCrossByteMax() }

    @Test
    fun readBits_widthOneAtBit7ReturnsSetBit() =
        MutFlow.underTest { commonTests.readBits_widthOneAtBit7ReturnsSetBit() }

    @Test
    fun readBitsBoolean_trueWhenSet() = MutFlow.underTest { commonTests.readBitsBoolean_trueWhenSet() }

    @Test
    fun readBitsBoolean_falseWhenClear() = MutFlow.underTest { commonTests.readBitsBoolean_falseWhenClear() }

    @Test
    fun readBitsBoolean_falseOnZeroByte() = MutFlow.underTest { commonTests.readBitsBoolean_falseOnZeroByte() }

    @Test
    fun readBitsBoolean_bit7() = MutFlow.underTest { commonTests.readBitsBoolean_bit7() }

    @Test
    fun writeBits_thenReadBits_singleByte() = MutFlow.underTest { commonTests.writeBits_thenReadBits_singleByte() }

    @Test
    fun writeBits_thenReadBits_crossByte() = MutFlow.underTest { commonTests.writeBits_thenReadBits_crossByte() }

    @Test
    fun writeBits_overwritesExistingBitsLowNibble() =
        MutFlow.underTest { commonTests.writeBits_overwritesExistingBitsLowNibble() }

    @Test
    fun writeBits_overwritesExistingBitsHighNibble() =
        MutFlow.underTest { commonTests.writeBits_overwritesExistingBitsHighNibble() }

    @Test
    fun writeBitsBoolean_setsBit() = MutFlow.underTest { commonTests.writeBitsBoolean_setsBit() }

    @Test
    fun writeBitsBoolean_clearsBit() = MutFlow.underTest { commonTests.writeBitsBoolean_clearsBit() }

    @Test
    fun writeBits_doesNotClobberOtherBits() = MutFlow.underTest { commonTests.writeBits_doesNotClobberOtherBits() }

    @Test
    fun writeBits_widthOneAtBit7_setsBitAndPreservesNeighbors() =
        MutFlow.underTest { commonTests.writeBits_widthOneAtBit7_setsBitAndPreservesNeighbors() }

    @Test
    fun writeBits_widthOneAtBit7_clearsBitAndPreservesNeighbors() =
        MutFlow.underTest { commonTests.writeBits_widthOneAtBit7_clearsBitAndPreservesNeighbors() }
}
