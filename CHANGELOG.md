# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [0.3.0] - 2026-09-25

### ✨ Features
- feat(ksp+kompact): add androidNativeArm64 target + kompact.generate=androidArm64 mode (#63) (6a8009a)

### 📦 Other
- fix(kover): exclude vulnerable freemarker transitive from plugin classpath (#66) (6c6af6f)
- fix(dependabot): ignore unresolved freemarker buildscript transitive (#64) (10015ee)

## [0.2.0] - 2026-09-25

### ✨ Features
- feat(ksp+kompact): ADR-0006 — immutable default views + opt-in Mutable sibling (slices 1-5) (#61) (737d5c0)

### 📦 Other
- refactor(runtime): collapse Byte/Short onto IntResult (≤32-bit-int value shape) (#60) (598948b)
- wayfinder: Kompact v1.0 implementation plan + v1-shape decisions (ADR-0005/0006/0003) (#58) (c6a4280)
- chore(deps): bump com.android.kotlin.multiplatform.library (#59) (8f81095)
- docs(adr): triage external code review into ADRs and spec tickets (#56) (87dfe9a)

## [0.1.7] - 2026-09-17

### 🐛 Fixes
- fix: KSP option handling, release-script idempotency, and docs link/anchor audit (#54) (0494cc1)

## [0.1.6] - 2026-09-17

### 🐛 Fixes
- fix: mark generated value-class companion object as 'actual' (#51) (5639d1a)


### 🐛 Fixes
- fix: mark generated value-class companion object as 'actual' (#51) (5639d1a)


### 📦 Other
- fix(ksp): emit model raw backing field as primary-constructor val (defect #3) (#48) (3c24aa3)


### 📦 Other
- fix(ksp): emit model raw backing field as primary-constructor val (defect #3) (#48) (3c24aa3)


### 📦 Other
- Merge branch 'main' of https://github.com/trancee/kompact (143ee28)
- fix(ci): use packages input for setup-android@v4 in release-publish workflow (ce203a3)
- chore(deps): bump ksp from 2.3.10 to 2.3.12 (#45) (29d963f)
- fix(deps): ignore kotlin-gradle-plugin in Dependabot config (dbaf3fd)
- fix(ci): use packages input for setup-android@v4 on macOS (b398167)
- fix(ksp): restore 100% kover coverage after round-safety refactor (f6ae98e)
- fix(ksp): round-safety + KMP expect/actual split via kompact.generate mode (b11a1b4)


### 📦 Other
- Merge branch 'main' of https://github.com/trancee/kompact (143ee28)
- fix(ci): use packages input for setup-android@v4 in release-publish workflow (ce203a3)
- chore(deps): bump ksp from 2.3.10 to 2.3.12 (#45) (29d963f)
- fix(deps): ignore kotlin-gradle-plugin in Dependabot config (dbaf3fd)
- fix(ci): use packages input for setup-android@v4 on macOS (b398167)
- fix(ksp): restore 100% kover coverage after round-safety refactor (f6ae98e)
- fix(ksp): round-safety + KMP expect/actual split via kompact.generate mode (b11a1b4)


### 📦 Other
- chore(deps): bump ksp from 2.3.10 to 2.3.12 (#45) (29d963f)
- fix(deps): ignore kotlin-gradle-plugin in Dependabot config (dbaf3fd)
- fix(ci): use packages input for setup-android@v4 on macOS (b398167)
- fix(ksp): restore 100% kover coverage after round-safety refactor (f6ae98e)
- fix(ksp): round-safety + KMP expect/actual split via kompact.generate mode (b11a1b4)


### 📦 Other
- fix(ksp): compile against KSP 2.3.10 for cross-version compatibility (826578c)


### 📦 Other
- fix(ksp): compile against KSP 2.3.10 for cross-version compatibility (826578c)


### 📦 Other
- fix(ksp): correct KSP 2.x service file path for provider discovery (5d34fdd)
- Merge branch 'main' of https://github.com/trancee/kompact (994c5e5)


### 📦 Other
- fix(ksp): correct KSP 2.x service file path for provider discovery (5d34fdd)
- Merge branch 'main' of https://github.com/trancee/kompact (994c5e5)




### ✨ Features
- feat: Kompact v1 — KMP bit-packing serializer, KSP processor, release automation, full docs (2571014)

### 📦 Other
- Release: 0.1.0 (fabda1b)
- ci: re-trigger release-pr.yml after persist-credentials fix (06666fb)
- Merge branch 'main' of https://github.com/trancee/kompact (071e46a)
- fix(release): persist-credentials: false so PAT push isn't overridden by GITHUB_TOKEN credential helper (a08295e)
- Release: 0.1.0 (b3e269e)
- Merge branch 'main' of https://github.com/trancee/kompact (d4dd725)
- fix(release): use git reset --hard origin/main in sync path instead of ff-only merge (b08718a)
- fix(release): use git reset --hard origin/main in sync path instead of ff-only merge (b406053)
- fix(release): generate CHANGELOG.md after checkout to avoid uncommitted-file conflict (3b50b5a)
- ci: trigger release-pr.yml with RELEASE_PAT set (8530021)
- fix(release): fix SIGPIPE in _get_bump_type + set git identity in release-pr.yml (27a836e)
- ci: re-trigger release-pr.yml (tag cleanup confirmed) (d6e820e)
- ci(release): re-trigger release-pr.yml after v0.0.1 tag cleanup (1f10554)
- fix(release): RELEASE_PAT repo-level secret + extract_next_snap fix (7c1f65f)
- Release: 0.1.0 (ae1eb94)
- Create dependabot.yml (05bb6e6)
- Merge pull request #29 from trancee/alert-autofix-1 (1707ea8)
- Potential fix for code scanning alert no. 1: Workflow does not contain permissions (8e13810)
- ci: add regen-goldens manual workflow (macOS apiDump) (ea33833)
- Merge pull request #22 from trancee/decision/descriptor-compatibility (4d02cc4)
- docs: define schema compatibility contract (2b8ea45)
- docs: define schema compatibility contract (2e4a8c4)
- Merge pull request #21 from trancee/decision/gradle-architecture (69a7bb9)
- docs: define Gradle generation architecture (216e8e5)
- Merge pull request #20 from trancee/decision/performance-budgets (82b4c50)
- docs: define performance budgets (bebb5d7)
- Merge pull request #19 from trancee/decision/conformance-contract (0f795f5)
- docs: define conformance and compatibility gates (98344a8)
- Merge pull request #18 from trancee/decision/validation-contract (18eb01d)
- docs: define validation and error contracts (5e7462f)
- Merge pull request #17 from trancee/decision/c99-interface (3e493dd)
- docs: define generated C99 interface (479c6a2)
- Merge pull request #16 from trancee/decision/kotlin-interface (b7c1e39)
- docs: define generated Kotlin interface (cc1dc6d)
- Merge pull request #15 from trancee/spec/kompact-v1-decisions (8d92923)
- docs: record allocation measurement research (d320824)
- docs: pin KSP research sources (f2d10a9)
- docs: record KSP KMP generation research (ba1c0bd)
- docs: define fixed aggregate layouts (8168243)
- docs: define envelope identity semantics (13fbcb3)
- docs: define scalar wire format (4d01a69)
- Merge pull request #12 from trancee/docs/add-project-readme (94bbcdd)
- docs: add project readme (90586e6)
- initial commit (29af49a)


### ✨ Features
- feat: Kompact v1 — KMP bit-packing serializer, KSP processor, release automation, full docs (2571014)

### 📦 Other
- ci: re-trigger release-pr.yml after persist-credentials fix (06666fb)
- Merge branch 'main' of https://github.com/trancee/kompact (071e46a)
- fix(release): persist-credentials: false so PAT push isn't overridden by GITHUB_TOKEN credential helper (a08295e)
- Release: 0.1.0 (b3e269e)
- Merge branch 'main' of https://github.com/trancee/kompact (d4dd725)
- fix(release): use git reset --hard origin/main in sync path instead of ff-only merge (b08718a)
- fix(release): use git reset --hard origin/main in sync path instead of ff-only merge (b406053)
- fix(release): generate CHANGELOG.md after checkout to avoid uncommitted-file conflict (3b50b5a)
- ci: trigger release-pr.yml with RELEASE_PAT set (8530021)
- fix(release): fix SIGPIPE in _get_bump_type + set git identity in release-pr.yml (27a836e)
- ci: re-trigger release-pr.yml (tag cleanup confirmed) (d6e820e)
- ci(release): re-trigger release-pr.yml after v0.0.1 tag cleanup (1f10554)
- fix(release): RELEASE_PAT repo-level secret + extract_next_snap fix (7c1f65f)
- Release: 0.1.0 (ae1eb94)
- Create dependabot.yml (05bb6e6)
- Merge pull request #29 from trancee/alert-autofix-1 (1707ea8)
- Potential fix for code scanning alert no. 1: Workflow does not contain permissions (8e13810)
- ci: add regen-goldens manual workflow (macOS apiDump) (ea33833)
- Merge pull request #22 from trancee/decision/descriptor-compatibility (4d02cc4)
- docs: define schema compatibility contract (2b8ea45)
- docs: define schema compatibility contract (2e4a8c4)
- Merge pull request #21 from trancee/decision/gradle-architecture (69a7bb9)
- docs: define Gradle generation architecture (216e8e5)
- Merge pull request #20 from trancee/decision/performance-budgets (82b4c50)
- docs: define performance budgets (bebb5d7)
- Merge pull request #19 from trancee/decision/conformance-contract (0f795f5)
- docs: define conformance and compatibility gates (98344a8)
- Merge pull request #18 from trancee/decision/validation-contract (18eb01d)
- docs: define validation and error contracts (5e7462f)
- Merge pull request #17 from trancee/decision/c99-interface (3e493dd)
- docs: define generated C99 interface (479c6a2)
- Merge pull request #16 from trancee/decision/kotlin-interface (b7c1e39)
- docs: define generated Kotlin interface (cc1dc6d)
- Merge pull request #15 from trancee/spec/kompact-v1-decisions (8d92923)
- docs: record allocation measurement research (d320824)
- docs: pin KSP research sources (f2d10a9)
- docs: record KSP KMP generation research (ba1c0bd)
- docs: define fixed aggregate layouts (8168243)
- docs: define envelope identity semantics (13fbcb3)
- docs: define scalar wire format (4d01a69)
- Merge pull request #12 from trancee/docs/add-project-readme (94bbcdd)
- docs: add project readme (90586e6)
- initial commit (29af49a)


### ✨ Features
- feat: Kompact v1 — KMP bit-packing serializer, KSP processor, release automation, full docs (2571014)

### 📦 Other
- Merge branch 'main' of https://github.com/trancee/kompact (d4dd725)
- fix(release): use git reset --hard origin/main in sync path instead of ff-only merge (b08718a)
- fix(release): use git reset --hard origin/main in sync path instead of ff-only merge (b406053)
- fix(release): generate CHANGELOG.md after checkout to avoid uncommitted-file conflict (3b50b5a)
- ci: trigger release-pr.yml with RELEASE_PAT set (8530021)
- fix(release): fix SIGPIPE in _get_bump_type + set git identity in release-pr.yml (27a836e)
- ci: re-trigger release-pr.yml (tag cleanup confirmed) (d6e820e)
- ci(release): re-trigger release-pr.yml after v0.0.1 tag cleanup (1f10554)
- fix(release): RELEASE_PAT repo-level secret + extract_next_snap fix (7c1f65f)
- Release: 0.1.0 (ae1eb94)
- Create dependabot.yml (05bb6e6)
- Merge pull request #29 from trancee/alert-autofix-1 (1707ea8)
- Potential fix for code scanning alert no. 1: Workflow does not contain permissions (8e13810)
- ci: add regen-goldens manual workflow (macOS apiDump) (ea33833)
- Merge pull request #22 from trancee/decision/descriptor-compatibility (4d02cc4)
- docs: define schema compatibility contract (2b8ea45)
- docs: define schema compatibility contract (2e4a8c4)
- Merge pull request #21 from trancee/decision/gradle-architecture (69a7bb9)
- docs: define Gradle generation architecture (216e8e5)
- Merge pull request #20 from trancee/decision/performance-budgets (82b4c50)
- docs: define performance budgets (bebb5d7)
- Merge pull request #19 from trancee/decision/conformance-contract (0f795f5)
- docs: define conformance and compatibility gates (98344a8)
- Merge pull request #18 from trancee/decision/validation-contract (18eb01d)
- docs: define validation and error contracts (5e7462f)
- Merge pull request #17 from trancee/decision/c99-interface (3e493dd)
- docs: define generated C99 interface (479c6a2)
- Merge pull request #16 from trancee/decision/kotlin-interface (b7c1e39)
- docs: define generated Kotlin interface (cc1dc6d)
- Merge pull request #15 from trancee/spec/kompact-v1-decisions (8d92923)
- docs: record allocation measurement research (d320824)
- docs: pin KSP research sources (f2d10a9)
- docs: record KSP KMP generation research (ba1c0bd)
- docs: define fixed aggregate layouts (8168243)
- docs: define envelope identity semantics (13fbcb3)
- docs: define scalar wire format (4d01a69)
- Merge pull request #12 from trancee/docs/add-project-readme (94bbcdd)
- docs: add project readme (90586e6)
- initial commit (29af49a)


### ✨ Features
- feat: Kompact v1 — KMP bit-packing serializer, KSP processor, release automation, full docs (2571014)

### 📦 Other
- Create dependabot.yml (05bb6e6)
- Merge pull request #29 from trancee/alert-autofix-1 (1707ea8)
- Potential fix for code scanning alert no. 1: Workflow does not contain permissions (8e13810)
- ci: add regen-goldens manual workflow (macOS apiDump) (ea33833)
- Merge pull request #22 from trancee/decision/descriptor-compatibility (4d02cc4)
- docs: define schema compatibility contract (2b8ea45)
- docs: define schema compatibility contract (2e4a8c4)
- Merge pull request #21 from trancee/decision/gradle-architecture (69a7bb9)
- docs: define Gradle generation architecture (216e8e5)
- Merge pull request #20 from trancee/decision/performance-budgets (82b4c50)
- docs: define performance budgets (bebb5d7)
- Merge pull request #19 from trancee/decision/conformance-contract (0f795f5)
- docs: define conformance and compatibility gates (98344a8)
- Merge pull request #18 from trancee/decision/validation-contract (18eb01d)
- docs: define validation and error contracts (5e7462f)
- Merge pull request #17 from trancee/decision/c99-interface (3e493dd)
- docs: define generated C99 interface (479c6a2)
- Merge pull request #16 from trancee/decision/kotlin-interface (b7c1e39)
- docs: define generated Kotlin interface (cc1dc6d)
- Merge pull request #15 from trancee/spec/kompact-v1-decisions (8d92923)
- docs: record allocation measurement research (d320824)
- docs: pin KSP research sources (f2d10a9)
- docs: record KSP KMP generation research (ba1c0bd)
- docs: define fixed aggregate layouts (8168243)
- docs: define envelope identity semantics (13fbcb3)
- docs: define scalar wire format (4d01a69)
- Merge pull request #12 from trancee/docs/add-project-readme (94bbcdd)
- docs: add project readme (90586e6)
- initial commit (29af49a)
