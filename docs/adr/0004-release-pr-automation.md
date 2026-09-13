# ADR-0004: Release-PR Automation for Maven Central Portal

- **Status:** accepted
- **Date:** 2026-09-13
- **Deciders:** kompact maintainer
- **Tags:** release, ci, github-actions, maven-central, pgp

## Context

Kompact needs an automated release pipeline that publishes `ch.trancee.kompact:kompact`
and `ch.trancee.kompact:kompact-ksp` to Maven Central Portal. The project uses a
custom Portal Publisher API integration (`portal-publish.gradle.kts`) — not a
third-party plugin like `nexus-publishing-plugin` or `com.vanniktech.maven.publish`.

The desired release model:

1. Every PR merged to `main` → create a **release PR** that stays open.
2. Additional PRs merged to `main` while the release PR is open → **sync** the
   release PR to include those changes, **without changing the version**.
3. Merging the release PR → **apply version** (SNAPSHOT → release), **build + sign
   + deploy** to Central Portal, **tag**, then **bump to next SNAPSHOT**.

### Key requirements

- The release PR must not change the version (it is a pure approval gate).
- The version bump happens only at release-PR merge time.
- Central Portal tokens and PGP private key must never appear in logs (S1).
- The publish step (irreversible) must require manual approval (S3: fail closed).
- The pipeline must be idempotent — re-runnable after a partial failure.

### Alternatives considered

1. **`release-please` (Google)**: Would automate changelog generation and version
   bumping, but it **always bumps the version inside the release PR**, which
   contradicts the "without changing the version" requirement. It also expects
   `maven-publish` + `nexus-publishing-plugin` for publishing, not a custom
   Portal API client. ❌ Rejected.

2. **`com.vanniktech.maven.publish`**: Simpler publishing plugin, but the project
   already has a working custom Portal Publisher API integration with S1-compliant
   redaction in `portal-publish.gradle.kts`. Swapping would discard that work. ❌
   Rejected.

3. **Manual release (no automation)**: Maintainer manually bumps version, builds,
   signs, deploys, tags, bumps to next SNAPSHOT. Error-prone; violates E7
   (CI config = authoritative automated gates). ❌ Rejected.

4. **Custom GitHub Actions workflows** (chosen): Full control over the release
   lifecycle, integrates with the existing Portal API tasks, matches all
   requirements. ✅ Selected.

## Decision

Implement two GitHub Actions workflows:

### `release-pr.yml` — Release PR management

- **Trigger**: `push` to `main`
- **Skip**: commits starting with `release:` or `chore(release):` (version bumps
  from the publish workflow — prevents circular triggering)
- **If no `release/ongoing` branch exists**: create it from `main`, create a PR
  targeting `main` with the `release` label.
- **If `release/ongoing` exists**: fast-forward it to `origin/main`, push, update
  the PR body (changelog regenerated).
- **`concurrency: release-pr`**: prevents overlapping runs on rapid successive
  pushes.

### `release-publish.yml` — Release execution

- **Trigger**: `pull_request_target` (closed) on `main`
- **Guards**: `github.event.pull_request.merged == true` + PR has `release` label
- **Conventional Commits versioning**: The release version is computed from
  commits since the last tag — `feat!:` or `BREAKING CHANGE` → major bump,
  `feat:` → minor bump, `fix:`/other → patch bump. If no commits since the
  last tag, defaults to patch bump.
- **`prepare` job** (no environment, auto-runs):
  1. Bump version (SNAPSHOT → computed release) via `version-bump.sh`
  2. Generate `CHANGELOG.md` from Conventional Commits since last tag, grouped
     by type (Breaking, Features, Fixes, Other)
  3. Run all CI quality gates (`spotlessCheck`, `checkKotlinAbi`, `jvmTest`,
     `koverVerify`, `bundleAndroidMainAar`, KSP module gates)
  4. Commit version bump + CHANGELOG.md + create git tag on `main`
- **`deploy` job** (`environment: release`, **manual approval**):
  1. Checkout the tagged release
  2. Build + sign + assemble bundles (`publishAllPublicationsToBundleDirRepository`,
     `generateChecksums`, `assembleCentralBundle`)
  3. Deploy to Central Portal (`centralPortalDeploy` for both modules)
  4. Wait for validation + poll loop (12 × 30 s, early-exit on success/failure)
  5. Publish (`centralPortalPublish` for both modules — IRREVERSIBLE)
  6. Bump to next SNAPSHOT, commit, push
  7. Delete `release/ongoing` branch

### Supporting infrastructure

- **`version-bump.sh`**: Shell script that extracts and bumps the version in
  `build.gradle.kts`. Implements Conventional Commits semantics — `feat!:` or
  `BREAKING CHANGE` → major, `feat:` → minor, `fix:`/other → patch. Also
  generates `CHANGELOG.md` grouped by commit type. Handles SNAPSHOT → release
  and release → next SNAPSHOT with automatic version increment.
- **GitHub Environment `release`**: Required reviewer (`trancee`), contains all
  publishing secrets as environment-scoped secrets (not repo-level):
  `CENTRAL_PORTAL_TOKEN_USERNAME`, `CENTRAL_PORTAL_TOKEN_PASSWORD`,
  `SIGNING_KEY`, `SIGNING_KEY_ID` (short, 8-char), `SIGNING_PASSWORD`.
- **`SIGNING_KEY_ID`**: The `.env` stores the 16-char long ID; the setup script
  shortens it to the last 8 chars before passing to Gradle's signing plugin
  (which rejects 16-char IDs without `0x` prefix).

### Why `pull_request_target` for the publish trigger

GitHub Actions' `GITHUB_TOKEN` cannot trigger workflows for PRs it creates.
Since `release-pr.yml` creates the release PR via `gh pr create` (using
`GITHUB_TOKEN`), the `pull_request` event would not fire reliably.
`pull_request_target` uses the base branch's workflow code and is the standard
pattern for release-PR automation. The `if` guard (merged + `release` label)
restricts execution to the legitimate release PR only.

## Consequences

- **Positive**: Fully automated release gating; secrets never in repository or
  logs; manual approval on the irreversible publish step; idempotent retries
  supported; both `kompact` and `kompact-ksp` modules released together;
  Conventional Commits drive both version bump and CHANGELOG.md content.
- **Negative**: Two jobs must complete (quality gates + manual approval); the
  maintainer must monitor the `deploy` job after approval. Failure after the
  version bump requires manual revert (documented in `docs/ci.md`).
- **First-release note**: With no prior `v*` tag, all commits since repo
  inception are scanned. Current history (19 `feat:`, 0 `feat!:`) yields a
  first release of `0.2.0` from `0.1.0-SNAPSHOT` (minor bump). This is the
  desired outcome — no intervention needed.
- **Security**: Environment-scoped secrets, `can_admins_bypass = true` allows
  the maintainer to override if needed, all credential values redacted in Gradle
  output by `portal-publish.gradle.kts` (S1).
