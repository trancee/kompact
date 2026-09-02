---
emoji: 📚
description: Audits documentation in pull requests against the Diátaxis framework and proposes improvements.
intent: Keep documentation accurate, well-classified by the Diátaxis framework, and quality-assured whenever pull requests touch documentation files.
on:
  pull_request:
    types: [opened, synchronize, reopened]
permissions:
  contents: read
  issues: read
  pull-requests: read
network:
  allowed:
    - defaults
    - openrouter.ai
tools:
  github:
    mode: gh-proxy
    toolsets: [default]
  cli-proxy: true
  bash: ["*"]
skills:
  - .github/skills/diataxis
safe-outputs:
  add-comment:
    target: "triggering"
    hide-older-comments: true
    max: 1
  create-pull-request-review-comment:
    max: 10
  create-pull-request:
    title-prefix: "[diataxis] "
    labels: [documentation, automation]
    draft: true
    protected-files: blocked
    allowed-files:
      - "**/*.md"
      - "docs/**"
    max-patch-files: 5
    max-patch-size: 1024
  noop:
engine:
  id: pi
  model: openai/${{ env.PI_MODEL }}
  env:
    PI_MODEL: poolside/laguna-s-2.1:free
    OPENAI_API_KEY: ${{ secrets.OPENROUTER_API_KEY }}
    OPENAI_BASE_URL: "https://openrouter.ai/api/v1"
---

# Diátaxis PR Docs Auditor

When a pull request is opened or updated, audit the repository's documentation using the **diataxis** skill and propose improvements following the [Diátaxis documentation framework](https://diataxis.fr/).

## What to do

1. **Fetch the PR** — use `gh pr view` and `gh pr diff` to inspect changed files.
2. **Identify documentation** — match files against doc patterns (`*.md`, `docs/**`, `README.md`, `AGENTS.md`, `CONTEXT.md`, `CHANGELOG.md`, etc.).
3. **Audit each doc** — for every affected documentation file, read it and apply the diataxis skill:
   - Classify its dominant form: **tutorial** (acquisition + action), **how-to** (application + action), **reference** (application + cognition), or **explanation** (acquisition + cognition).
   - Assess quality: accuracy, bounded completeness, consistency, usefulness, precision, fit, flow, anticipation, coherence, usability.
4. **Post findings** — summarize results as a PR comment via `add_comment`.
5. **Line-level feedback** — for concrete issues on specific lines, post review comments via `create_pull_request_review_comment`.
6. **Propose doc updates** — when a small, concrete improvement is evident (e.g., split a mixed-form page, fix a broken link, align headings to Diátaxis terminology), propose it via `create_pull_request` restricted to `**/*.md` and `docs/**`.
7. **No-op** — call `noop` with a short reason when the PR touches no documentation files, or when all affected docs already satisfy the Diátaxis quality gate.

## Diátaxis skill

The diataxis skill is installed from `.github/skills/diataxis/SKILL.md`. Read it and apply its guidance — particularly the compass mapping, quality gate, and validation steps. If the skill references files like `references/tutorials.md` or `scripts/check-links.py` that do not exist in this repository, record that as an unresolved fact and proceed with the parts that are applicable.

## Process

When you start:
1. Fetch the PR number from the GitHub event context.
2. Use `gh pr view` to list changed files.
3. Filter for documentation and doc-adjacent files.
4. Read each doc file and apply the diataxis skill.
5. Post the audit summary as a PR comment.

If no documentation files are touched, call `noop` with a short reason.
