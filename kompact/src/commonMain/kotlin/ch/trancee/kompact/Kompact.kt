package ch.trancee.kompact

import ch.trancee.kompact.runtime.BooleanResult
import ch.trancee.kompact.runtime.DoubleResult
import ch.trancee.kompact.runtime.FloatResult
import ch.trancee.kompact.runtime.IntResult
import ch.trancee.kompact.runtime.LongResult

/**
 * Top-level namespace for the Kompact runtime.
 *
 * `Result` re-exports the five specialized typed-result value classes
 * under one import path — `Kompact.Result.Int`, `Kompact.Result.Boolean`, … —
 * so a newcomer can `import ch.trancee.kompact.Kompact` instead of naming all
 * five result types. Byte/Short are subsumed by `Int` (width resolved via
 * ScalarType at read time — readScalar already returns IntResult for 8/16-bit
 * fields); this is purely a one-stop re-export (additive; zero-alloc on the
 * success path).
 *
 * See `KompactResult.kt` for the packed-Long encodings of each result kind.
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
