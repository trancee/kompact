//[kompact](../../../index.md)/[ch.trancee.kompact](../index.md)/[Kompact](index.md)

# Kompact

[common]\
object [Kompact](index.md)

Top-level namespace for the Kompact runtime.

`Result` re-exports the five specialized typed-result value classes under one import path — `Kompact.Result.Int`, `Kompact.Result.Boolean`, … — so a newcomer can `import ch.trancee.kompact.Kompact` instead of naming all five result types. There is no `Byte`/`Short` result type — 8- and 16-bit reads are decoded by `readScalar`, which returns `IntResult` (width and signedness come from `ScalarType`). Each result is a zero-alloc `@JvmInline` over a packed `Long` on success and failure; see `architecture.md` § &quot;Zero-allocation reads&quot; and `api-reference.md` § &quot;Typed result value classes&quot;.

See `KompactResult.kt` for the packed-Long encodings of each result kind.

## Types

| Name | Summary |
|---|---|
| [Result](-result/index.md) | [common]<br>object [Result](-result/index.md) |