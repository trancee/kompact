package ch.trancee.kompact

import ch.trancee.kompact.runtime.BooleanResult
import ch.trancee.kompact.runtime.DoubleResult
import ch.trancee.kompact.runtime.FloatResult
import ch.trancee.kompact.runtime.IntResult
import ch.trancee.kompact.runtime.LongResult

/**
 * Top-level namespace for the Kompact runtime.
 *
 * `Result` re-exports the five specialized typed-result types
 * under one import path — `Kompact.Result.Int`, `Kompact.Result.Boolean`, … —
 * so a newcomer can `import ch.trancee.kompact.Kompact` instead of naming all
 * five result types. There is no `Byte`/`Short` result type — 8- and 16-bit
 * reads are decoded by `readScalar`, which returns `IntResult` (width and
 * signedness come from `ScalarType`). Four result types are zero-alloc packed
 * value classes; `LongResult` allocates to preserve the full signed domain.
 * See `architecture.md` § "Zero-allocation reads" and `api-reference.md` §
 * "Typed result value classes".
 *
 * See `KompactResult.kt` for the packed encodings and `LongResult` representation.
 */
public object Kompact {
    public object Result {
        public typealias Int = IntResult

        public typealias Long = LongResult

        public typealias Float = FloatResult

        public typealias Double = DoubleResult

        public typealias Boolean = BooleanResult
    }
}
