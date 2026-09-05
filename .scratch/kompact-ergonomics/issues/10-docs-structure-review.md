Type: grilling
Status: resolved

# 10 — Is the docs/ layer the right shape?

## Question

The docs/ layer was just laid down in this effort as
`tutorial + reference + architecture + ci + navigation index`:

- `docs/getting-started.md` — the executable tutorial (a
  16-bit telemetry frame, wire bytes pinned, verified by
  `GettingStartedTest`).
- `docs/api-reference.md` — the public-API reference
  (KompactRuntime / KompactWriter / KompactFraming / the
  result value classes / KompactDecodeError / annotations).
- `docs/architecture.md` — the design rationale (LSB-first,
  zero-alloc, value classes, packed-Long error encoding,
  framing, versioning).
- `docs/ci.md` — the how-to for the two CI workflows.
- `docs/README.md` — the navigation index.

The five-file shape follows the Diátaxis tutorial /
reference / explanation / how-to split, plus a navigation
index. The decision: is the layering itself the right one,
or is one of the splits off?

- **"How to use it" vs. "how it works"** — is the tutorial
  (`getting-started.md`) the right place for the "first
  example" or should that be in the README and the
  tutorial be a more advanced "here's the typed-result
  pattern" walkthrough? Today the README has a 5-line
  excerpt of the tutorial and links to it; the tutorial
  has the full example. Is the layering correct?
- **"What can I call" vs. "what does it do"** — is the
  reference (`api-reference.md`) the right place for
  "every function and parameter," or should the
  reference be the *narrow* surface (the seven public
  read/write entry points) and the rationale for each
  one live in `architecture.md`? Today the reference
  is detailed (per-function tables) and the
  architecture doc covers the *design* rationale. A
  consumer reading the reference gets the signature
  and a one-line description; a consumer reading the
  architecture doc gets the *why*. The boundary is
  right but the reference is currently 11k of tables
  that duplicate information that could be a
  KDoc-generated reference (e.g. via Dokka).
- **"How the CI works"** — is `docs/ci.md` the right
  place, or should CI live in the project root
  (`CONTRIBUTING.md` is the conventional home, but
  this repo doesn't have one)? Today `docs/ci.md` is
  a how-to for the two workflows; a contributor who
  wants to know "how do I run the tests locally" reads
  `docs/ci.md`. A contributor who wants to know "what
  is the project's contribution workflow" reads
  `AGENTS.md` (the AI policy, which is human-readable
  but not contributor-focused).
- **The "what is in this repo" map** — `docs/README.md`
  is the navigation index. Should this be a separate
  page, or is it overkill for five docs?

## Context for the claiming session

- `README.md` — the root index, ~3k, with a where-to-go-next
  table.
- `docs/getting-started.md` — ~5k, tutorial.
- `docs/api-reference.md` — ~12k, reference.
- `docs/architecture.md` — ~11k, explanation.
- `docs/ci.md` — ~4k, how-to.
- `docs/README.md` — ~2k, navigation index.
- The repo's existing human-readable docs (before this
  effort): none for consumers. The only docs were
  `AGENTS.md` / `CONSTITUTION.md` (AI policy) and
  `.scratch/kompact-spec/` (internal spec).
- The Diátaxis skill's form definitions (tutorial,
  how-to, reference, explanation) are the reference for
  "is the layering right?" — each form has a defined
  mode and a contract; if any of the five docs fails the
  contract for its form, it's a layering problem.
- The repo's `AGENTS.md` says (in the "Agent skills"
  section): "Issues and spec tickets live as markdown
  files under `.scratch/<feature>/`; no GitHub Issues
  used. See `docs/agents/issue-tracker.md`." This is
  *process* documentation, not consumer docs, and
  doesn't compete with the docs/ layer.

## Open sub-questions

1. **Is the reference doc too long for a v1 library?** 12k
   of per-function tables is fine for a stable, mature
   API; for a v1 that's still being shaped by tickets
   01–10, the reference churns every commit. A
   KDoc-generated reference (Dokka) might be the right
   answer for v1 (less hand-maintained) with the
   rationale content in `architecture.md`.
2. **Should the tutorial and the README's code excerpt
   converge into one example?** Today the README has
   a 5-line excerpt of the tutorial; the tutorial has
   the full 16-bit example. If the layering changes
   (tutorial moves to README, or README moves into
   the tutorial), the convergence is implicit.
3. **The "contributor vs. consumer" split.** A
   contributor's needs (run the tests, regenerate the
   goldens, understand the spec) overlap with a
   consumer's needs (use the API, understand the wire
   format) but are not identical. The current docs
   layer is consumer-first. A future "Contributor's
   guide" might be a separate file in `docs/` or a
   `CONTRIBUTING.md` at the root.
4. **The "what is in this repo" map.** `docs/README.md`
   is the navigation index. Is this overkill, or is
   it the natural entry point for someone who arrived
   at `docs/` from a link in the README?

## What "resolved" looks like

- The chosen docs shape is recorded under `## Answer`
  with a one-line rationale.
- If a file is renamed, merged, or split, the new
  layout is listed.
