# Value Class Representation: Expect/Actual Patterns for Kompact

## Recommendation
Use `expect` without @JvmInline in commonMain, with `@JvmInline actual value class` declarations in each platform source set (jvmMain, iosArm64Main, iosSimulatorArm64Main).

## Key Evidence

### 1. @JvmInline Multiplatform Support Status
Kotlin 2.6 still requires `@JvmInline` on the literal declaration (cannot be hidden via expect/actual annotation class). The annotation exists only in `kotlin-stdlib-jvm` for value class compilation; other platforms (JS, WASM, Native) lack it in their stdlibs. Common code cannot declare value classes directly.

> "In Kotlin 2.6 you can't place a value class directly in a common source set because the `@JvmInline` annotation that makes a class a value class exists only in the JVM-specific stdlib" — [Source: Medium article on expect/actual patterns]

### 2. Kotlin/Native Value Class Representation
Kotlin/Native compiles value classes as Swift structs, passed by value. Boxing (wrapper allocation) occurs only at type-erasure boundaries:
- Generic type arguments
- Nullable types (`Foo?`)
- Interface/Any-typed parameters
- Return values crossing ABI boundaries

> "On the iOS side they appear as plain Swift structs containing the same single field... boxing only occurs when the Kotlin type is used in a context that requires type erasure" — [Source: TypeAlias guide]

## Exact Call-Shape Boundaries (Hot Path)

**Unboxed (zero-cost):**
- Direct calls: `fun process(id: LocalId)` where `LocalId` is the actual value class
- Non-nullable, non-generic usage
- Platform-specific APIs

**Boxing (allocation):**
- Generic calls: `fun <T> process(x: T)` 
- Interface calls: `fun process(id: Displayable)`
- Nullable calls: `fun process(id: LocalId?)`
- Java interop (calls through erasure)

## Caveat for Spec
The `expect` class in commonMain must NOT carry `@JvmInline` (it's meaningless there and causes compilation errors on non-JVM platforms). Each platform's `actual` MUST be a value class with `@JvmInline`, and the underlying type must be consistent (ByteArray) for ABI compatibility across expect/actual projections.

## Sources
- Kotlin 2.6 multiplatform value class limitation: https://medium.com/@KaushalVasava/expect-and-actual-functions-in-kotlin-for-kotlin-multi-platform-19a3ba08d4c4e
- Kotlin inline classes documentation: https://kotlinlang.org/docs/inline-classes.html
- TypeAlias guide on autoboxing: https://typealias.com/guides/inline-classes-and-autoboxing