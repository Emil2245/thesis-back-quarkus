# Centro Médico Tulcán seed reconciliation

## Goal
Audit the Centro Médico Tulcán scenario against the authoritative Excel/PDF artifacts and repair seed data so budget rows, APUs, details, and schedule are internally consistent and realistic. Technical specifications text remains out of scope.

## Scope
- Audit `V004__seed_escenarios.sql` and supporting fixtures.
- Reconcile budget/APU identity, completeness, quantities, unit prices, totals, and detail links against the source workbook/artifacts.
- Repair seed data in a new additive migration or the authorized seed source, preserving applied-migration policy.
- Replace unrealistic CMT schedule timing with sequential, plausible activity periods while preserving completed-project semantics and budget linkage.
- Add focused regression checks for CMT seed integrity and schedule shape.
- Run required build/test/graph verification and document residuals or source ambiguities.

## Non-goals
- Do not generate technical specifications for every APU.
- Do not change motor formulas or accepted GM residuals.
- Do not change canonical domain decisions without documenting a blocker.

## Work batches
1. Extract and inventory Excel/PDF/fixture/seed data; produce reconciliation findings.
2. Reconcile budget rows and APU links/details; identify exact repair set.
3. Rebuild realistic CMT schedule timing and verify distributions/weights.
4. Implement additive seed corrections and focused regression tests.
5. Run verification, update documentation, and record evidence.

## Evidence log
- 2026-09-12: `V004__seed_escenarios.sql` is the concentrated scenario seed; CMT has 298 rubros/APUs and 298 activities.
- 2026-09-12: workbook contains 306 sheets: one cover, one budget, and 304 APU sheets; the budget has 298 coded rows and 292 unique codes.
- 2026-09-12: the budget's six duplicated codes are `501AL2`, `500C29`, `500ASU`, `500AT0`, `500AT4`, and `501DH6`; each duplicate is represented in SQL with a `-B` APU code and the corresponding duplicate rubro points to that `-B` APU.
- 2026-09-12: ten budget codes have no matching workbook sheet: `500BFU`, `500BFV`, `500BKC`, `500COH`, `501067`, `501B50`, `501DIH`, `501DS8`, `501DS9`, `504239`; these remain explicit source-gap stubs rather than invented details.
- 2026-09-12: V014 adds 2,543 workbook-derived detail rows across 288 APUs, four sections per populated APU, and deterministic CMT project-base inputs.
- 2026-09-12: V015 assigns each of the 298 CMT activities to one contiguous monthly batch across all 12 periods, preserving existing weights and completed status; no real schedule source exists, so this is explicitly a generated planning pattern.
- 2026-09-12: focused integration tests for V014 and V015 pass; Spotless, `build -x test`, and `git diff --check` pass.
- 2026-09-12: full suite reports 788 tests: 785 pass, 2 accepted pre-existing GM-19/GM-20 failures, 1 skipped GM-24, 0 new failures/errors.

## Work batch 6 — provisional synthetic compositions
- Add an additive migration (do not edit V014/V015) that creates clearly labelled provisional composition rows for the ten source-gap APUs.
- Preserve each existing APU direct/indirect/total values exactly; synthetic detail subtotals must sum to the existing direct cost so the budget total remains unchanged.
- Add comments at the migration header and immediately before each synthetic APU block explaining that the data is temporary, invented, and must be replaced when real source details become available.
- Add regression coverage proving all 298 APUs have four sections/details after the provisional migration and the CMT budget total remains unchanged.

## Evidence log
- 2026-09-24: V016 adds deterministic UUIDv7-compatible project inputs and 40 clearly labelled provisional details (four per source-gap APU), using exact section subtotal splits that sum to each pre-existing `costo_directo`; V014/V015 remain unchanged.
- 2026-09-24: CMT seed and schedule tests pass; `RepresentativeSeedsIT`, Spotless, `build -x test`, and `git diff --check` pass. Full suite is 788 tests: 785 pass, 2 accepted GM-19/GM-20 residuals, 1 skipped GM-24, 0 errors.

## Status
- Current batch: 6 (provisional synthetic compositions)
- Implementation status: complete in working tree; placeholders must be replaced when verified source compositions arrive; no commit requested
