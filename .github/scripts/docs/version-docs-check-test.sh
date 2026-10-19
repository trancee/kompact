#!/usr/bin/env bash
set -euo pipefail

REPOSITORY_ROOT="$(git rev-parse --show-toplevel)"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT

FIXTURE="$TEST_ROOT/repository"
mkdir -p "$FIXTURE/docs/how-to" "$FIXTURE/docs/agents" "$FIXTURE/docs/research"
printf 'allprojects {\n    version = "0.8.0-SNAPSHOT"\n}\n' > "$FIXTURE/build.gradle.kts"
printf '# Changelog\n\n## [0.7.0] - 2026-10-04\n' > "$FIXTURE/CHANGELOG.md"
for path in \
  README.md \
  SECURITY.md \
  docs/getting-started.md \
  docs/api-reference.md \
  docs/ci.md \
  docs/how-to/consume-from-another-project.md \
  docs/agents/agent-quick-start.md \
  docs/research/ksp-kmp-generation.md; do
  mkdir -p "$FIXTURE/$(dirname "$path")"
  : > "$FIXTURE/$path"
done
cat > "$FIXTURE/README.md" <<'EOF'
Latest published release: `0.7.0`.
Development version: `0.8.0-SNAPSHOT`.
The development version is `0.8.0-SNAPSHOT`.
implementation("ch.trancee.kompact:kompact:0.7.0")
EOF
cat > "$FIXTURE/docs/how-to/consume-from-another-project.md" <<'EOF'
The current checkout is `0.8.0-SNAPSHOT`.
id("ch.trancee.kompact.codegen") version "0.7.0"
EOF

CHECK_SCRIPT="$REPOSITORY_ROOT/.github/scripts/docs/check-version-references.sh"
if ! output="$(bash "$CHECK_SCRIPT" "$FIXTURE" 2>&1)"; then
  printf 'FAIL valid references were rejected:\n%s\n' "$output" >&2
  exit 1
fi

sed -i.bak 's/0\.8\.0-SNAPSHOT/0.9.0-SNAPSHOT/' "$FIXTURE/build.gradle.kts"
rm -f "$FIXTURE/build.gradle.kts.bak"
if output="$(bash "$CHECK_SCRIPT" "$FIXTURE" 2>&1)"; then
  printf 'FAIL development-version drift was accepted\n' >&2
  exit 1
fi
grep -q 'development version' <<< "$output"
sed -i.bak 's/0\.9\.0-SNAPSHOT/0.8.0-SNAPSHOT/' "$FIXTURE/build.gradle.kts"
rm -f "$FIXTURE/build.gradle.kts.bak"

sed -i.bak 's/\[0\.7\.0\]/[0.8.0]/' "$FIXTURE/CHANGELOG.md"
rm -f "$FIXTURE/CHANGELOG.md.bak"
if output="$(bash "$CHECK_SCRIPT" "$FIXTURE" 2>&1)"; then
  printf 'FAIL published-version drift was accepted\n' >&2
  exit 1
fi
grep -q 'published version' <<< "$output"

sed -i.bak 's/\[0\.8\.0\]/[0.7.0]/' "$FIXTURE/CHANGELOG.md"
rm -f "$FIXTURE/CHANGELOG.md.bak"
cat > "$FIXTURE/README.md" <<'EOF'
Latest published release: `0.6.1`.
Development version: `0.7.0-SNAPSHOT`.
The development version is `0.7.0-SNAPSHOT`.
implementation("ch.trancee.kompact:kompact:0.6.1")
EOF
cat > "$FIXTURE/docs/how-to/consume-from-another-project.md" <<'EOF'
The current checkout is `0.7.0-SNAPSHOT`.
id("ch.trancee.kompact.codegen") version "0.6.1"
EOF
python3 "$REPOSITORY_ROOT/.github/scripts/docs/sync-version-references.py" "$FIXTURE"
if ! output="$(bash "$CHECK_SCRIPT" "$FIXTURE" 2>&1)"; then
  printf 'FAIL version synchronization did not repair references:\n%s\n' "$output" >&2
  exit 1
