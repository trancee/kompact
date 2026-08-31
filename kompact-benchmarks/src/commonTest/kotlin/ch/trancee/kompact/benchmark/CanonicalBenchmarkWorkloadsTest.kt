package ch.trancee.kompact.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals

class CanonicalBenchmarkWorkloadsTest {
    @Test
    fun retainedWorkloadsHaveExactPacketSizesAndReferenceResults() {
        assertEquals(4, CanonicalBenchmarkWorkloads.small.size)
        assertEquals(32, CanonicalBenchmarkWorkloads.medium.size)
        assertEquals(244, CanonicalBenchmarkWorkloads.large.size)

        assertEquals(
            HandwrittenReference.readUnsigned(CanonicalBenchmarkWorkloads.small, 20, 10),
            CanonicalBenchmarkWorkloads.readSmallSpeed(),
        )
        assertEquals(
            HandwrittenReference.readUnsigned(CanonicalBenchmarkWorkloads.medium, 67, 17),
            CanonicalBenchmarkWorkloads.readMediumUnaligned(),
        )
        assertEquals(
            HandwrittenReference.readUnsigned(CanonicalBenchmarkWorkloads.large, 1531, 13),
            CanonicalBenchmarkWorkloads.readLargeNestedElement(),
        )
    }
}
