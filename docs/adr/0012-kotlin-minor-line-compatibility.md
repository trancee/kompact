# ADR-0012: Kotlin minor-line compatibility for the Gradle plugin

- Status: Accepted
- Date: 2026-10-18
- Tags: build, compatibility
- Supersedes: the exact KGP and KSP pair contract in [release-audit issue
  16](../../.scratch/kompact-initial-release-audit/issues/16-gradle-plugin-compatibility-contract.md)
- Related: [ADR-0008](0008-framed-generated-views.md) introduced the
  `ch.trancee.kompact.codegen` plugin and its reflective KSP2 integration.

## Context

The `ch.trancee.kompact.codegen` plugin runs KSP2 in an isolated class loader.
That engine uses the Kotlin and KSP versions that the plugin was built with,
not the Kotlin Gradle Plugin (KGP) of the consumer. Before this decision, the
plugin failed the build unless the KGP version of the consumer matched the
tested version exactly. Every KGP patch release therefore broke code
generation, even though Kotlin keeps metadata and language versions stable
within a minor line such as `2.4.x`.

## Decision

1. If the detected KGP version equals the tested version, the plugin
   continues without a message.
2. If the detected version is another release of the same `major.minor` line,
   including a pre-release such as `2.4.30-RC`, the plugin continues and logs a
   warning that names both versions.
3. If the minor line differs, or the plugin cannot detect the version, the
   plugin still fails the build with `Kompact code generation is incompatible`
   before any generation runs.
4. The reflective KSP2 entry point (`KSPLoader.loadAndRunKSP`) keeps its
   existing diagnostics. Its failures name the tested KGP and KSP pair.

## Consequences

- Consumers can take KGP patch releases without waiting for a Kompact
  release.
- If a patch release does break the KSP2 integration, the break shows up as a
  generation failure. The warning printed earlier in the build points to the
  cause.
- A new Kotlin minor line still needs a Kompact release that was tested
  against it.
