# Mutation disposition: invalid-input and equivalence audit

This disposition preserves the source-level audit of all 38 earlier `EQUIV` rows and adds fresh single-class union runs for the runtime and KSP adapters. The union runs directly test every mutation discovered in those runs against the combined selected behavior-test methods. It does not claim 100% mutation kills or stable identities across separate historical reports.

## Fresh aggregate union runs

Each selected module originally had multiple independent MutFlow adapter classes. The matching `*UnionMutationTest` adapter now contains every existing selected delegate method in one class-level scope; original adapters and behavior tests remain intact. Runtime has 81 methods; KSP has 66, including its existing edge-case and boundary delegates. Fresh JSON lists every union method and paired XML contains one suite per aggregate class.

### Runtime union: `:kompact`

- Adapter: `ch.trancee.kompact.runtime.KompactCursorUnionMutationTest`; filter is `-PmutationTest.includes=ch.trancee.kompact.runtime.KompactCursorUnionMutationTest`. Command used `gradle -PmutationTest.jvmOnly=true :kompact:mutationResults` with `--rerun-tasks --no-build-cache --no-configuration-cache --console=plain`.
- Configured deep budget: `maxMutationRuns = Int.MAX_VALUE`; all discovered mutations were evaluated.
- Fresh JSON UTC: `2026-10-05T12:48:25.170Z`; schema **2**; discovered/evaluated/untested **413/413/0**.
- Killed/survived/timed out **393/12/8**; gaps **0**; score **95.16%**, Excellent, High confidence; recorded 95% interval **92.64–96.84%**.
- Paired XML suite: **33,546 testcases**, **116 failures**, **0 errors**, **0 skipped**. Failures are 104 timed-out test invocations plus 12 survivor outcomes, not baseline failures.
- Gradle exited nonzero under strict MutFlow policy due to survivors/timeouts; compilation and discovery completed.

### KSP union: `:kompact-ksp`

- Adapter: `ch.trancee.kompact.ksp.gen.GeneratorUnionMutationTest`; filter is `-PmutationTest.includes=ch.trancee.kompact.ksp.gen.GeneratorUnionMutationTest`. Command used `gradle -PmutationTest.enabled=true :kompact-ksp:mutationResults` with `--rerun-tasks --no-build-cache --no-configuration-cache --console=plain`.
- Configured deep budget: unlimited (`Int.MAX_VALUE`); all discovered mutations were evaluated.
- Fresh JSON UTC: `2026-10-05T12:52:33.503Z`; schema **2**; discovered/evaluated/untested **233/233/0**.
- Killed/survived/timed out **229/4/0**; gaps **0**; score **98.28%**, Excellent, High confidence; recorded 95% interval **95.67–99.33%**.
- Paired XML suite: **15,448 testcases**, **4 failures**, **0 errors**, **0 skipped**. Each failure is a survivor, not a baseline failure.
- Gradle exited nonzero under strict survivor policy; compilation and discovery completed.

Within each fresh report, `(sourceLocation, originalOperator, variantOperator)` tuples are unique. These are direct union-scope outcomes, not guesses from cross-adapter killer tuples. Every configured source target appears in the union report:

| Module | Production source target | Mutations | Killed | Survived | Timed out |
|---|---|---:|---:|---:|---:|
| runtime | `KompactRuntime.kt` | 20 | 14 | 0 | 6 |
| runtime | `KompactCursor.kt` | 95 | 86 | 7 | 2 |
| runtime | `KompactCursorByteRanges.kt` | 128 | 125 | 3 | 0 |
| runtime | `KompactCursorRepeats.kt` | 151 | 149 | 2 | 0 |
| runtime | `KompactByteRange.kt` | 19 | 19 | 0 | 0 |
| KSP | `FramedClassGenerator.kt` | 29 | 29 | 0 | 0 |
| KSP | `FramedHolderGenerator.kt` | 44 | 44 | 0 | 0 |
| KSP | `FramedScalarHolderGenerator.kt` | 50 | 50 | 0 | 0 |
| KSP | `ValueClassGenerator.kt` | 45 | 45 | 0 | 0 |
| KSP | `ValueHolderGenerator.kt` | 65 | 61 | 4 | 0 |


## Prior separate-adapter report pairs (superseded)

### runtime

