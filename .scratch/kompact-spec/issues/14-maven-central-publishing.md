# 14: Maven Central Publishing

## Metadata

- **ID**: kompact-spec/14
- **Status**: ready-for-agent
- **Priority**: P1
- **Owner**: trancee (Philipp Grosswiler — github.com/trancee/kompact)
- **Created**: 2026-09-06
- **Related**: [SKILL: maven-central-publishing](skill://maven-central-publishing)

## Classification

| Field             | Value |
|-------------------|-------|
| **Scenario**      | First release |
| **Owner**         | trancee (Philipp Grosswiler) |
| **Coordinates**   | `ch.trancee.kompact:kompact:0.1.0-SNAPSHOT` |
| **Packaging**     | Kotlin Multiplatform (JVM jar + klib) |
| **Targets**       | `jvm`, `iosArm64`, `iosSimulatorArm64` |
| **Route**         | Portal Publisher API (direct integration) |
| **Staging mode**  | `USER_MANAGED` |
| **Signing**       | PGP (env-var injected) |
| **Max external action** | None — configure only. User must explicitly authorize account creation, token generation, and publishing. |

## Release Plan

### Phase 1 — Configuration (DONE)

- [x] Removed `com.vanniktech.maven.publish` 0.37.0 from `gradle/libs.versions.toml`
- [x] Removed `alias(libs.plugins.vanniktechMavenPublish)` from root `build.gradle.kts`
- [x] Added `maven-publish`, `signing`, `dokka` plugins to `kompact/build.gradle.kts`
- [x] Configured POM metadata on all publications via `MavenPublication` cast
- [x] Configured conditional PGP signing (only when `SIGNING_KEY` env var is present)
- [x] Added JVM sources JAR (KGP's `jvmSourcesJar` + common sources via Groovy interop)
- [x] Added Dokka Javadoc JAR (`dokkaJavadocJar` task)
- [x] Created local `bundleDir` Maven repository (`build/maven-layout/`)
- [x] Created Portal Publisher API tasks: `generateChecksums`, `assembleCentralBundle`, `centralPortalDeploy`, `centralPortalStatus`, `centralPortalPublish`
- [x] Verified build compiles: `gradle :kompact:tasks --group publication` ✅
- [x] Verified `inspect-project.py` — 0 warnings (except `SNAPSHOT_RELEASE_ROUTE_RISK` which is expected for a `-SNAPSHOT` version)

### Phase 2 — Authorization-gated (REQUIRES USER CONFIRMATION)

> **Do NOT attempt any of these without explicit user authorization.**

1. **Central Portal account registration** — Register at https://central.sonatype.com/ using GitHub login path. Verify namespace `ch.trancee.kompact` (reversed domain `trancee.ch` — May require DNS TXT record or GitHub repo challenge proof).
2. **Portal user token generation** — Generate `CENTRAL_PORTAL_TOKEN_USERNAME` + `CENTRAL_PORTAL_TOKEN_PASSWORD` env vars at Central Portal → Account → User Tokens.
3. **PGP key pair** — Generate a PGP key pair, publish the public key to a keyserver (keys.openpgp.org), inject the private key via `SIGNING_KEY` env var (with `SIGNING_KEY_ID` and `SIGNING_PASSWORD`).
4. **Version bump** — Change `0.1.0-SNAPSHOT` → `0.1.0` in `build.gradle.kts`.
5. **Local validation** — Run `publishAllPublicationsToBundleDirRepository`, inspect Maven layout, verify signatures + checksums.
6. **Portal deployment** — Run `centralPortalDeploy` (requires token). Validate via `centralPortalStatus`.
7. **Publication** — Point-of-risk confirmation before `centralPortalPublish` (IRREVERSIBLE).

### Phase 3 — Post-release

- [ ] Bump version to next `-SNAPSHOT` (e.g., `0.1.1-SNAPSHOT`)
- [ ] Update `CHANGELOG.md`
- [ ] Tag release in git
- [ ] Verify artifacts appear in Maven Central search

## Environment Variables Required (at release time)

| Variable | Purpose | Source |
|----------|---------|--------|
| `CENTRAL_PORTAL_TOKEN_USERNAME` | Portal API token username | Central Portal → Account → User Tokens |
| `CENTRAL_PORTAL_TOKEN_PASSWORD` | Portal API token password | Central Portal → Account → User Tokens |
| `SIGNING_KEY` | PGP private key (ASCII-armored) | Generated locally, never committed |
| `SIGNING_KEY_ID` | PGP key ID | Generated locally |
| `SIGNING_PASSWORD` | PGP key passphrase | Generated locally |
| `CENTRAL_PORTAL_DEPLOYMENT_ID` | Deployment UUID (for status/publish) | Written to `build/portal/deployment-id` by `centralPortalDeploy` |

## Gradle Task Reference

| Task | What it does | Requires |
|------|-------------|----------|
| `publishAllPublicationsToBundleDirRepository` | Publishes all KMP publications to `build/maven-layout/` | — |
| `generateChecksums` | Adds `.md5`, `.sha1`, `.sha256`, `.sha512` for every non-checksum file | `publishAllPublicationsToBundleDirRepository` |
| `assembleCentralBundle` | Zips `build/maven-layout/` into `build/kompact-portal-bundle.zip` | `generateChecksums` |
| `centralPortalDeploy` | POSTs bundle to Portal `/publisher/deploy` | `CENTRAL_PORTAL_TOKEN_*` env vars |
| `centralPortalStatus` | GETs deployment status from Portal `/publisher/status/:id` | `CENTRAL_PORTAL_TOKEN_*` env vars |
| `centralPortalPublish` | POSTs to Portal `/publisher/publish` (IRREVERSIBLE) | `CENTRAL_PORTAL_TOKEN_*` env vars |

## Verification Commands

```bash
# Verify build configuration (no publication required)
gradle :kompact:tasks --group publication --no-daemon

# Inspect project for Central-readiness
python3 ~/.agents/skills/maven-central-publishing/scripts/inspect-project.py --root . --json

# Validate locally (no credentials needed)
gradle :kompact:publishAllPublicationsToBundleDirRepository generateChecksums assembleCentralBundle --no-daemon
```
