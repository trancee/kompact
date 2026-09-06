# Maven Central release report

## Authorization

- Central account / organization: **REGISTERED** — account exists under organization `trancee`.
- Verified namespace: **VERIFIED** — `ch.trancee.kompact` (reversed domain `trancee.ch`).
- Current Publisher Terms reviewed by user: **Not yet** — user has not explicitly reviewed/approved terms. Deploy+validate succeeded; publish was skipped per user instruction.
- Authorized actions: **Deploy + validate** — bundle uploaded and validated. `centralPortalPublish` (irreversible) **skipped** per user request.
- Point-of-risk publish confirmation: **Pending** — `centralPortalPublish` is IRREVERSIBLE. Deployment UUID `bb8e6cb2-02f5-4ffa-9fb8-fd3dc740af36` is `VALIDATED` (zero errors) and ready for publish.

## Release

- Source commit/tag: `feat/laguna` branch.
- Build tool and publisher/plugin version: Gradle 9.7.1, Kotlin 2.4.10, KMP plugin (version-aligned to Kotlin). No third-party publishing plugin — direct Portal Publisher API integration via `java.net.http.HttpClient`.
- Route and endpoint class: Portal Publisher API (direct HTTP integration). Endpoints (corrected per official docs at `central.sonatype.org/publish/publish-portal-api/`):
  - Deploy: `https://central.sonatype.com/api/v1/publisher/upload?name=<name>&publishingType=<type>` — multipart with `bundle` field (octet-stream), query params for `name`/`publishingType`
  - Status: `https://central.sonatype.com/api/v1/publisher/status?id=<deploymentId>` — POST, returns JSON
  - Publish: `https://central.sonatype.com/api/v1/publisher/deployment/<deploymentId>` — POST (no body)
  - Drop: `https://central.sonatype.com/api/v1/publisher/deployment/<deploymentId>` — DELETE
- Publishing mode (`USER_MANAGED` / `AUTOMATIC`): `USER_MANAGED`
- Coordinates and versions: `ch.trancee.kompact:kompact:0.1.0`, `ch.trancee.kompact:kompact-jvm:0.1.0`, `ch.trancee.kompact:kompact-iosarm64:0.1.0`, `ch.trancee.kompact:kompact-iossimulatorarm64:0.1.0`
- Release or snapshot: `0.1.0` (non-SNAPSHOT)
- PGP signing key: short key ID `681C9AF1` (last 8 of `C9E0F0E1...`); Gradle signing plugin rejects 16-char key IDs without `0x` prefix. Short ID form resolves this. The `.env` file retains the full 16-char ID; normalization to short form is done in the loader script, not in source.

## Artifacts

- Maven-layout bundle path: `build/kompact-portal-bundle.zip` (created by `assembleCentralBundle`, assembled after signing fix)
- Bundle digest: 481,903 bytes (~471 KB), 231 files (4 publications × artifacts + signatures + checksums)
- Primary artifacts:
  - `kompact-0.1.0.jar` (common/metadata root)
  - `kompact-jvm-0.1.0.jar` (JVM)
  - `kompact-0.1.0.module` (Gradle module metadata, root)
  - `kompact-jvm-0.1.0.module` (JVM)
  - `kompact-0.1.0.pom` (root POM)
  - `kompact-jvm-0.1.0.pom` (JVM POM)
  - `kompact-iosarm64-0.1.0.klib` (iOS arm64 native)
  - `kompact-iossimulatorarm64-0.1.0.klib` (iOS simulator arm64)
  - `kompact-*-sources.jar` (sources for all targets)
  - `kompact-jvm-0.1.0-javadoc.jar` (JVM Javadoc — README.md content, per KMP/JDK25 Dokka V1/V2 incompatibility)
  - `kompact-0.1.0-kotlin-tooling-metadata.json` (root)
- POMs and required metadata: POMs generated for `jvm`, `iosArm64`, `iosSimulatorArm64`, `kotlinMultiplatform` publications. All include: name, description, URL, Apache 2.0 license, developer (trancee/Philipp Grosswiler), SCM (git, github.com/trancee/kompact).
- Sources artifacts: `jvmSourcesJar` (configured via KGP + common sources added via Groovy interop). Verified: `sourceArtifacts: true` in inspection.
- Javadoc artifacts: `dokkaJavadocJar` task (`dokkaJavadocJar-0.1.0.jar`). Verified: `javadoc_files: true` in inspection.
- PGP fingerprint and signature verification: **SIGNED** — all artifacts have `.asc` detached signatures (20 signature files across 4 publications). Signing uses in-memory PGP keys from env vars. Verified by successful `signJvmPublication`, `signIosArm64Publication`, `signIosSimulatorArm64Publication`, `signKotlinMultiplatformPublication` tasks.
- Checksums: `.md5`, `.sha1`, `.sha256`, `.sha512` generated for every non-checksum file by `generateChecksums` task. Checksums also generated for `.asc` signature files.
- Secret scan result: `secret_indirection_files: ['.github/workflows/regen-goldens.yml', 'kompact/build.gradle.kts']` — all secrets via env vars, none committed. `gh secret list --repo trancee/kompact` shows only `COPILOT_GITHUB_TOKEN`.

## Central deployment

- Deployment UUID: `bb8e6cb2-02f5-4ffa-9fb8-fd3dc740af36`
- Deployment name: `kompact-0.1.0`
- Upload time: 2026-09-06T15:29 (approx)
- Upload response: HTTP 201 (Created)
- Validation state/errors: **`VALIDATED`** — zero errors. All 6 publications accepted.
  - purls: `pkg:maven/ch.trancee.kompact/kompact@0.1.0`, `pkg:maven/ch.trancee.kompact/kompact-jvm@0.1.0`, `pkg:maven/ch.trancee.kompact/kompact-iosarm64@0.1.0` (+ klib variant), `pkg:maven/ch.trancee.kompact/kompact-iossimulatorarm64@0.1.0` (+ klib variant)
  - warnings: Organization `trancee` is over its monthly Release Count publishing limit (enforcement begins October 1, 2026 — not yet enforced)
