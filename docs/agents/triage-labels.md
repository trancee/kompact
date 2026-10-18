# Triage labels

The skills use five canonical triage roles. This table maps each role to the
string that this repository's tracker uses. Because the tracker is local
Markdown, the string goes on the `Status:` line of an issue file, not on a
GitHub label. See [`issue-tracker.md`](issue-tracker.md).

| Role in the skills | String in this tracker | Meaning |
| ------------------ | ---------------------- | ------- |
| `needs-triage` | `needs-triage` | A maintainer must evaluate the issue. |
| `needs-info` | `needs-info` | The issue waits for more information from the reporter. |
| `ready-for-agent` | `ready-for-agent` | The issue is fully specified and an agent can work on it unattended. |
| `ready-for-human` | `ready-for-human` | A human must implement the issue. |
| `wontfix` | `wontfix` | Nobody will act on the issue. |

When a skill names a role, for example "apply the AFK-ready triage label",
use the matching string from the table.

Existing files also use `Status:` values that are not triage roles:
`resolved`, `active`, `deferred`, `proposed`, and `wayfinder:map`. The
wayfinder operations also set `claimed`. To change the triage vocabulary,
edit the second column.
