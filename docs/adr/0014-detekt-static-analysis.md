# ADR-0014 — detekt static analysis

- **Status:** Accepted
- **Tags:** build, quality

## Context

Spotless with ktfmt checks formatting, but nothing checked code smells,
exception handling, or complexity. The latest stable detekt line (`1.23.8`)
was built against Kotlin `2.0.21`. The only `2.x` release is
`2.0.0-alpha.6` (`dev.detekt`), which tracks Kotlin `2.4`.

## Decision

1. Apply `io.gitlab.arturbosch.detekt` `1.23.8` once in the root build. It
   analyses the production sources of `kompact` (`commonMain`), `kompact-ksp`
   and `kompact-gradle-plugin`. CI runs it next to `spotlessCheck`.
2. Run only the plain `detekt` task. The type-resolution tasks are not used
   because they depend on the Kotlin compiler version detekt was built with.
3. Build on the default rule set. `config/detekt/detekt.yml` disables only
   `MagicNumber` (wire-format constants) and `MaxLineLength` (ktfmt owns line
   length), and it widens `SwallowedException` for the two intentionally
   translated exceptions.
4. Fix new findings or suppress them in place with a comment. The structural
   metrics that predated adoption (`ReturnCount`, `LongMethod`,
   `TooManyFunctions` and similar) are frozen in `config/detekt/baseline.xml`.
   The baseline may shrink but must not grow.
5. Move to detekt `2.x` once it ships a stable release.

## Consequences

- New smells fail CI, while existing hot-path code keeps its early-return
  structure.
- The Kotlin-version mismatch is limited to parsing, which tolerates it.
  Type-aware rules are unavailable until the `2.x` migration.
