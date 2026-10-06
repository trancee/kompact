#!/usr/bin/env python3
"""Synchronize maintained consumer-document version references."""

import re
import sys
from pathlib import Path


VERSION_PATTERN = re.compile(r"\b\d+\.\d+\.\d+\b(?!-SNAPSHOT)")
SNAPSHOT_PATTERN = re.compile(r"\b\d+\.\d+\.\d+-SNAPSHOT\b")
PRODUCT_COORDINATE = re.compile(r"ch\.trancee\.kompact")
PUBLISHED_CONTEXT = re.compile(r"\b(?:latest|published)\b", re.IGNORECASE)
KOMPACT_PUBLISHED_CONTEXT = re.compile(r"\bKompact\b.*\b(?:latest|published)\b", re.IGNORECASE)

DOCUMENTS = (
    "README.md",
    "SECURITY.md",
    "docs/getting-started.md",
    "docs/api-reference.md",
    "docs/ci.md",
    "docs/agents/agent-quick-start.md",
    "docs/research/ksp-kmp-generation.md",
)


def versions(root: Path) -> tuple[str, str]:
    build_text = (root / "build.gradle.kts").read_text()
    build_match = re.search(
        r'^\s*version\s*=\s*"(\d+\.\d+\.\d+(?:-SNAPSHOT)?)"',
        build_text,
        re.MULTILINE,
    )
    if build_match is None:
        raise ValueError("Could not read a semantic project version from build.gradle.kts")

    changelog_text = (root / "CHANGELOG.md").read_text()
    changelog_match = re.search(r"^## \[(\d+\.\d+\.\d+)\]", changelog_text, re.MULTILINE)
    if changelog_match is None:
        raise ValueError("Could not read the latest published version from CHANGELOG.md")
    return build_match.group(1), changelog_match.group(1)


def is_published_context(path: str, line: str) -> bool:
    if path == "docs/research/ksp-kmp-generation.md":
        return bool(PRODUCT_COORDINATE.search(line) or KOMPACT_PUBLISHED_CONTEXT.search(line))
    return bool(PRODUCT_COORDINATE.search(line) or PUBLISHED_CONTEXT.search(line))


def synchronize(root: Path) -> int:
    root = root.resolve()
    development_version, published_version = versions(root)
    paths = [root / path for path in DOCUMENTS]
    paths.extend(sorted((root / "docs/how-to").glob("*.md")))

    changed = 0
    for path in dict.fromkeys(paths):
        if not path.is_file():
            continue
        relative_path = path.relative_to(root).as_posix()
        original = path.read_text()
        output: list[str] = []
        for line in original.splitlines(keepends=True):
            line = SNAPSHOT_PATTERN.sub(development_version, line)
            if is_published_context(relative_path, line):
                line = VERSION_PATTERN.sub(published_version, line)
            output.append(line)
        updated = "".join(output)
        if updated != original:
            path.write_text(updated)
            changed += 1
    print(
        f"Synchronized {changed} documentation files to development "
        f"{development_version} and published {published_version}."
    )
    return changed


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")
    try:
        synchronize(root)
    except (OSError, ValueError) as failure:
        print(f"Could not synchronize documentation versions: {failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
