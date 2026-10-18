# ADR-0012 — Kotlin minor-line compatibility for the Gradle plugin

- **Status:** Accepted
- **Tags:** build, compatibility
- **Supersedes:** the exact-pair contract in
  [release-audit issue 16](../../.scratch/kompact-initial-release-audit/issues/16-gradle-plugin-compatibility-contract.md)

## Context

The `ch.trancee.kompact.codegen` plugin runs KSP2 in an isolated class loader.
That engine uses the Kotlin and KSP versions the plugin was built with, not
the consumer's Kotlin Gradle Plugin (KGP). Before this decision the plugin
failed the build unless the consumer's KGP version matched the tested version
exactly. Any KGP patch release therefore broke code generation, even though
Kotlin keeps metadata and language versions stable within a minor line
(`2.4.x`).

## Decision

1. If the detected KGP version equals the tested version, the plugin
   proceeds silently.
2. If it is another release of the same `major.minor` line, including
   pre-releases such as `2.4.30-RC`, the plugin proceeds and logs a warning
   naming both versions.
3. If the minor line differs, or the version cannot be detected, the plugin
   still fails the build with `Kompact code generation is incompatible`
   before any generation runs.
4. The reflective KSP2 entry point (`KSPLoader.loadAndRunKSP`) keeps its
   existing diagnostics. Its failures name the tested KGP and KSP pair.

## Consequences

- Consumers can take KGP patch releases without waiting for a Kompact
  release.
- A patch release that does break the KSP2 seam surfaces as a generation
  failure. The warning printed earlier in the build points to the cause.
- Moving to a new Kotlin minor line still needs a Kompact release that has
  been tested against it.
