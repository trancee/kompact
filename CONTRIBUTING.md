# Contributing to Kompact

This guide is for people changing Kompact itself. If you only want to use the
library, start with the [consumer documentation](docs/README.md).

## Before you change something

Read the relevant page in [`docs/adr/`](docs/README.md#design-decisions) before
changing the runtime, wire format, public API, KMP targets, or release flow.
Keep changes focused and include tests for behavior changes.

The repository uses the Gradle wrapper and JDK 21. Kotlin and Android Gradle
Plugin versions are declared in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml); the Gradle version is
pinned separately in
[`gradle/wrapper/gradle-wrapper.properties`](gradle/wrapper/gradle-wrapper.properties).

## Run checks

Start with the checks for the module you changed:

```bash
./gradlew :kompact:jvmTest
./gradlew :kompact-ksp:test
./gradlew :kompact-ksp-integration:test
./gradlew :kompact-gradle-plugin:test
```

Run the formatter check before opening a pull request:

```bash
./gradlew spotlessCheck
```

The CI workflow also checks public ABI baselines, coverage, Android compilation,
generated-code integration, and publication bundles. The full task list differs
by host; see [CI checks and local commands](docs/ci.md). iOS Simulator tests,
iOS ABI validation, and Dokka generation run on macOS.

## Update documentation

- Keep each guide focused on one reader goal. The
  [documentation index](docs/README.md) describes where each page belongs.
- Verify commands, API behavior, and examples against the current source.
- Regenerate the committed API reference with
  `./gradlew :kompact:dokkaGeneratePublicationMarkdown`; do not edit files under
  `kompact/docs/api/` by hand.
- The release workflow generates `CHANGELOG.md` from Conventional Commits. Do
  not add unreleased entries directly to that file.

## Open a pull request

Use a feature branch and a Conventional Commit title such as
`docs: clarify framed schema setup` or `fix: reject truncated input`. Explain
the user-visible effect, list the checks you ran, and call out any host-specific
validation that could not run. Do not include credentials or private security
details in the branch, logs, or pull request.

For a security report, follow [`SECURITY.md`](SECURITY.md) instead of opening a
public issue with vulnerability details.
