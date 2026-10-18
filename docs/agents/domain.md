# Domain docs

This page tells the engineering skills which domain documentation to read
before they explore the codebase.

## Read these before you explore

Kompact is a single-context repository. Read these sources:

- `CONTEXT.md` at the repository root. The file does not exist yet. When it
  exists, it is the glossary.
- [`docs/adr/`](../adr/). Read the ADRs that touch the area you work in. The
  ADRs are numbered `0001` to `0015` and cover system-wide decisions.
- [`docs/architecture.md`](../architecture.md) for the wire format, the error
  encoding, and the value-class layout.

There is no `CONTEXT-MAP.md` and there are no context-scoped ADR folders.

If a file in this list does not exist, continue without it. Do not flag that
it is missing, and do not suggest that someone create it up front. The
`/domain-modeling` skill creates `CONTEXT.md` and new ADRs when a term or a
decision is resolved.

## Repository layout for domain docs

Kompact uses the single-context layout:

```text
/
├── CONTEXT.md          (not created yet)
├── docs/adr/           (system-wide decisions, 0001 to 0015)
├── kompact/            (runtime)
├── kompact-ksp/        (KSP processor)
└── kompact-gradle-plugin/
```

## Use the project's vocabulary

When your output names a domain concept, use the name that the code and the
docs use. This applies to issue titles, refactor proposals, hypotheses, and
test names. Until `CONTEXT.md` exists, take the names from the public API and
the ADRs: model, view, holder, frame, cursor (`KompactCursor`), writer
(`KompactWriter`), repeat, nested region, length prefix, fixed-layout model,
framed model, `KompactStatus`, and `KompactResult`. Do not use synonyms for
these names.

If the concept you need has no name yet, decide which case applies. Either
you are inventing language that the project does not use, so reconsider, or
there is a real gap, so note it for `/domain-modeling`.

## Flag ADR conflicts

If your output contradicts an existing ADR, say so explicitly. Do not
override the ADR silently:

> _Contradicts ADR-0006 (immutable value-class views by default), but worth
> reopening because..._
