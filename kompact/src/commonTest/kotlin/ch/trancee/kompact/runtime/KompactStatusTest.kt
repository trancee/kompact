package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class KompactStatusTest {
    @Test
    fun malformedInputStatusesMapToSharedDecodeErrors() {
        assertSame(KompactDecodeError.BoundsError, KompactStatus.BOUNDS_ERROR.decodeError)
        assertSame(KompactDecodeError.BadLengthPrefix, KompactStatus.BAD_LENGTH_PREFIX.decodeError)
        assertSame(KompactDecodeError.InvalidUtf8, KompactStatus.INVALID_UTF8.decodeError)
        assertSame(KompactDecodeError.TruncatedNested, KompactStatus.TRUNCATED_INPUT.decodeError)
    }

    @Test
    fun successAndCallerContractStatusesHaveNoDecodeError() {
        val withoutDecodeError =
            listOf(
                KompactStatus.OK,
                KompactStatus.INVALID_BOUNDS,
                KompactStatus.INVALID_WIDTH,
                KompactStatus.INVALID_VALUE,
                KompactStatus.UNALIGNED_REGION,
                KompactStatus.INVALID_ARGUMENT,
                KompactStatus.WORKSPACE_TOO_SMALL,
            )

        withoutDecodeError.forEach { status -> assertNull(status.decodeError, status.name) }
        assertEquals(11, KompactStatus.entries.size)
    }

    @Test
    fun cursorOperationsReportTypedStatus() {
        val cursor = KompactCursor(ByteArray(1))

        assertSame(KompactStatus.OK, cursor.status)
        assertSame(KompactStatus.INVALID_WIDTH, cursor.readBits(0))
        assertSame(KompactStatus.INVALID_WIDTH, cursor.status)
        assertSame(KompactStatus.BOUNDS_ERROR, cursor.readBits(9))
        assertSame(KompactDecodeError.BoundsError, cursor.status.decodeError)
        assertSame(KompactStatus.OK, cursor.readBits(8))
    }
}