- JSON generated UTC: `2026-10-05T12:33:16.529000+00:00`; schema **2**.
- Discovered/evaluated/untested: **619/619/0**.
- Killed/survived/timed out: **512/91/16**; execution gaps: **0**.
- Score: **82.71%**; band **Excellent**; confidence **High**; recorded 95% interval **79.54–85.49%**.
- JSON class-qualified methods: 81; paired XML suites: 3; XML tests/failures/errors/skips: **19104/195/0/0**.
- XML UTC timestamps: `2026-10-05T12:31:30.145Z`, `2026-10-05T12:31:48.904Z`, `2026-10-05T12:32:09.398Z`.

### KSP

- JSON generated UTC: `2026-10-05T12:16:28.994000+00:00`; schema **2**.
- Discovered/evaluated/untested: **429/429/0**.
- Killed/survived/timed out: **386/43/0**; execution gaps: **0**.
- Score: **89.98%**; band **Excellent**; confidence **High**; recorded 95% interval **86.77–92.47%**.
- JSON class-qualified methods: 66; paired XML suites: 4; XML tests/failures/errors/skips: **7503/43/0/0**.
- XML UTC timestamps: `2026-10-05T12:16:22.977Z`, `2026-10-05T12:16:25.307Z`, `2026-10-05T12:16:26.292Z`, `2026-10-05T12:16:28.599Z`.

At the earlier equivalence-audit checkpoint, runtime was rerun after regression additions and KSP was unchanged. Those reports are retained here only as historical separate-adapter evidence; the fresh aggregate union reports above supersede them for current scope. XML failures were mutation outcomes, not baseline failures.

## Audit of all previous EQUIV rows

The source locations below are the earlier report locations, before the reset KDoc added lines; current-source mapping and new disposition are explicit. All 38 old rows are listed individually, including duplicates for each adapter class. `RETAINED EQUIV` is used only when the whole public diagnostic outcome is unchanged. `KILLED` and `CROSS-ADAPTER SCOPE` are not equivalence claims.

| Prior module | Prior adapter | Prior source tuple | Audit result | Exact evidence / full-contract reasoning |
|---|---|---|---|---|
| runtime | `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactCursor.kt:71` `< → <=` | RECLASSIFIED: KILLED | Fresh `resetKeepsNegativePositionDiagnosticWhenStartIsZero` asserts INVALID_BOUNDS, errorBitOffset 0, errorDetail -1, and preservation of the previous binding; killer `KompactCursorBoundaryMutationTest::delegates_21()`. Current source line 75. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactCursor.kt:71` `0 → 1` | RECLASSIFIED: KILLED | Fresh `resetKeepsNegativePositionDiagnosticWhenStartIsZero` asserts INVALID_BOUNDS, errorBitOffset 0, errorDetail -1, and preservation of the previous binding; killer `KompactCursorBoundaryMutationTest::delegates_21()`. Current source line 75. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactCursor.kt:73` `< → <=` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactCursor.kt:73` `< → >` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactCursor.kt:244` `< → <=` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactCursor.kt:244` `64 → 65` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactCursor.kt:99` `> → >=` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactCursor.kt:274` `< → <= #2` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactCursor.kt:274` `63 → 64` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursorByteRanges.kt:153` `> → >=` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursorByteRanges.kt:76` `> → >=` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursorByteRanges.kt:239` `> → >=` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursor.kt:99` `> → >=` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:71` `< → <=` | RECLASSIFIED: X-SCOPE | This adapter still survives; the identical tuple is killed by `KompactCursorBoundaryMutationTest::delegates_21()`. No stable mutant ID, so it is cross-adapter scope evidence, not a kill in this adapter. Current source line 75. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:71` `0 → 1` | RECLASSIFIED: X-SCOPE | This adapter still survives; the identical tuple is killed by `KompactCursorBoundaryMutationTest::delegates_21()`. No stable mutant ID, so it is cross-adapter scope evidence, not a kill in this adapter. Current source line 75. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:73` `< → <=` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:73` `< → >` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:244` `< → <=` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:244` `64 → 65` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:274` `< → <= #2` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:274` `63 → 64` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:76` `> → >=` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:239` `> → >=` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorRepeats.kt:268` `> → >=` | RECLASSIFIED: KILLED | Corrupted caller-retained checkpoint regression asserts BAD_LENGTH_PREFIX and the next-prefix errorBitOffset; fresh killer `KompactCursorMutationTest::delegates_38()`. Current source line 268. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorRepeats.kt:277` `> → >=` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorRepeats.kt:285` `> → >=` | RECLASSIFIED: KILLED | Boundary-ending corrupted checkpoint regression asserts BAD_LENGTH_PREFIX and the next-prefix errorBitOffset; fresh killer `KompactCursorMutationTest::delegates_39()`. Current source line 285. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorRepeats.kt:305` `> → >=` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorRepeats.kt:215` `* → /` | RECLASSIFIED: KILLED | The `* → /` and `+ → -` variants corrupt the externally visible checkpoint offset; regression asserts `[8, 520]`, killer `KompactCursorMutationTest::delegates_32()`. Current source line 215. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorRepeats.kt:215` `+ → -` | RECLASSIFIED: KILLED | The `* → /` and `+ → -` variants corrupt the externally visible checkpoint offset; regression asserts `[8, 520]`, killer `KompactCursorMutationTest::delegates_32()`. Current source line 215. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorRepeats.kt:213` `< → >` | RECLASSIFIED: KILLED | Fixed-repeat checkpoint offsets are observable through the caller-owned IntArray alias; regression asserts `[8, 520]`, killer `KompactCursorMutationTest::delegates_32()`. Current source line 213. |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:95` `> → >=` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:95` `0 → -1` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:187` `> → >=` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:187` `0 → -1` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| KSP | `ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest` | `ValueHolderGenerator.kt:95` `> → >=` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| KSP | `ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest` | `ValueHolderGenerator.kt:95` `0 → -1` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| KSP | `ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest` | `ValueHolderGenerator.kt:187` `> → >=` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |
| KSP | `ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest` | `ValueHolderGenerator.kt:187` `0 → -1` | RETAINED EQUIV | See the source-specific full-contract proof below; all failure status and error-detail fields are unchanged. |

