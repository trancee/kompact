---
name: diataxis
description: "Creates/audits/restructures technical docs via Diátaxis. Use for tutorials, how-to, reference, explanation, doc architecture, classification, or quality. Don't use for prose-only edits without a documentation need, API implementation, or product design."
metadata:
  category: "documentation"
  source: "https://diataxis.fr/"
  sourceVersion: "evildmp/diataxis-documentation-framework@957c09ca40b4a1edc23874f713e01937d50d54d5"
---

# Diátaxis

## 1. Scope

CLASSIFY create | revise | audit | restructure. RECORD product/craft, practitioner+competence, immediate situation, outcome, bounded pages/dir/journey. INSPECT live product/commands/API/config/examples + repo doc conventions; product behavior wins.

## 2. Compass

| need | context | form |
|---|---|---|
| action | acquisition | tutorial |
| action | application | how-to |
| cognition | application | reference |
| cognition | acquisition | explanation |

Classify by served need, not title/difficulty/length/steps. One dominant need per coherent page/section; brief support allowed only if flow remains. Distinct sustained need => split+link.

## 3. JIT rules

- tutorial -> READ `references/tutorials.md`
- how-to -> READ `references/how-to-guides.md`
- reference -> READ `references/reference.md`
- explanation -> READ `references/explanation.md`
- multi-form -> read selected refs only; one need/output; define cross-links

## 4. Branch

- create -> smallest complete doc for need
- revise -> smallest add/remove/move/split/merge/rename/rewrite
- audit -> copy `assets/audit-report.md`; evidence-backed, impact-ranked findings
- restructure -> improve real pages first; no empty four-part shell

## 5. Produce

- tutorial: safe controlled repeatable path; tutor owns success; visible result each step; expected output+observation; minimal choice/explanation
- how-to: competent practitioner + specific real goal; executable sequence; required judgment/branches/risk/recovery; usability > completeness
- reference: neutral machinery mirror; consistent pattern; facts/params/defaults/constraints/errors/warnings/examples; no persuasion
- explanation: one bounded why; context/reasons/history/implications/connections/perspectives/alternatives; no procedure
- match repo terms/headings/nav/code/link style
- audience=human => proper natural English; audience=agent => compact directive syntax

## 6. Architecture

Organize by practitioner need. Title/intro/placement/form make purpose predictable. Link neighboring forms without duplicate content. Reference may mirror product structure. Add navigation category only after real content exists. Publish each complete increment.

## 7. Quality

READ `references/quality-checklist.md`; evaluate every applicable item.
GATE functional: accuracy, bounded completeness, consistency, usefulness, precision. Exercise tutorial/how-to journey; compare reference to machinery; ground explanation facts.
Then judge fit, flow, anticipation, coherence, usability. Classification alone != quality.

## 8. Validate

1. RUN repo doc formatter/linter/build.
2. RUN:
   ```bash
   python3 scripts/check-links.py path/to/docs
   ```
3. READ required external links; checker is local-only.
4. RERUN affected examples/journeys; record exact evidence.
5. CONFIRM titles/nav/cross-links expose need without Diátaxis terminology.
6. OUT complete docs or audit + evidence + unresolved facts.

## Fail

- ambiguous compass -> choose form for immediate situation; split only sustained competing needs
- unverifiable fact -> mark unresolved; finish reachable work
- tutorial not reliably executable -> repair environment/expected-result gaps
- how-to branches explode -> narrow goal or split goals
- reference unbounded -> define machinery+version
- explanation expands -> restate why; delete unrelated material
- link target/fragment missing -> fix path/anchor; checker limitation -> verify with doc toolchain, record limitation
