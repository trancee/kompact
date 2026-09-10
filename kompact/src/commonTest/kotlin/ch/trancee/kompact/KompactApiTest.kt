package ch.trancee.kompact

import ch.trancee.kompact.runtime.ByteResult
import ch.trancee.kompact.runtime.IntResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that the top-level [Kompact] namespace object and its `Result`
 * typealias group initialize and resolve (Ticket 08: the re-export namespace
 * was never accessed in tests, so the class-init and typealias bindings were
 * uncovered on the JVM).
 */
class KompactApiTest {
    @Test
    fun kompactObjectsInitialize() {
        // Accessing the object singletons triggers <clinit> on both Kompact
        // and Kompact.Result. Typealiases are erased at runtime, so merely
        // using Kompact.Result.Byte as a type does not trigger initialization;
        // we must read the object reference itself.
        val kompactRef: Kompact = Kompact
        val resultRef: Kompact.Result = Kompact.Result

        // Use the references so the compiler doesn't optimize them away.
        assertEquals("Kompact", kompactRef::class.simpleName)
        assertEquals("Result", resultRef::class.simpleName)
    }

    @Test
    fun kompactResultTypeAliases_resolveToResultTypes() {
        // Type-driven use: Kompact.Result.Byte is a typealias for ByteResult.
        val byteResult: Kompact.Result.Byte = ByteResult.success(42)
        assertEquals(42, byteResult.getOrThrow())

        val intResult: Kompact.Result.Int = IntResult.failure(ch.trancee.kompact.runtime.KompactDecodeError.BoundsError)
        assertTrue(intResult.isFailure)
    }
}