Audit disposition: **38 rows** = 7 RECLASSIFIED: KILLED, 29 RETAINED EQUIV, 2 RECLASSIFIED: X-SCOPE. Nine rows were withdrawn from EQUIV: seven have direct fresh kills, and the two main-adapter reset rows are cross-adapter scope survivors. The remaining 29 rows have the proofs below.

## Source-by-source equivalence proofs retained

### Reset branch comparison, current `KompactCursor.kt:77` (prior line 73)

The `when` arm returns `endBit`; when it is not selected, `invalidDetail` is zero and the fallback also sets `errorDetail = endBit`, including when `endBit == 0`. The earlier `startBit < 0` and `position < startBit` arms retain their priority, and `errorBitOffset` is computed independently. Thus changing only `endBit < position` to `<=` or `>` changes which expression supplies the same detail, not status, error offset/detail, or retained binding. This holds for malformed overlapping bounds and out-of-buffer bounds.

### Signed and unsigned width arithmetic

- **Signed, current `KompactCursor.kt:248` (prior line 244), `< → <=` and `64 → 65`:** the public validator rejects every width outside `1..64` before this branch. At width 64 the added calculation has shift zero, minimum `Long.MIN_VALUE`, maximum `Long.MAX_VALUE`, and accepts every Long as the original branch skip does. Widths 1–63 are unchanged. Invalid widths retain `STATUS_INVALID_WIDTH` and width detail; successful validation clears diagnostics in either case. Boundary tests cover both signed extremes; `Long.MAX_VALUE` was added explicitly.
- **Unsigned Long, current `KompactCursor.kt:278` (prior line 274), comparison `#2` and `63 → 64`:** invalid widths return before the condition. Negative values take the first `value < 0L` disjunct, preserving `STATUS_INVALID_VALUE` and width detail. For nonnegative Long values, `value ushr 63` is always zero, so enabling this check at width 63 cannot reject a representable value. Other widths are unaffected. Successful diagnostics clear identically.
- **Byte-offset overflow, current `KompactCursor.kt:103` (prior line 99), `> → >=`:** negative and ordering checks short-circuit first. Otherwise `endByte >= 0` and `endByte * 8` is a multiple of eight, so it cannot equal `Int.MAX_VALUE`. Both operators reject the same bounds with `STATUS_INVALID_BOUNDS`, current-position offset, and `endByte` detail.