- Publish confirmation time: **Skipped** — user instructed to skip the irreversible publish step.
- Final state: `VALIDATED` (ready for `centralPortalPublish` when user authorizes)
- Central URLs: `https://central.sonatype.com/publishing` (to verify post-publish)
- Consumer resolution result/time: **Pending** — verify via `gradle :kompact:dependencies` with Maven Central repository after publish.

## Credentials and safety

- Portal token owner/purpose: Portal user token for organization `trancee`. Tokens are Portal-specific, NOT the GitHub password.
- Secret-store references: `.env` file at repo root (gitignored). All credentials via env vars only. No properties files, no command-line args.
- Signing-key: PGP private key in `.env` (`SIGNING_KEY`), key ID `C9E0F0E1681C9AF1` (long) → normalized to `681C9AF1` (short 8-char) for Gradle signing plugin. Passphrase via `SIGNING_PASSWORD` env var. PGP private key never committed to source.
- Legacy OSSRH credentials/endpoints removed: **Yes** — `vanniktech.maven.publish` plugin fully removed. No OSSRH/s01 references remain. All publishing tasks use direct Portal API endpoints.
- Temporary files: `build/portal/` and `build/maven-layout/` are in `.gitignore` and cleaned by `gradle clean`.

## Limitations and follow-up

- Unpublished/dropped deployments: Deployment `bb8e6cb2-...` is `VALIDATED` and kept (not dropped). Ready for publish or can be dropped via DELETE `/api/v1/publisher/deployment/<id>`.
- Unsupported modules/variants: None. All three KMP targets (`jvm`, `iosArm64`, `iosSimulatorArm64`) are published with sources, signatures, and checksums.
- Propagation pending: Artifact propagation to Maven Central mirrors typically takes 1–3 hours after `centralPortalPublish`.
- Snapshot handling: Version is `0.1.0` (non-SNAPSHOT), correctly routed to release endpoint.
- Corrective release: If validation had failed, the deployment would be dropped via Portal UI/API and re-run after fixes. Validation passed with zero errors.
- API endpoint correction: Initial `centralPortalDeploy` task used `/api/v1/publisher/deploy` (HTTP 500). Corrected to `/api/v1/publisher/upload` per official docs. Also corrected `centralPortalStatus` (POST to `/status?id=` instead of GET to `/status/<id>`) and `centralPortalPublish` (POST to `/deployment/<id>` with no body instead of POST to `/publish` with JSON body).
- Signing key ID normalization: Gradle's `PgpKeyId.normaliseKeyId` rejects 16-char key IDs without `0x` prefix. Fixed by using the short 8-char key ID (`681C9AF1`) in the loader script. This is the only workaround needed — the `.env` file is unchanged.

## What's left (authorization-gated)

These steps require **explicit user confirmation** before execution:

1. **PUBLISH** — Run `gradle :kompact:centralPortalPublish` (IRREVERSIBLE). User must confirm at point of risk. Deployment UUID `bb8e6cb2-02f5-4ffa-9fb8-fd3dc740af36` is `VALIDATED` (zero errors).
2. **Verify** — After publish, verify artifact resolution via `gradle :kompact:dependencies` with Maven Central repository.

### Verification commands

```bash
# Verify all tasks are registered
gradle :kompact:tasks --group publication --no-daemon

# Verify Central-readiness
python3 ~/.agents/skills/maven-central-publishing/scripts/inspect-project.py --root . --json

# Build artifacts locally (no signing, no token)
gradle :kompact:publishAllPublicationsToBundleDirRepository generateChecksums assembleCentralBundle --no-daemon

# Deploy for validation (requires .env credentials)
python3 -c "
import os, subprocess
with open('.env') as f:
    for line in f:
        line = line.strip()
        if not line or line.startswith('#'): continue
        key, _, value = line.partition('=')
        value = value.replace('\\\\n', '\n')
        os.environ[key] = value
kid = os.environ.get('SIGNING_KEY_ID', '')
if len(kid) == 16: os.environ['SIGNING_KEY_ID'] = kid[-8:]
env = os.environ.copy()
subprocess.run(['gradle', ':kompact:clean', ':kompact:publishAllPublicationsToBundleDirRepository', 'generateChecksums', 'assembleCentralBundle', 'centralPortalDeploy', '--no-daemon', '--console=plain'], env=env, timeout=120)
"

# Check validation status
gradle :kompact:centralPortalStatus --no-daemon --console=plain
```

### Env var requirements

All credentials via `.env` (gitignored):

```bash
CENTRAL_PORTAL_TOKEN_USERNAME=<portal_token_username>
CENTRAL_PORTAL_TOKEN_PASSWORD=<portal_token_password>
SIGNING_KEY=<ascii-armored-pgp-private-key>        # \\n escapes → newlines
SIGNING_KEY_ID=<16-hex-char-key-id>                 # auto-shortened to 8 chars at runtime
SIGNING_PASSWORD=<pgp-passphrase>
```

**Note on `SIGNING_KEY_ID`**: The `.env` file contains the 16-char long key ID (`C9E0F0E1681C9AF1`). Gradle's signing plugin rejects 16-char IDs without `0x` prefix. The loader script in `centralPortalDeploy` normalization step shortens to the last 8 chars (`681C9AF1`) before running Gradle. The `.env` file itself is NOT modified.
