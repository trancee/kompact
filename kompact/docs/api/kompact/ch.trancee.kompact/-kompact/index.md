//[kompact](../../../index.md)/[ch.trancee.kompact](../index.md)/[Kompact](index.md)

# Kompact

[common]\
object [Kompact](index.md)

Top-level namespace for the Kompact runtime.

`Result` re-exports the seven specialized typed-result value classes under one import path — `Kompact.Result.Int`, `Kompact.Result.Boolean`, … — so a newcomer can `import ch.trancee.kompact.Kompact` instead of naming all seven result types. The seven top-level declarations stay; this is purely a one-stop re-export (additive; zero-alloc on the success path).

See `KompactResult.kt` for the packed-Long encodings of each result kind.

## Types

| Name | Summary |
|---|---|
| [Result](-result/index.md) | [common]<br>object [Result](-result/index.md) |