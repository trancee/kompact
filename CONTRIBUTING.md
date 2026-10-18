# Contributing to Kompact

This guide is for people who change Kompact itself. To use the library, start
with the [documentation map](docs/README.md).

## Before you change something

Before you change the runtime, the wire format, the public API, the KMP
targets, or the release flow, read the matching ADR in
[`docs/adr/`](docs/README.md#design-decisions). Keep each change focused, and
add tests for every behavior change.

The build uses the Gradle wrapper and JDK 21. The runtime, the KSP processor,
and the Gradle plugin compile to JVM bytecode 17. The versions of Kotlin, KSP,
the Android Gradle Plugin, and the other build tools are in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml). The Gradle version is
in
[`gradle/wrapper/gradle-wrapper.properties`](gradle/wrapper/gradle-wrapper.properties).

## Run checks

Start with the tests for the module you changed:

```bash
./gradlew :kompact:jvmTest
./gradlew :kompact-ksp:test
./gradlew :kompact-ksp-integration:test
./gradlew :kompact-gradle-plugin:test
```

Before you open a pull request, run the formatter check and static analysis:

```bash
./gradlew spotlessCheck detekt
```

If `spotlessCheck` fails, run `./gradlew spotlessApply` to format the sources.

`detekt` analyzes the production sources of `:kompact`, `:kompact-ksp`, and
`:kompact-gradle-plugin` with the project rules in `config/detekt/detekt.yml`.
Fix a new finding in the code. Use a narrow `@Suppress` with a comment only
for a pattern you chose on purpose. `config/detekt/baseline.xml` lists only
the findings that existed when the project adopted detekt
([ADR-0014](docs/adr/0014-detekt-static-analysis.md)). Do not regenerate the
baseline to hide new findings.

CI also checks the public ABI dumps, coverage, Android compilation, generated
code, and the publication bundles. A separate CI job runs mutation testing
over the whole runtime and KSP processor packages and fails if any mutant
survives. The task lists differ by host. See
[CI checks and local commands](docs/ci.md) for the exact commands, including
the mutation-testing commands. iOS Simulator tests, iOS ABI validation, and
Dokka generation run only on macOS.

## Update documentation

Follow these rules when you change documentation:

- Keep each guide focused on one reader goal. The
  [documentation map](docs/README.md) shows where each page belongs.
- Check commands, API behavior, and examples against the current source.
- To regenerate the committed API reference, run
  `./gradlew :kompact:dokkaGeneratePublicationMarkdown`. Do not edit files under
  `kompact/docs/api/` by hand.
- The release workflow generates `CHANGELOG.md` from Conventional Commits. Do
  not add unreleased entries to that file.

## Open a pull request

Work on a feature branch. Give the pull request a Conventional Commit title,
such as `docs: clarify framed schema setup` or `fix: reject truncated input`.
In the description, explain the effect that users see, list the checks you
ran, and name any host-specific check that you could not run. Do not put
credentials or private security details in the branch, the logs, or the pull
request.

To report a security problem, follow [`SECURITY.md`](SECURITY.md). Do not open
a public issue with vulnerability details.