### Int.MAX_VALUE byte-length guards

At current `KompactCursorByteRanges.kt:76` (`readNested`), `:153` (`skipByteRange`), and `:239` (`readPrefixedRange`), the unnumbered `> → >=` survivor changes the first comparison, `length > Int.MAX_VALUE`; each source line also contains a second `payloadEnd > endBit` comparison. MutFlow marks the second occurrence as `>= #2`; fresh reports kill that payload-bound mutation with exact-fit delegates. These are distinct mutants.

The supported 32-bit prefix is unsigned, so if decoded length equals `Int.MAX_VALUE`, `length * 8` exceeds every representable cursor end (`endBit <= Int.MAX_VALUE`), even before adding nonnegative payload start. Thus the second payload bound is already true. If length is larger, both first-comparison variants are true. Both return `STATUS_BAD_LENGTH_PREFIX`, offset `cursor.position`, detail `prefixWidth`, and leave parent position plus child/range binding unchanged. Widths 8 and 16 cannot reach this boundary. Fresh `intMaxLengthPrefixesKeepBadLengthDiagnosticsAndBindingsUnchanged` tests exact Int.MAX_VALUE prefixes for nested, range, and skip reads.

### Repeat lookup length guards

The remaining unnumbered guards are current `KompactCursorRepeats.kt:277` (skipped element) and `:305` (target element). A 32-bit prefix can equal `Int.MAX_VALUE`; the existing later `elementEnd > workspace.endBit` / `payloadEnd > workspace.endBit` test must then fail because workspace end offsets fit in Int. Both paths return `STATUS_BAD_LENGTH_PREFIX`, `errorBitOffset = elementStart`, and `errorDetail = elementPrefixWidth`, without advancing the parent or binding the element cursor. `>` and `>=` therefore preserve status, diagnostics, and bindings. Fresh exact-maximum-prefix regressions exercise both cases after successful repeat binding.

Old guards at `:268` and `:285` were not equivalent when caller-retained checkpoint storage is corrupted: equality could change the public error offset. They are removed from EQUIV and killed by delegates 38/39. The fixed-repeat loop was also not equivalent: writes to caller-owned checkpoint storage are observable and are now asserted.

### KSP trailing-zero gaps and validation behavior

The eight old KSP rows are four code-generation variants at `ValueHolderGenerator.kt:95` (decoder) and `:187` (encoder), each recorded in two adapter classes. For a validated model, changing `trailingBits > 0` or `0 → -1` can emit only an additional `skipBits(0)` or `writeZeros(0)`. Generated source text differs, but is not the public wire/API contract. At runtime the zero operations leave position, `valueBits`, and bytes unchanged.

This remains equivalent on errors: decoder preflights the complete frame and returns its exact status/offset/detail if unavailable; checked field reads return immediately on failure. Encoder preflights capacity, validates every constrained scalar before writing, and returns the first validation status/detail before reaching the trailing gap. If checks succeed, previous operations have already cleared diagnostics; the added zero operation clears only already-clear diagnostics. Zero-size layouts are likewise preflighted. Thus invalid-buffer and invalid-value behavior, error details, cursor fields, and output bytes are unchanged. Each `ValueClassGenerator` entry point calls `requireValidLayout` (`LayoutValidator.validateAll`) before invoking `ValueHolderGenerator`; if validation returns layout errors, generation fails before code is emitted.

## Historical per-adapter survivor inventory (superseded; no stable mutant IDs)

`EQUIV` below is limited to the proven tuples above. `X-SCOPE` means another selected adapter reports the identical source/operator tuple killed; no stable mutant ID exists, so this is evidence rather than proof of identical bytecode identity. `UNRESOLVED` rows, if any, are called out explicitly.

