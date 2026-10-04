# Quality, documentation, and release audit

## Ranked findings

### Published-version documentation is behind the actual release

The README and multiple consumer/security/API pages identify `0.6.1` as the
latest Maven Central release and describe `0.7.0-SNAPSHOT` as unpublished.
The release audit verified Maven Central metadata showing `0.7.0` as the
latest published release and the repository at `0.8.0-SNAPSHOT`. Since
published coordinates cannot be overwritten, this is a release-history/docs
alignment issue, not an artifact to repair in place. Choose one authoritative
version source and automate consumer-doc version refresh/checking.

Affected pages include `README.md`, `SECURITY.md`, `docs/api-reference.md`,
`docs/getting-started.md`, `docs/agents/agent-quick-start.md`, and multiple
how-to/research docs. Exact current publication state was verified externally
against Maven Central metadata in the audit.

### The hard allocation contract has no executable evidence or gate yet

`docs/research/allocation-boxing-measurement.md` explicitly says it is a plan,
not an executed report, with no retained platform measurements or numeric
budgets. `docs/architecture.md` likewise disclaims published performance
budgets. CI has no allocation benchmark gate. This conflicts with the
Constitution's P1/P2/P6/T11 requirements for performance-sensitive claims.
The allocation feasibility report separately details candidate methods and
the Android Native proof gap.

### Native tests are not executed in workflows

The reviewed CI and release workflows execute JVM tests and JVM-adjacent
module tests; no iOS or Android Native test task appeared. macOS performs ABI
and documentation checks, not Native test execution. This leaves shared
behavior unexecuted on supported Native targets, despite the cross-platform
contract. Decide whether the first supported release requires simulator tests,
device tests, or a documented/tested host limitation per target.

### Runtime publication bundle is not dry-run in pull-request CI

The Portal dry-run assembles bundles for KSP and Gradle plugin, but not
`:kompact`, the primary runtime artifact. The runtime bundle path is exercised
by the actual release workflow. Add a pre-release bundle check if this is
confirmed against the current workflow revision.

### CI gates differ across modules

Runtime and KSP have ABI and coverage gates; the Gradle plugin has plugin
validation and tests but no observed Kover or ABI gate. Decide whether its
internal/public scope qualifies for a documented exclusion or should receive
compatible checks. Also identify any KSP integration-test exclusion rationale
explicitly.

### Release commit automation appears to conflict with Constitution G1

The release workflow pushes release/version commits to `main` using a PAT,
while Constitution G1 forbids protected-default direct commits. ADR-0004
describes the automation, but does not outrank the Constitution. Resolve the
governance conflict or alter release flow before treating policy as compliant.

### External branch-protection settings remain unverified

The repository has no committed branch-protection configuration. The release
workflow relies on PR CI having required all checks, while its own rerun list
appears Linux-focused. A maintainer must verify GitHub required status checks;
this cannot be established from this source audit.

## Evidence limitations

This is a source audit; the agent did not run Gradle gates. GitHub branch
protection is external configuration and was not inspected. Reconfirm workflow
findings on the current branch before turning them into implementation work.
