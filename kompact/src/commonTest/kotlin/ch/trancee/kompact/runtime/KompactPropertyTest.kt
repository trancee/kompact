package ch.trancee.kompact.runtime

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Spec: .scratch/kompact-spec/issues/10-cross-platform-testing-model.md
 *
 * Property-based tests: random bit-widths / values / bit-offsets are
 * round-tripped through `KompactRuntime.readBits` / `writeBits` and
 * verified to be lossless. A small seed-based PRNG replaces a heavy
 * property-testing library to keep dependencies minimal.
 */
class KompactPropertyTest {

    @Test
    fun roundtrip_random_uint8() {
        val rng = Random(seed = 0x1234)
        repeat(1000) {
            val value = rng.nextBits(8)
            val bitOffset = rng.nextInt(0, 1024)
            val buf = ByteArray(256)
            KompactRuntime.writeBits(buf, bitOffset, 8, value.toLong())
            val read = KompactRuntime.readBits(buf, bitOffset, 8)
            assertEquals(value, read, "roundtrip failed for value=$value offset=$bitOffset")
        }
    }

    @Test
    fun roundtrip_random_uint16_cross_byte() {
        val rng = Random(seed = 0x5678)
        repeat(1000) {
            val value = rng.nextBits(16)
            val bitOffset = rng.nextInt(0, 1000)
            val buf = ByteArray(256)
            KompactRuntime.writeBits(buf, bitOffset, 16, value.toLong())
            val read = KompactRuntime.readBits(buf, bitOffset, 16)
            assertEquals(value, read, "roundtrip failed for value=$value offset=$bitOffset")
        }
    }

    @Test
    fun roundtrip_random_uint32() {
        val rng = Random(seed = 0x9ABC)
        repeat(1000) {
            val value = rng.nextInt()
            val bitOffset = rng.nextInt(0, 900)
            val buf = ByteArray(256)
            KompactRuntime.writeBits(buf, bitOffset, 32, value.toLong())
            val read = KompactRuntime.readBits(buf, bitOffset, 32)
            assertEquals(value, read, "roundtrip failed for value=$value offset=$bitOffset")
        }
    }

    @Test
    fun roundtrip_random_uint64() {
        val rng = Random(seed = 0xDEF0)
        repeat(500) {
            val value = rng.nextLong()
            val bitOffset = rng.nextInt(0, 800)
            val buf = ByteArray(256)
            KompactRuntime.writeBits(buf, bitOffset, 64, value)
            val read = KompactRuntime.readBitsLong(buf, bitOffset, 64)
            assertEquals(value, read, "roundtrip failed for value=$value offset=$bitOffset")
        }
    }

    @Test
    fun writeBits_does_not_corrupt_adjacent_field() {
        val rng = Random(seed = 0xBEEF)
        repeat(500) {
            val a = rng.nextBits(4)
            val b = rng.nextBits(4)
            val buf = ByteArray(1)
            KompactRuntime.writeBits(buf, 0, 4, a.toLong())
            KompactRuntime.writeBits(buf, 4, 4, b.toLong())
            val ra = KompactRuntime.readBits(buf, 0, 4)
            val rb = KompactRuntime.readBits(buf, 4, 4)
            assertEquals(a, ra, "adjacent field A corrupted")
            assertEquals(b, rb, "adjacent field B corrupted")
        }
    }

    @Test
    fun mask_boundary_at_byte_8() {
        val rng = Random(seed = 0xCAFE)
        repeat(500) {
            val value = rng.nextBits(10)
            val buf = ByteArray(4)
            KompactRuntime.writeBits(buf, 4, 10, value.toLong())
            val read = KompactRuntime.readBits(buf, 4, 10)
            assertEquals(value, read, "cross-boundary roundtrip failed value=$value")
        }
    }
}
