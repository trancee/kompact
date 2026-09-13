# Code Generation Strategy for Kompact Value-Class Getters

**Recommendation:** Use KSP with Kotlin 1.9+ for incremental, deterministic generation of complete `value class` declarations in commonMain, targeting Android/JVM and iOS Native.

## Key Evidence

1. **KSP supports commonMain generation** — KSP generates Kotlin source files into `build/generated/ksp/commonMain/kotlin`, compiled for all targets (JVM, Android, iOS). Generated code is fully IDE-visible with navigation, refactoring, and autocomplete support via Gradle source-set inclusion.

2. **KSP incremental processing provides deterministic output** (KSP 2.3.9+) — Per the incremental processing spec, KSP tracks dependencies via resolution tracing and input-output correspondence, ensuring minimal rebuilds with Gradle build-cache reuse. The dirtiness propagation rules guarantee identical outputs for unchanged inputs.

3. **K2 compiler macros are experimental** — Kotlin 2.2+ macros are explicitly marked experimental, require opt-in flags (`@OptIn(kotlin.experimental.macros.MacroApi::class)`), and are not production-ready for KMP libraries targeting multiple platforms.

## Critical Boundary

**KSP generates entire value-class source files, NOT property implementations for existing declarations** — KSP cannot modify existing Kotlin files. Therefore, the generator must produce complete `value class Foo(val raw: ByteArray) { @KompactField... val x: Int get() = ... }` declarations in commonMain using Kotlin 1.9+`value class` syntax (without `@JvmInline`). The `@JvmInline` annotation is JVM-specific and unavailable in commonMain, but Kotlin 1.9+ value classes work correctly on all platforms without it.

Per the accepted user constraint: generated `actual` value classes MAY carry `@JvmInline` on JVM targets; the PROMPT §3 prohibition applies to hand-written common API only.

## Sources

- Kotlin Symbol Processing with Kotlin Multiplatform — https://kotlinlang.org/docs/ksp-multiplatform.html  
- KSP Incremental Processing — https://kotlinlang.org/docs/ksp-incremental.html  
- Kotlin Symbol Processing API Overview — https://kotlinlang.org/docs/ksp-overview.html  
- KSP FAQ — https://kotlinlang.org/docs/ksp-faq.html  
- What's new in Kotlin 2.2.20 — https://kotlinlang.org/docs/whatsnew2220.html (macros stability)