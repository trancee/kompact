package ch.trancee.kompact

import ch.trancee.kompact.runtime.BooleanResult
import ch.trancee.kompact.runtime.ByteResult
import ch.trancee.kompact.runtime.DoubleResult
import ch.trancee.kompact.runtime.FloatResult
import ch.trancee.kompact.runtime.IntResult
import ch.trancee.kompact.runtime.LongResult
import ch.trancee.kompact.runtime.ShortResult
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
        // Every Kompact.Result.* alias must resolve to its specialized result
        // value class and round-trip a typed value. Pinned here so a refactor
        // that drops or mis-binds any alias (api-reference.md lists seven)
        // breaks the build — typealiases are erased at runtime, so usage is the
        // only proof that the alias resolves to the right result shape.
        val byteResult: Kompact.Result.Byte = ByteResult.success(42)
        assertEquals(42, byteResult.getOrThrow())

        val shortResult: Kompact.Result.Short = ShortResult.success(1000)
        assertEquals(1000, shortResult.getOrThrow())

        val intResult: Kompact.Result.Int = IntResult.success(7)
        assertEquals(7, intResult.getOrThrow())

        val longResult: Kompact.Result.Long = LongResult.success(42L)
        assertEquals(42L, longResult.getOrThrow())

        val floatResult: Kompact.Result.Float = FloatResult.success(3.14f)
        assertEquals(3.14f, floatResult.getOrThrow(), 0.0001f)

        val doubleResult: Kompact.Result.Double = DoubleResult.success(3.14)
        assertEquals(3.14, doubleResult.getOrThrow(), 0.0001)

        val booleanResult: Kompact.Result.Boolean = BooleanResult.success(true)
        assertTrue(booleanResult.getOrThrow())

        // The failure alias path must resolve too — carries a typed error.
        val failed: Kompact.Result.Int =
            IntResult.failure(ch.trancee.kompact.runtime.KompactDecodeError.BoundsError)
        assertTrue(failed.isFailure)
    }
}