fi

sed -i.bak 's/0\.8\.0-SNAPSHOT/0.8.0/' "$FIXTURE/build.gradle.kts"
rm -f "$FIXTURE/build.gradle.kts.bak"
sed -i.bak 's/\[0\.7\.0\]/[0.8.0]/' "$FIXTURE/CHANGELOG.md"
rm -f "$FIXTURE/CHANGELOG.md.bak"
python3 "$REPOSITORY_ROOT/.github/scripts/docs/sync-version-references.py" "$FIXTURE"
if ! output="$(bash "$CHECK_SCRIPT" "$FIXTURE" 2>&1)"; then
  printf 'FAIL release-PR version synchronization did not repair references:\n%s\n' "$output" >&2
  exit 1
fi

printf 'allprojects {\n    version = "0.9.0-SNAPSHOT"\n}\n' > "$FIXTURE/build.gradle.kts"
cat > "$FIXTURE/README.md" <<'EOF'
Latest published release: `0.8.0`; the current checkout is `0.8.0`.
The development version is `0.8.0`.
Kotlin `2.4.21` is the compiler.
EOF
cp "$FIXTURE/README.md" "$TEST_ROOT/readme-before.md"
if output="$(bash "$CHECK_SCRIPT" "$FIXTURE" 2>&1)"; then
  printf 'FAIL stable-looking development-version drift was accepted\n' >&2
  exit 1
fi
grep -q 'development version' <<< "$output"
cmp "$TEST_ROOT/readme-before.md" "$FIXTURE/README.md"

cat > "$FIXTURE/docs/how-to/consume-from-another-project.md" <<'EOF'
## Published setup

id("ch.trancee.kompact.codegen") version "0.8.0"

## Try the development snapshot

The current checkout is `0.8.0`.

```kotlin
kotlin("multiplatform") version "2.4.21"
id("ch.trancee.kompact.codegen") version "0.8.0"
implementation("ch.trancee.kompact:kompact:0.8.0")
```

## Published setup again

implementation("ch.trancee.kompact:kompact:0.8.0")
EOF
cat > "$FIXTURE/docs/research/ksp-kmp-generation.md" <<'EOF'
Kompact's published plugin (`0.8.0`; this checkout is
`0.8.0`) uses KSP.

The latest KSP release is `2.3.12`.
EOF
python3 "$REPOSITORY_ROOT/.github/scripts/docs/sync-version-references.py" "$FIXTURE"
cat > "$TEST_ROOT/expected-readme.md" <<'EOF'
Latest published release: `0.8.0`; the current checkout is `0.9.0-SNAPSHOT`.
The development version is `0.9.0-SNAPSHOT`.
Kotlin `2.4.21` is the compiler.
EOF
cmp "$TEST_ROOT/expected-readme.md" "$FIXTURE/README.md"
grep -q 'plugin (`0.8.0`; this checkout is' "$FIXTURE/docs/research/ksp-kmp-generation.md"
grep -q '^`0.9.0-SNAPSHOT`) uses KSP.' "$FIXTURE/docs/research/ksp-kmp-generation.md"
grep -q 'latest KSP release is `2.3.12`' "$FIXTURE/docs/research/ksp-kmp-generation.md"
[[ "$(grep -c 'version "0.9.0-SNAPSHOT"' "$FIXTURE/docs/how-to/consume-from-another-project.md")" -eq 1 ]]
[[ "$(grep -c 'kompact:0.9.0-SNAPSHOT' "$FIXTURE/docs/how-to/consume-from-another-project.md")" -eq 1 ]]
[[ "$(grep -c 'kompact:0.8.0' "$FIXTURE/docs/how-to/consume-from-another-project.md")" -eq 1 ]]
grep -q 'multiplatform") version "2.4.21"' "$FIXTURE/docs/how-to/consume-from-another-project.md"
bash "$CHECK_SCRIPT" "$FIXTURE"
output="$(python3 "$REPOSITORY_ROOT/.github/scripts/docs/sync-version-references.py" "$FIXTURE")"
grep -q 'Synchronized 0 documentation files' <<< "$output"

