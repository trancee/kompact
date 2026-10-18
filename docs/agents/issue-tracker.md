# Issue tracker: local Markdown

Kompact does not use GitHub Issues. Issues and specs live as Markdown files
under `.scratch/`.

## Conventions

- Each feature or effort has one directory: `.scratch/<feature-slug>/`.
- The directory holds a map at `.scratch/<feature-slug>/map.md`. A spec, if
  the effort has one, goes in `.scratch/<feature-slug>/spec.md`. No effort has
  a `spec.md` today. One effort, `kompact-pqcble-integration`, has only a
  `proposal.md`.
- Each implementation ticket is its own file at
  `.scratch/<feature-slug>/issues/<NN>-<slug>.md`, numbered from `01`. Do not
  combine tickets in one file.
- Research notes for an effort go in `.scratch/<feature-slug>/research/`.
- A `Type:` line and a `Status:` line near the top of each file record the
  ticket type and the triage state. See
  [`triage-labels.md`](triage-labels.md) for the status strings.
- Comments and conversation history go at the bottom of the file, under a
  `## Comments` heading.

## Publish to the issue tracker

When a skill says "publish to the issue tracker", create a file under
`.scratch/<feature-slug>/`. Create the directory if it does not exist.

## Fetch the relevant ticket

When a skill says "fetch the relevant ticket", read the file at the path it
gives. The user normally passes the path or the issue number.

## Wayfinding operations

The `/wayfinder` skill uses these operations. The map is one file, and each
ticket is one child file.

- Map: `.scratch/<effort>/map.md`. Existing maps use the headings
  `## Destination`, `## Notes`, `## Decisions so far`, and
  `## Not yet specified` (the fog). New maps carry `Type: wayfinder:map` and `Status: wayfinder:map`.
  Older maps carry `Type: map`.
- Child ticket: `.scratch/<effort>/issues/<NN>-<slug>.md`, numbered from `01`,
  with the question in the body. The `Type:` line is one of `research`,
  `prototype`, `grilling`, or `task`. The `Status:` line is `claimed` or
  `resolved` while the wayfinder works on it.
- Blocking: a `Blocked by: NN, NN` line near the top. A ticket is unblocked
  when every ticket it lists is `resolved`.
- Frontier: the files in `.scratch/<effort>/issues/` that are open,
  unblocked, and unclaimed. The lowest number goes first.
- Claim: set `Status: claimed` and save the file before you start work.
- Resolve: append the answer under an `## Answer` heading and set
  `Status: resolved`. Then append a pointer to the map's Decisions so far
  section in `map.md`, with a one-line summary and a link to the ticket.
