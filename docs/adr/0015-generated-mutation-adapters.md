# ADR-0015 — generated MutFlow adapters

- **Status:** Accepted
- **Tags:** build, testing

## Context

MutFlow `1.7.0` only reports a mutant as killed when a real JUnit `@Test`
method in a `@MutFlowTest` class fails inside `MutFlow.underTest`. Dynamic
tests and other test factories don't count as kills. The runtime and KSP
modules used to keep hand-written adapter classes under `src/mutflowTest`.
Each adapter listed a subset of behavior tests, and a union adapter combined
them. When behavior tests were added, the lists went stale. The runs also
needed long `-PmutationTest.includes` lists, and comparison mutants in the
bit-copy loops either timed out or aborted the test JVM.

## Decision

1. The `mutation-results` convention plugin registers
   `generateMutationAdapter`. It reads the module's test sources and writes
   one adapter class. The adapter has one `@Test` per behavior test, and each
   calls `MutFlow.underTest { TestClass().method() }`. Modules configure this
   through `mutationTesting { adapterSources, adapterPackage }`:
   - the runtime uses `commonTest` and adds the adapter to MutFlow's
     `mutatedTest` compilation;
   - KSP uses `src/test` and adds the adapter to the `test` source set.
2. Every behavior test can kill every mutant, and every killer identity names
   the original test class and method. Generation fails if a `@Test` is not a
   parameterless function in a top-level class, or if two adapter methods
   would have the same name. A test that can't be wrapped is never silently
   skipped.
3. The generated adapter is the default test filter, so no include list is
   needed. `-PmutationTest.includes` remains available as an override.
4. Runtime loops must have a trip count that a mutant can't make unbounded.
   The bit-copy loops in `KompactRuntime` iterate a precomputed byte count,
   and `writeZeros` uses `repeat`. MutFlow's per-test budget plus a 10 s
   `timeoutMs` bound anything else.
5. A surviving mutant is resolved in one of two ways. If the mutant changes
   behavior, a test is added. If it is equivalent, the redundant code is
   removed. `// mutflow:ignore` and `// mutflow:falsePositive` are allowed
   only when a written proof shows that neither option is possible.

## Consequences

- New behavior tests take part in mutation testing automatically. The target
  lists in each module's `mutflow { targets }` are the only scope setting.
- A full run executes the module's whole test suite for each mutant. Both
  modules finish in under a minute.
- Adapter method names are `"<TestClass> <method>"`. MutFlow truncates long
  killer names in reports, so redundancy findings can merge tests that share
  a prefix.
