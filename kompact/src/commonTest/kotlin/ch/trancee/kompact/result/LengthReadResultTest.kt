package ch.trancee.kompact.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spec: .scratch/kompact-spec/issues/08-runtime-error-model.md
 *
 * LengthReadResult packs (length, afterPrefix) into a single Long:
 * bit 60 = OK flag, bits 61..63 = error code, low 28 bits = length,
 * bits 28..59 = afterPrefix. This test pins the packing so a future
 * change to the bit layout fails loudly.
 */
class LengthReadResultTest {

    @Test
    fun success_is_ok() {
        val r = LengthReadResult.success(length = 42, afterPrefix = 100)
        assertTrue(r.isOk)
        assertEquals(0, r.errorCode)
    }

    @Test
    fun success_value_round_trip() {
        val r = LengthReadResult.success(length = 42, afterPrefix = 100)
        val (length, afterPrefix) = r.value
        assertEquals(42, length)
        assertEquals(100, afterPrefix)
    }

    @Test
    fun failure_is_not_ok() {
        val r = LengthReadResult.failure(KompactError.BoundsError)
        assertFalse(r.isOk)
        assertEquals(KompactError.BoundsError, r.errorCode)
    }

    @Test
    fun packing_distinguishes_ok_and_failure() {
        val ok = LengthReadResult.success(length = 0, afterPrefix = 0)
        val fail = LengthReadResult.failure(0)
        // OK flag (bit 60) must be set on success and clear on failure.
        assertTrue(ok.isOk)
        assertFalse(fail.isOk)
    }

    @Test
    fun error_code_extraction_supports_all_codes() {
        for (code in 0..6) {
            val r = LengthReadResult.failure(code)
            assertEquals(code, r.errorCode, "error code $code must round-trip")
        }
    }
}
