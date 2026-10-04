---
Type: grilling
Status: resolved
---

## Question

What must CI execute and measure on each supported target before the first
supported release? Settle Native simulator/device test expectations,
per-target allocation evidence (including the Android Native validation
spike), and which measurements become merge/release gates. Also identify the
required-status-check evidence needed from external GitHub settings; do not
equate compilation or JVM tests with Native runtime proof.

## Answer

### Behavioral tests

PR CI runs behavioral tests on JVM and Android JVM host targets and iOS
Simulator Arm64. Before the first supported release, run behavioral tests on
physical iOS Arm64 and Android Native Arm64 devices. Compilation alone is not
runtime proof for those targets.

### Allocation claims

A zero-allocation claim is per target and must not be inferred from another
platform. Require a repeatable harness for the guaranteed API, a validated
exact zero-allocation counter, and an intentional-allocation positive
control. Retain evidence with commit, toolchain, OS/device, and workload
metadata as a release artifact. A sampled profile or an unproven Android
Native measurement method is insufficient. A target may remain functionally
supported while its zero-allocation claim is withheld until the proof method
is validated.

### Required checks

Both Linux and macOS CI jobs are required PR status checks. Verify the external
GitHub branch-protection settings before release; workflow YAML alone does
not prove that these checks are mandatory.