| Module | Adapter testClass | Current source location | Operator change | Disposition | Evidence / rationale |
|---|---|---|---|---|---|
| runtime | `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactCursor.kt:77` | `< → <=` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactCursor.kt:77` | `< → >` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactCursor.kt:248` | `< → <=` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactCursor.kt:248` | `64 → 65` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactCursor.kt:103` | `> → >=` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactCursor.kt:278` | `< → <= #2` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactCursor.kt:278` | `63 → 64` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursorByteRanges.kt:153` | `> → >=` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursor.kt:103` | `< → <= #2` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_09() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursor.kt:103` | `* → /` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_17() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursor.kt:103` | `> → >=` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursorByteRanges.kt:76` | `> → >=` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursorByteRanges.kt:239` | `> → >=` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursor.kt:103` | `< → <=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_19() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursor.kt:103` | `0 → 1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_19() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursor.kt:103` | `0 → -1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_19() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursor.kt:103` | `\|\| → &&` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_19() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursor.kt:103` | `\|\| → && #2` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_17(), ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_19() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactRuntime.kt:105` | `0 → 1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_14() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactRuntime.kt:108` | `- → +` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_14() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactRuntime.kt:130` | `0 → 1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_08(), ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_14() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactRuntime.kt:133` | `- → +` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_14() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactRuntime.kt:135` | `- → +` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_08(), ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_14() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursor.kt:68` | `0 → -1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_01() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursor.kt:68` | `&& → \|\|` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_01(), ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_21() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursor.kt:68` | `&& → \|\| #2` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_01(), ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_21() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactCursor.kt:68` | `&& → \|\| #3` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_01(), ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_02(), ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_20() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactByteRange.kt:52` | `> → <` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_01(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_14() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactByteRange.kt:52` | `\|\| → &&` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_01(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_14() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactByteRange.kt:52` | `< → >` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_01() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactByteRange.kt:52` | `0 → -1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_01() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactByteRange.kt:52` | `- → +` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_14() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:101` | `< → <=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_05(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_12() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:101` | `0 → 1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_05(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_12() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:101` | `0 → -1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_05() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:101` | `> → >=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_17() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:101` | `\|\| → &&` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_05() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:107` | `% → /` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_05() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:107` | `> → >=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_05(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_12() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:107` | `\|\| → &&` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_05() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:110` | `> → >=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_05(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_12(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_17() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:263` | `% → /` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_07(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_19() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:266` | `> → >=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_13() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:272` | `> → >=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_07(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_12(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_13() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:275` | `/ → *` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_19() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:276` | `/ → *` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_07() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:75` | `< → <=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_21() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:75` | `< → >` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_01(), ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_20() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:75` | `0 → 1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_21() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:75` | `0 → -1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_01() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:76` | `< → <=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_20() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:76` | `< → >` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_01(), ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_21() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:77` | `< → <=` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:77` | `< → >` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:82` | `== → !=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_01(), ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_20(), ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_21() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:248` | `< → <=` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:248` | `64 → 65` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:248` | `64 → 63` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_15() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:252` | `< → >` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_05(), ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_15() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:252` | `> → >=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_05(), ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_15() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:278` | `< → <=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_12() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:278` | `0 → 1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_12() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:278` | `0 → -1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_18() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:278` | `< → <= #2` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:278` | `63 → 64` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:278` | `63 → 62` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_12(), ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_15() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:294` | `== → !=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_05(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_07() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:65` | `% → /` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_04() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:70` | `> → >=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_04(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_12() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:76` | `> → >=` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:76` | `> → >= #2` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_04(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_12(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_17() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactByteRange.kt:40` | `< → <=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_03(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_07(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_19() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactByteRange.kt:40` | `0 → 1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_03(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_07(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_19() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactByteRange.kt:40` | `0 → -1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_01() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactByteRange.kt:40` | `< → <= #2` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_09() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactByteRange.kt:40` | `\|\| → &&` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_01() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactByteRange.kt:40` | `\|\| → && #2` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_01() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:230` | `% → /` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_06() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:234` | `> → >=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_12() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:239` | `> → >=` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorByteRanges.kt:239` | `\|\| → &&` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_06(), ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest::delegates_20() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactRuntime.kt:130` | `0 → 1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_08(), ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_14() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactRuntime.kt:133` | `- → +` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_14() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactRuntime.kt:135` | `- → +` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_08(), ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_14() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorRepeats.kt:277` | `> → >=` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursorRepeats.kt:305` | `> → >=` | EQUIV | See the matching source-specific proof above. |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactRuntime.kt:105` | `0 → 1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_14() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactRuntime.kt:108` | `- → +` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_14() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:68` | `0 → -1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_01() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:68` | `&& → \|\|` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_01(), ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_21() |
| runtime | `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactCursor.kt:68` | `&& → \|\| #2` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_01(), ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest::delegates_21() |
| KSP | `ch.trancee.kompact.ksp.gen.FramedScalarHolderGeneratorMutationTest` | `FramedHolderGenerator.kt:250` | `isScalar → !isScalar` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.FramedClassGeneratorMutationTest::delegates_09() |
| KSP | `ch.trancee.kompact.ksp.gen.FramedScalarHolderGeneratorMutationTest` | `FramedHolderGenerator.kt:255` | `isScalar → !isScalar` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.FramedClassGeneratorMutationTest::delegates_09() |
| KSP | `ch.trancee.kompact.ksp.gen.FramedScalarHolderGeneratorMutationTest` | `FramedHolderGenerator.kt:254` | `<anonymous>() → removed` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.FramedClassGeneratorMutationTest::delegates_09() |
| KSP | `ch.trancee.kompact.ksp.gen.FramedScalarHolderGeneratorMutationTest` | `FramedHolderGenerator.kt:223` | `== → !=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.FramedClassGeneratorMutationTest::delegates_10() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:127` | `< → <=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_01() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:127` | `64 → 65` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_01() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:34` | `<anonymous>() → removed` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_01() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:83` | `- → +` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_02(), ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:84` | `> → >=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:84` | `> → <` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_02(), ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:84` | `0 → 1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:84` | `0 → -1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:98` | `<anonymous>() → removed` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_02() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:50` | `<anonymous>() → removed` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_01() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:179` | `- → +` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_01(), ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_02(), ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:180` | `> → >=` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:180` | `> → <` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_01(), ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_02(), ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:180` | `0 → 1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_01(), ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:180` | `0 → -1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:178` | `<anonymous>() → removed` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_01(), ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_02(), ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:73` | `* → /` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:94` | `- → +` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:95` | `> → >=` | EQUIV | See the matching source-specific proof above. |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:95` | `> → <` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:95` | `0 → 1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:95` | `0 → -1` | EQUIV | See the matching source-specific proof above. |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:33` | `<anonymous>() → removed` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_01() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:140` | `* → /` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_01(), ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:49` | `<anonymous>() → removed` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_01() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:186` | `- → +` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_01(), ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:187` | `> → >=` | EQUIV | See the matching source-specific proof above. |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:187` | `> → <` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_01(), ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:187` | `0 → 1` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest::delegates_03() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest` | `ValueHolderGenerator.kt:187` | `0 → -1` | EQUIV | See the matching source-specific proof above. |
| KSP | `ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest` | `ValueClassGenerator.kt:31` | `<get-mutable>() → !<get-mutable>()` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest::delegates_28() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest` | `ValueClassGenerator.kt:30` | `requireValidLayout() → removed` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest::delegates_27() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest` | `ValueClassGenerator.kt:361` | `<anonymous>() → removed` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest::delegates_44() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest` | `ValueClassGenerator.kt:367` | `<anonymous>() → removed` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest::delegates_03(), ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest::delegates_15(), ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest::delegates_29() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest` | `ValueClassGenerator.kt:42` | `requireSupportedType() → removed` | X-SCOPE | Identical tuple killed in another selected adapter; no stable mutant ID. Killer(s): ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest::delegates_26(), ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest::delegates_31(), ch.trancee.kompact.ksp.gen.ValueClassGeneratorMutationTest::delegates_35() |
| KSP | `ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest` | `ValueHolderGenerator.kt:95` | `> → >=` | EQUIV | See the matching source-specific proof above. |
| KSP | `ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest` | `ValueHolderGenerator.kt:95` | `0 → -1` | EQUIV | See the matching source-specific proof above. |
| KSP | `ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest` | `ValueHolderGenerator.kt:187` | `> → >=` | EQUIV | See the matching source-specific proof above. |
| KSP | `ch.trancee.kompact.ksp.gen.ValueHolderGeneratorMutationTest` | `ValueHolderGenerator.kt:187` | `0 → -1` | EQUIV | See the matching source-specific proof above. |

