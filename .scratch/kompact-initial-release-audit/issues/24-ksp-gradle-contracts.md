---
Type: task
---

## Objective

Make KSP diagnostics deterministic and actionable, and bound the Gradle
plugin's Kotlin/KSP compatibility contract to tested tool pairs.

## Decisions

- Follow [the plugin compatibility decision](16-gradle-plugin-compatibility-contract.md)
  and [the codegen diagnostics contract](17-codegen-diagnostics-contract.md).
- Support only exact Kotlin/KSP pairs exercised in tests. Validate Kotlin
  Gradle Plugin compatibility during plugin configuration and KSP2 loader
  compatibility before task execution.
- Empty annotated models and resolved invalid annotated fields are compile
  errors. Unannotated properties are ignored and documented. Defer only
  genuinely unresolved symbols; suppress output for invalid models while
  continuing independent valid models and reporting multiple safe diagnostics.

## Acceptance criteria

- Add tests that demonstrate the exact supported Kotlin/KSP pair(s) and reject
  unsupported/missing/retyped KGP or KSP2 entrypoints with actionable,
  stable diagnostics.
- Confirm invalid KSP models produce no partial source while unrelated valid
  models continue; cover empty models, resolved invalid fields, unannotated
  properties, and unresolved symbols.
- Preserve configuration-cache, task avoidance, and build-cache behavior on
  the supported path; add negative-path TestKit coverage for compatibility
  failures.
- Document the tested compatibility matrix and diagnostic behavior.
- Run KSP processor tests, Gradle TestKit integration tests, the full plugin
  suite, ABI/coverage checks when established by task 27, and formatter/static
  analysis.