printf 'allprojects {\n    version = "0.9.0"\n}\n' > "$FIXTURE/build.gradle.kts"
printf '# Changelog\n\n## [0.9.0] - 2026-10-17\n' > "$FIXTURE/CHANGELOG.md"
python3 "$REPOSITORY_ROOT/.github/scripts/docs/sync-version-references.py" "$FIXTURE"
bash "$CHECK_SCRIPT" "$FIXTURE"
if grep -q SNAPSHOT "$FIXTURE/README.md" "$FIXTURE/docs/how-to/consume-from-another-project.md"; then
  printf 'FAIL released development references retained SNAPSHOT\n' >&2
  exit 1
fi
printf 'allprojects {\n    version = "0.10.0-SNAPSHOT"\n}\n' > "$FIXTURE/build.gradle.kts"
python3 "$REPOSITORY_ROOT/.github/scripts/docs/sync-version-references.py" "$FIXTURE"
bash "$CHECK_SCRIPT" "$FIXTURE"
grep -q 'Latest published release: `0.9.0`; the current checkout is `0.10.0-SNAPSHOT`' "$FIXTURE/README.md"
grep -q 'kompact:0.10.0-SNAPSHOT' "$FIXTURE/docs/how-to/consume-from-another-project.md"
grep -q 'kompact:0.9.0' "$FIXTURE/docs/how-to/consume-from-another-project.md"

for claim in \
  'The development version is `0.10.0-SNAPSHOT`. It is not on Maven Central.' \
  'The published `0.9.0` plugin expects a different schema declaration.' \
  'This guide needs the unreleased `0.10.0-SNAPSHOT` runtime.' \
  'The latest release is `0.9.0`, and its API differs.'; do
  printf '%s\n' "$claim" > "$FIXTURE/docs/how-to/release-claim.md"
  if output="$(bash "$CHECK_SCRIPT" "$FIXTURE" 2>&1)"; then
    printf 'FAIL release-state claim was accepted: %s\n' "$claim" >&2
    exit 1
  fi
  grep -q 'release-state claim' <<< "$output"
done
printf 'Kotlin `2.4.21` differs from Kotlin `2.3.0`.\n' > "$FIXTURE/docs/how-to/release-claim.md"
bash "$CHECK_SCRIPT" "$FIXTURE"
rm "$FIXTURE/docs/how-to/release-claim.md"

cat > "$FIXTURE/docs/how-to/nested-heading.md" <<'EOF'
## Try the development snapshot

### Use the snapshot runtime

implementation("ch.trancee.kompact:kompact:0.9.0")

### Enable schema generation

id("ch.trancee.kompact.codegen") version "0.9.0"

## Published setup

implementation("ch.trancee.kompact:kompact:0.9.0")
EOF
python3 "$REPOSITORY_ROOT/.github/scripts/docs/sync-version-references.py" "$FIXTURE"
[[ "$(grep -c '0.10.0-SNAPSHOT' "$FIXTURE/docs/how-to/nested-heading.md")" -eq 2 ]] || {
  printf 'FAIL a subsection of a development section lost its development role\n' >&2
  cat "$FIXTURE/docs/how-to/nested-heading.md" >&2
  exit 1
}
grep -q '^implementation("ch.trancee.kompact:kompact:0.9.0")$' "$FIXTURE/docs/how-to/nested-heading.md"
rm "$FIXTURE/docs/how-to/nested-heading.md"

printf 'PASS documentation version drift checks\n'