Historical per-adapter records: **134** (29 source-reviewed EQUIV hypotheses and 105 X-SCOPE rows); exact historical row-to-union identity mapping remains unresolved.

## Historical per-adapter runtime timeout records (superseded)

The superseded runtime separate-adapter JSON contained 16 `TimedOut` records (eight source/operator descriptions repeated across selected adapters):

| Adapter | Source location | Operator change |
|---|---|---|
| `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactRuntime.kt:28` | `> → >=` |
| `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactRuntime.kt:28` | `0 → -1` |
| `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactCursor.kt:315` | `> → >=` |
| `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactCursor.kt:315` | `0 → -1` |
| `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactRuntime.kt:105` | `> → >=` |
| `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactRuntime.kt:105` | `0 → -1` |
| `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactRuntime.kt:130` | `> → >=` |
| `ch.trancee.kompact.runtime.KompactCursorBoundaryMutationTest` | `KompactRuntime.kt:130` | `0 → -1` |
| `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactRuntime.kt:105` | `> → >=` |
| `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactRuntime.kt:105` | `0 → -1` |
| `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactRuntime.kt:130` | `> → >=` |
| `ch.trancee.kompact.runtime.KompactCursorByteRangeMutationTest` | `KompactRuntime.kt:130` | `0 → -1` |
| `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactRuntime.kt:130` | `> → >=` |
| `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactRuntime.kt:130` | `0 → -1` |
| `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactRuntime.kt:105` | `> → >=` |
| `ch.trancee.kompact.runtime.KompactCursorMutationTest` | `KompactRuntime.kt:105` | `0 → -1` |

