# ADR-0015: Generated MutFlow adapters

- Status: Accepted
- Date: 2026-10-18
- Tags: build, testing

## Context

MutFlow `1.7.0` reports a mutant as killed only when a real JUnit `@Test`
method in a `@MutFlowTest` class fails inside `MutFlow.underTest`. Dynamic
tests and other test factories do not count as kills. The runtime and KSP
modules used to keep hand-written adapter classes under `src/mutflowTest`.
Each adapter listed a subset of the behavior tests, and a union adapter
combined them. When someone added a behavior test, the lists went stale. The
runs also needed long `-PmutationTest.includes` lists. Comparison mutants in
the bit-copy loops either timed out or stopped the test JVM.

## Decision

1. The `mutation-results` convention plugin registers
   `generateMutationAdapter`. The task reads the test sources of the module
   and writes one adapter class. The adapter has one `@Test` for each behavior
   test, and each one calls `MutFlow.underTest { TestClass().method() }`.
   Modules configure the task through
   `mutationTesting { adapterSources, adapterPackage }`:
   - The runtime reads `commonTest` and adds the adapter to the `mutatedTest`
     compilation of MutFlow.
   - KSP reads `src/test` and adds the adapter to the `test` source set.
2. Every behavior test can kill every mutant, and every killer identity names
   the original test class and method. Generation fails if a `@Test` is not a
   function without parameters in a top-level class, or if two adapter methods
   would get the same name. The generator never skips a test it cannot wrap.
3. The generated adapter is the default test filter, so no include list is
   needed. `-PmutationTest.includes` stays available as an override.
4. Runtime loops must have a trip count that a mutant cannot make unbounded.
   The bit-copy loops in `KompactRuntime` iterate a precomputed byte count,
   and `writeZeros` uses `repeat`. The per-test budget of MutFlow and a 10 s
   `timeoutMs` bound everything else.
5. A surviving mutant is resolved in one of two ways. If the mutant changes
   behavior, add a test. If it is equivalent, remove the redundant code.
   `// mutflow:ignore` and `// mutflow:falsePositive` are allowed only when a
   written proof shows that neither way is possible.

## Consequences

- New behavior tests take part in mutation testing automatically. The target
  lists in the `mutflow { targets }` block of each module are the only scope
  setting.
- A full run executes the whole test suite of the module for each mutant. Both
  modules finish in under a minute.
- Adapter method names have the form `"<TestClass> <method>"`. MutFlow
  truncates long killer names in reports, so redundancy findings can merge
  tests whose names share a prefix.

> Note added 2026-10-18. The targets now cover whole packages:
> `ch.trancee.kompact.runtime.**` in `kompact/build.gradle.kts` and
> `ch.trancee.kompact.ksp.**` in `kompact-ksp/build.gradle.kts`. The
> `mutation-test` job in `.github/workflows/ci.yml` runs both modules. For the
> current results (748 of 748 runtime mutants and 391 of 391 KSP mutants
> killed), see [the mutation-testing
> disposition](../mutation-testing-disposition.md).