- The decision is propagated to the README (which
  currently links to the five docs) and to any
  cross-references in `docs/architecture.md` and
  `docs/api-reference.md` (which link to each other
  and to the tutorial).
- If a `CONTRIBUTING.md` is added, its scope and
  shape are noted.

## Answer

**Decisions** (one per open sub-question):

- **Reference doc length** — **add Dokka now** (the user's
  pick). Add the `dokka` Gradle plugin to `:kompact`,
  configure `dokkaGfm` (Markdown output) to produce a per-file
  reference in `docs/api/`, and replace the hand-maintained
  per-function tables in `docs/api-reference.md` with a pointer
  to the generated output. The narrative content (the
  "Long" suffix explanation on `readScalarAsLong`, the
  writer's growable-buffer note, the cross-references to the
  architecture doc) stays hand-maintained in
  `docs/api-reference.md` as the curated *overview*; the
  per-function detail (signature, params, returns, throws,
  warnings) is generated from KDoc. The implementation is a
  follow-up commit: the *decision* is recorded here; the build
  wiring (plugin, version catalog, config) is a separate
  effort that doesn't block the other ergonomics tickets.
- **Tutorial vs. README excerpt** — **keep the current
  shape**. The README's 5-line code excerpt is a *taste*, not
  a *tutorial*; the tutorial (`docs/getting-started.md`) is
  the canonical walkthrough with the wire bytes (`0xA5 0x40`)
  and the expected output. Standard layered approach.
- **CI doc location** — **keep `docs/ci.md`**. The current
  scope (two workflows + local-run instructions) fits a
  single page. A future `CONTRIBUTING.md` at the root is the
  right home for *broader* contributor docs (release process,
  issue tracker, code review conventions) when they
  accumulate; for now, CI alone doesn't justify a root-level
  contributor doc.
- **`docs/README.md` index** — **keep the separate index**.
  Two pages (root `README.md` + `docs/README.md`) is the
  standard layered approach. The index lists the user-facing
  docs and points at the internal docs (AGENTS.md,
  CONSTITUTION.md, `docs/agents/*`). The index is a
  navigation hub, not a duplication of the root.

### What this means for the implementation commit

The single-actionable decision is the Dokka pick. The
implementation is a follow-up commit (this ticket records the
*decision*; the build wiring is separate):

1. Add the `dokka` plugin to `gradle/libs.versions.toml` (a
   version-catalog entry for `org.jetbrains.dokka:dokka-gradle-plugin`).
2. Add the plugin to `kompact/build.gradle.kts` and configure
   `dokkaGfm` (Markdown output) to produce per-file output at
   `docs/api/`.
3. Update the `.gitignore` (or the `:kompact:clean` task) to
   exclude the generated `docs/api/` from source control
   (the output is a build artifact; the *source* of truth is
   the KDoc comments). Alternatively, commit the generated
   output and let CI re-verify it on every change; the user's
   call (the simpler path is to commit the output so the docs
   PR review is self-contained).
4. Replace the per-function tables in `docs/api-reference.md`
   with a curated overview (the narrative currently in the
   file: the "Long" explanation, the writer's growable-buffer
   note, the cross-references) + a pointer to the generated
   `docs/api/`.
5. Re-render the generated output on every `:kompact:apiDump`
   or as a separate `:kompact:dokkaGfm` task that the CI
   runs (the existing `apiCheck` job on macOS can be extended
   to also regenerate the docs; the Linux `jvm-test` job can
   run `:kompact:dokkaGfm` as an additional task if the
   output is committed and needs to stay in sync with the
   source).

### What does NOT change

- **The tutorial stays hand-maintained.** Tutorials are
  acquisition-form (Diátaxis) and don't lend themselves to
  generation; the wire-byte pinning (`0xA5 0x40`) and the
  expected-output assertions are the tutorial's value, and
  those are hand-written prose around the verified
  `GettingStartedTest`.
- **The architecture doc stays hand-maintained.** It covers
  the *why* (LSB-first, zero-alloc, value classes, error
  encoding, framing, versioning), which is explanation-form
  (Diátaxis) and is not what Dokka generates.
- **`docs/README.md` stays as the navigation index.** No
  change.
- **`docs/ci.md` stays as the CI how-to.** No change.

### Propagation

- **Ticket 11** (`KompactWriter.bitCursor` visibility) — the
  `docs/api-reference.md` rewrite (when it lands as part of
  the Dokka follow-up) will reflect whatever 11 decides
  (the `bitCursor` row in the public-surface table is added
  or removed based on the visibility decision). Note in
  11's body; do not resolve.
- **The follow-up Dokka commit** depends on the public surface
  stabilizing. The implementation commits for tickets 01 +
  02 + 04 + 05 + 07 land the new public symbols
  (`ScalarType`, `readScalarAsLong`, the `…OrThrow`
  wrappers, the `Kompact.Result` namespace, the `getOrElse` /
  `map` extensions, `NestedRegionResult`, the framing
  companion functions). The Dokka follow-up runs *after*
  those land, so the generated reference is in sync from the
  start.

## Comments