The paired runtime XML identifies these as MutFlow `MutationTimedOutException` failures at a finite per-test wall-clock budget of 1000–1018 ms; no timeout override is configured here. These mutants change `remaining > 0` to `>= 0` or a zero sentinel to `-1`, causing a zero-sized chunk to repeat without reducing `remaining`. This is mutant-induced nontermination on exercised calls, not a build/infrastructure timeout. Keep them as timed-out outcomes; do not suppress/filter them. The separately documented 15-minute executor backstop was not reached.

## Current union survivor disposition

The previous 134-row ledger is not a stable-ID list. The union runs are authoritative for current scope: every discovered mutation was evaluated with the combined selected tests, with zero untested outcomes and zero execution gaps. Historical row-to-record reconciliation remains unresolved because the tool publishes no stable mutant IDs. Current survivors are individually classified below from the fresh union reports; no tuple-based retrospective kill claim is made.

### Runtime: 12 surviving mutants, source-proven equivalent

| Source location | Mutation | Full-contract disposition |
|---|---|---|
| `KompactCursor.kt:77` | `< → <=` | The selected branch returns `endBit`, and fallback reports the same value; status, error offset/detail, and retained binding are unchanged. |
| `KompactCursor.kt:77` | `< → >` | Same fallback equivalence, including malformed overlapping bounds. |
| `KompactCursor.kt:248` | `< → <=` | Width 64 is valid; the added full-width signed range calculation accepts every `Long`. Widths 1–63 are unchanged; invalid widths return earlier with the same diagnostic. |
| `KompactCursor.kt:248` | `64 → 65` | The width-64 path remains a no-op validation path; allowed widths and status/diagnostics do not change. |
| `KompactCursor.kt:278` | `< → <= #2` | For nonnegative `Long`, `value ushr 63` is always zero; negative values fail the earlier disjunct. Status and width detail do not change. |
| `KompactCursor.kt:278` | `63 → 64` | The added width-63 check cannot reject a nonnegative `Long`; invalid widths and negative values keep their earlier failures. |
| `KompactCursor.kt:103` | `> → >=` | Earlier ordering checks ensure `endByte * 8` cannot equal `Int.MAX_VALUE`; both forms reject the same bounds with identical offset/detail. |
| `KompactCursorByteRanges.kt:76` | `> → >=` | A 32-bit prefix equal to `Int.MAX_VALUE` already makes payload end exceed every representable cursor end. Both forms return `STATUS_BAD_LENGTH_PREFIX` with the same offset, prefix-width detail, and failure-atomic state. |
| `KompactCursorByteRanges.kt:153` | `> → >=` | Same exact maximum-length proof for `skipByteRange`; parent position and range binding remain unchanged on failure. |
| `KompactCursorByteRanges.kt:239` | `> → >=` | Same proof for `readPrefixedRange`; validation and range/cursor commits are not reached on failure. |
| `KompactCursorRepeats.kt:277` | `> → >=` | Length equal to `Int.MAX_VALUE` fails the later payload-end guard with the same status, element-start offset, prefix-width detail, and no binding/advance. |
| `KompactCursorRepeats.kt:305` | `> → >=` | Same payload-end and diagnostic proof for the target element. |

