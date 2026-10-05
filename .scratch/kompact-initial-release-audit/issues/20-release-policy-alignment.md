---
Type: grilling
Status: resolved
---

## Question

How should the release version-bump/tag workflow comply with Constitution
G1's prohibition on direct commits to protected default branches? Decide
whether the release automation must switch to a PR-based flow or whether the
Constitution itself must be amended through its governance process. ADR-0004
alone cannot override the higher-priority rule.

## Answer

Preserve Constitution G1 unchanged. All release version/changelog commits must
arrive through a release PR that receives authorized human review and merge.
After merge, automation may publish/tag, but must not push a follow-up commit
directly to protected `main`. Advancing the next development snapshot also
requires a subsequent PR rather than a direct bot push.

This removes the protected-branch bypass without adding a Constitution
exception, at the cost of a human-reviewed release PR and potentially a
second PR to advance the next snapshot. Update ADR-0004 and release workflows
to match this policy.
