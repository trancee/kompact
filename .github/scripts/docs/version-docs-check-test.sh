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
The development version, `0.8.0-SNAPSHOT`, is not published yet.
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
The development version, `0.7.0-SNAPSHOT`, is not published yet.
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

printf 'PASS documentation version drift checks\n'
