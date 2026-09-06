package ch.trancee.kompact

import ch.trancee.kompact.runtime.BooleanResult
import ch.trancee.kompact.runtime.ByteResult
import ch.trancee.kompact.runtime.DoubleResult
import ch.trancee.kompact.runtime.FloatResult
import ch.trancee.kompact.runtime.IntResult
import ch.trancee.kompact.runtime.LongResult
import ch.trancee.kompact.runtime.ShortResult

/**
 * Top-level namespace for the Kompact runtime.
 *
 * `Result` re-exports the seven specialized typed-result value classes
 * under one import path — `Kompact.Result.Int`, `Kompact.Result.Boolean`, … —
 * so a newcomer can `import ch.trancee.kompact.Kompact` instead of naming all
 * seven result types. The seven top-level declarations stay; this is purely
 * a one-stop re-export (additive; zero-alloc on the success path).
 *
 * See `KompactResult.kt` for the packed-Long encodings of each result kind.
 */
public object Kompact {

    public object Result {

        public typealias Byte = ByteResult

        public typealias Short = ShortResult

        public typealias Int = IntResult

        public typealias Long = LongResult

        public typealias Float = FloatResult

        public typealias Double = DoubleResult

        public typealias Boolean = BooleanResult
    }
}