These are surviving outcomes, not empirical kills. Equivalence is supported by valid input domains, current source flow, and existing boundary/error tests. No ignore comments or exclusions were added.

### KSP: 4 surviving mutants, zero-trailing-gap no-ops

| Source location | Mutation | Full behavior disposition |
|---|---|---|
| `ValueHolderGenerator.kt:95` | `> → >=` | Only zero trailing bits changes, adding `skipBits(0)`. It calls `ensureAvailable(0)`, does not change position or `valueBits`, and clears diagnostics already clear after preflight and successful reads. |
| `ValueHolderGenerator.kt:95` | `0 → -1` | Same zero-gap effect; validated layouts cannot have negative trailing bits. |
| `ValueHolderGenerator.kt:187` | `> → >=` | Only zero trailing bits changes, adding `writeZeros(0)`. Preflight succeeds; the write loop does not run; buffer, position, and `valueBits` remain unchanged; diagnostics were already clear. |
| `ValueHolderGenerator.kt:187` | `0 → -1` | Same zero-gap effect; negative trailing gaps cannot arise after layout validation. |

On invalid buffers or invalid constrained values, generated decode/encode returns before the trailing-gap operation, preserving status and details. On successful zero-trailing layouts, original and mutated code have the same status, cursor state, holder values, and bytes. Emitted source text may differ, but is not a documented runtime/API contract. These survivors are source-proven equivalent, not kills or scope gaps.

## Runtime timeouts: exactly 8 distinct mutants

The JSON has **8 distinct `TimedOut` mutation records**. Paired XML has **104 timeout invocation failures**, because the same mutation can be triggered by multiple delegated methods. Each XML message reports an approximately **1,000–1,009 ms per-test wall-clock budget**. Project/module configuration has no explicit MutFlow timeout override; no `MUTFLOW_*` override was present for these runs. The 15-minute task backstop is a separate execution-gap mechanism and was not reached. No mutants were suppressed or filtered.

| Source location | Mutation | Timeout invocations | Method identities |
|---|---|---:|---|
| `KompactRuntime.kt:28` | `> → >=` | 1 | `delegates_union_043` |
| `KompactRuntime.kt:28` | `0 → -1` | 1 | `delegates_union_043` |
| `KompactRuntime.kt:105` | `> → >=` | 34 | Same 34 methods as the paired `0 → -1` mutation |
| `KompactRuntime.kt:105` | `0 → -1` | 34 | 34 delegated methods |
| `KompactRuntime.kt:130` | `> → >=` | 15 | Same 15 methods as the paired `0 → -1` mutation |
| `KompactRuntime.kt:130` | `0 → -1` | 15 | 15 delegated methods |
| `KompactCursor.kt:315` | `> → >=` | 2 | `delegates_union_055`, `delegates_union_061` |
| `KompactCursor.kt:315` | `0 → -1` | 2 | `delegates_union_055`, `delegates_union_061` |

Each loop processes `remaining` bits. The mutant admits `remaining == 0`; then `chunk = minOf(0, bitsAvailable)` is zero and subtracting it leaves `remaining` at zero forever. These are mutant-induced nontermination results, not untested mutants, equivalences, or build gaps. A longer allowance only delays the zero-progress loop, so the finite invocation timeout remains appropriate and unchanged.

## Completion and limitations

- The cross-adapter **scope** concern is empirically addressed: every mutation discovered by each fresh union run was evaluated with the full selected behavior-test union. Both reports have zero untested mutations and zero execution gaps.
- Exact identity mapping of the 105 historical X-SCOPE ledger rows to fresh outcomes is still unresolved because MutFlow has no stable mutant IDs. This is explicitly not inferred from identical location/operator tuples.
- Current results are not 100% killed: runtime has 12 source-proven equivalent survivors and 8 loop timeouts; KSP has 4 source-proven equivalent survivors. The mutation tasks exit nonzero under strict policy.
- MutFlow 1.6.1 evidence is JVM-only; Android and iOS Native variants are unsupported by the available integration.
- The proposal-only test-refactor review found no demonstrated behavior gap and made no changes. No production implementation changed in this continuation; tests were not deleted or consolidated; no commit was made.
