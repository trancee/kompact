#!/usr/bin/env python3
"""Synchronize maintained consumer-document version references."""

import argparse
import re
import sys
from pathlib import Path


VERSION_PATTERN = re.compile(r"\b\d+\.\d+\.\d+(?:-SNAPSHOT)?\b")
PRODUCT_COORDINATE = re.compile(r"ch\.trancee\.kompact")
PUBLISHED_CONTEXT = re.compile(r"(?<!not )\b(?:latest|published)\b", re.IGNORECASE)
KOMPACT_PUBLISHED_CONTEXT = re.compile(r"\bKompact\b.*\b(?:latest|published)\b", re.IGNORECASE)
DEVELOPMENT_CONTEXT = re.compile(
    r"\b(?:development[ -]+version|(?:current|this)\s+checkout|"
    r"current(?=\s+`[^`]+`\s+checkout))",
    re.IGNORECASE,
)
HEADING = re.compile(r"^(#{1,6})\s+(.+)")
# A release rewrites every Kompact version, so a paragraph that ties one to a
# publication status or an API difference stops being true at the next release.
RELEASE_STATE_CLAIM = re.compile(
    r"\bnot\s+(?:yet\s+)?(?:published|released|on\s+Maven\s+Central)\b"
    r"|\bun(?:published|released)\b|\bdiffer(?:s|ent|ences?)?\b",
    re.IGNORECASE,
)

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


def rewrite(
    text: str,
    path: str,
    development_version: str,
    published_version: str,
    check: bool,
) -> tuple[str, int]:
    paragraph = ""
    paragraph_start = 1
    paragraph_has_version = False
    claims = 0
    development_heading: int | None = None
    fence: str | None = None
    output: list[str] = []

    def end_paragraph() -> None:
        nonlocal claims, paragraph_has_version
        claim = RELEASE_STATE_CLAIM.search(paragraph) if paragraph_has_version else None
        if check and claim:
            phrase = " ".join(claim.group().split())
            print(
                f"{path}:{paragraph_start}: release-state claim \"{phrase}\" "
                "next to a Kompact version becomes false after a release",
                file=sys.stderr,
            )
            claims += 1
        paragraph_has_version = False

    for line_number, line in enumerate(text.splitlines(keepends=True), start=1):
        stripped = line.lstrip()
        if not paragraph.strip():
            paragraph_start = line_number
        if stripped.startswith(("```", "~~~")):
            marker = stripped[:3]
            if fence == marker:
                fence = None
            elif fence is None:
                fence = marker
            end_paragraph()
            paragraph = ""
        heading = HEADING.match(line) if fence is None else None
        if heading:
            level = len(heading.group(1))
            if development_heading is not None and level <= development_heading:
                development_heading = None
            if re.search(r"\b(?:development|snapshot)\b", heading.group(2), re.IGNORECASE):
                development_heading = level
            end_paragraph()
            paragraph = ""
        if not line.strip():
            end_paragraph()
            paragraph = ""
        context = paragraph + line
        offset = len(paragraph)

        def replace(match: re.Match[str]) -> str:
            nonlocal paragraph_has_version
            version = match.group()
            role = None
            if version.endswith("-SNAPSHOT"):
                role = "development"
            elif PRODUCT_COORDINATE.search(line):
                role = "development" if development_heading is not None else "published"
            elif fence is None:
                position = offset + match.start()
                sentence_start = max(
                    (boundary.end() for boundary in re.finditer(r"[.!?]\s+", context)
                     if boundary.end() <= position),
                    default=0,
                )
                development_cues = [
                    cue.start() for cue in DEVELOPMENT_CONTEXT.finditer(context)
                    if sentence_start <= cue.start() < position
                ]
                published_cues = [
                    cue.start() for cue in PUBLISHED_CONTEXT.finditer(context)
                    if sentence_start <= cue.start() < position
                ] if is_published_context(path, context) else []
                latest_development = max(development_cues, default=-1)
                latest_published = max(published_cues, default=-1)
                if latest_development > latest_published:
                    role = "development"
                elif latest_published >= 0:
                    role = "published"
            if role is None:
                return version
            if fence is None:
                paragraph_has_version = True
            expected = development_version if role == "development" else published_version
            if check and version != expected:
                source = "build.gradle.kts" if role == "development" else "latest CHANGELOG.md release"
                print(
                    f"{path}:{line_number}: {role} version {version} "
                    f"does not match {source} ({expected})",
                    file=sys.stderr,
                )
            return expected

        output.append(VERSION_PATTERN.sub(replace, line))
        paragraph = context if fence is None else ""
    end_paragraph()
    return "".join(output), claims


def synchronize(root: Path, check: bool = False) -> int:
    root = root.resolve()
    development_version, published_version = versions(root)
    paths = [root / path for path in DOCUMENTS]
    paths.extend(sorted((root / "docs/how-to").glob("*.md")))

    changed = 0
    claims = 0
    for path in dict.fromkeys(paths):
        if not path.is_file():
            continue
        relative_path = path.relative_to(root).as_posix()
        original = path.read_text()
        updated, path_claims = rewrite(
            original, relative_path, development_version, published_version, check
        )
        claims += path_claims
        if updated != original:
            if not check:
                path.write_text(updated)
            changed += 1
    if not check:
        print(
            f"Synchronized {changed} documentation files to development "
            f"{development_version} and published {published_version}."
        )
    elif changed == 0 and claims == 0:
        print(
            f"Documentation versions match build.gradle.kts ({development_version}) "
            f"and CHANGELOG.md ({published_version})."
        )
    return changed + claims


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", type=Path, default=Path("."))
    parser.add_argument("--check", action="store_true", help="Report drift without changing files")
    arguments = parser.parse_args()
    try:
        changed = synchronize(arguments.root, check=arguments.check)
    except (OSError, ValueError) as failure:
        print(f"Could not synchronize documentation versions: {failure}", file=sys.stderr)
        return 1
    return 1 if arguments.check and changed else 0


if __name__ == "__main__":
    raise SystemExit(main())
