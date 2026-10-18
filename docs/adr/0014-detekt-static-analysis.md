# ADR-0014: detekt static analysis

- Status: Accepted
- Date: 2026-10-18
- Tags: build, quality

## Context

Spotless with ktlint checks formatting, but nothing checked code smells,
exception handling, or complexity. The latest stable detekt line (`1.23.8`)
was built against Kotlin `2.0.21`. The only `2.x` release is `2.0.0-alpha.6`
(`dev.detekt`), which tracks Kotlin `2.4`.

## Decision

1. Apply `io.gitlab.arturbosch.detekt` `1.23.8` once, in the root build. It
   analyzes the production sources of `kompact` (`commonMain`), `kompact-ksp`,
   and `kompact-gradle-plugin`. CI runs it next to `spotlessCheck`.
2. Run only the plain `detekt` task. The build does not use the
   type-resolution tasks, because they depend on the Kotlin compiler version
   that detekt was built with.
3. Build on the default rule set. `config/detekt/detekt.yml` disables only
   `MagicNumber`, because wire-format constants are the bit arithmetic, and
   `MaxLineLength`, because ktlint owns line length. It also adds
   `InvocationTargetException` and `FileAlreadyExistsException` to the ignored
   types of `SwallowedException`, because the code translates those two
   exceptions on purpose.
4. Fix new findings, or suppress them in place with a comment.
   `config/detekt/baseline.xml` freezes the structural findings that existed
   before adoption (`ReturnCount`, `LongMethod`, `TooManyFunctions`, and
   similar rules). The baseline may shrink but must not grow.
5. Move to detekt `2.x` once it ships a stable release.

## Consequences

- New smells fail CI, while existing hot-path code keeps its early-return
  structure.
- The Kotlin version mismatch affects only parsing, and parsing tolerates it.
  Type-aware rules stay unavailable until the move to `2.x`.
