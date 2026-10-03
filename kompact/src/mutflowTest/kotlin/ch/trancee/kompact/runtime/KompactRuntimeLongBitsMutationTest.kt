package ch.trancee.kompact.runtime

import io.github.anschnapp.mutflow.MutFlow
import kotlin.test.Test

class KompactRuntimeLongBitsMutationTest {
    private val commonTests = KompactRuntimeLongBitsTest()

    @Test
    fun readBitsLong_singleByte() = MutFlow.underTest { commonTests.readBitsLong_singleByte() }

    @Test
    fun readBitsLong_masksSignedBytesLsbFirst() =
        MutFlow.underTest { commonTests.readBitsLong_masksSignedBytesLsbFirst() }

    @Test
    fun readBitsLong_crossByteBoundary() = MutFlow.underTest { commonTests.readBitsLong_crossByteBoundary() }

    @Test
    fun readBitsLong_assemblesUpTo64Bits_allOnes() =
        MutFlow.underTest { commonTests.readBitsLong_assemblesUpTo64Bits_allOnes() }

    @Test
    fun readBitsLong_assembles63Bits() = MutFlow.underTest { commonTests.readBitsLong_assembles63Bits() }

    @Test
    fun readBitsLong_singleBit_atByteBoundary() =
        MutFlow.underTest { commonTests.readBitsLong_singleBit_atByteBoundary() }

    @Test
    fun readBitsLong_subByteWidth_returnsUnsignedMagnitude() =
        MutFlow.underTest { commonTests.readBitsLong_subByteWidth_returnsUnsignedMagnitude() }

    @Test
    fun writeBitsLong_thenReadLong_singleByte() =
        MutFlow.underTest { commonTests.writeBitsLong_thenReadLong_singleByte() }

    @Test
    fun writeBitsLong_thenReadLong_crossByte() =
        MutFlow.underTest { commonTests.writeBitsLong_thenReadLong_crossByte() }

    @Test
    fun writeBitsLong_thenReadLong_64BitAllOnes() =
        MutFlow.underTest { commonTests.writeBitsLong_thenReadLong_64BitAllOnes() }

    @Test
    fun writeBitsLong_thenReadLong_largePositive() =
        MutFlow.underTest { commonTests.writeBitsLong_thenReadLong_largePositive() }

    @Test
    fun writeBitsLong_thenReadLong_32BitWriteDoesNotAffectUpperBits() =
        MutFlow.underTest { commonTests.writeBitsLong_thenReadLong_32BitWriteDoesNotAffectUpperBits() }

    @Test
    fun writeBitsLong_doesNotClobberOtherBits() =
        MutFlow.underTest { commonTests.writeBitsLong_doesNotClobberOtherBits() }
}
