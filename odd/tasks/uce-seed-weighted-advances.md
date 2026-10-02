# Repair UCE seed weighted advances

## Authorization and scope
User requested auditing all seeds and continuing if the same defect is confirmed. Source/live audit confirmed fractional rather than weighted percentage-point values for UCE-CON-2026-B. Preserve explicit partial programming, missing periods, weights, review metadata, CMT and Daule; never modify applied historical migrations. No reset or live application authorized. User subsequently explicitly authorized commit and push to main.

## Tasks
- [x] UCE-01: Audit all seed schedules and confirm units and intent against source/live data.
- [x] UCE-02: Guarded V020 conversion with test-first regression and documentation.
- [x] UCE-03: Independent verification and closure; disclose remaining intentional incompleteness.

## Evidence
Read-only verifier enumerated V001–V019 and all three live seed projects: CMT298 advances100/weights100/zero deviations; UCE-B12 weights100 advances9/all12 deviations; UCE-A no budget/schedule. V004:2066–2103 explicitly partial UCE maps: six sum1 over eight periods, six sum0.5 over four. Convert distribution by stored weight, retaining missing keys; raw converted total71.3204500. Parser max scale4 requires deterministic rounding/residual to exact rounded peractivity target (full weight or half weight). Six partial activities must remain deviations and preflight must remain blocked. Whole-schedule guard protects any changed map/cohort/weight/configuration. Existing reviews now present in both projects; preserve without fabricated approval. One CMT weight differs from independently rounded budget ratio: not investigated, not advance defect, do not change.

## Progress
UCE-01 and UCE-02 complete. V020/test/docs implemented on fix/uce-seed-weighted-advances: RED missing migration, GREEN19 tests zero failures/skips; Spotless and diff check pass. Fifteen guarded edit scenarios protect whole schedule; converted total71.3206 with six partial deviations and all exports still blocked intentionally. UCE-03 complete: independent fresh 19 tests passed with zero failures/errors/skips; Spotless and diff check passed. Graphify update completed inside ignored graphify-out; SQL grammar and community warnings remain. Exact converted total71.3206; six partial deviations remain; all three real export preflights correctly blocked. UI, full suite, live application not tested. Native assessment unassessable due root nested repository, therefore independent high-risk verification required. No live application. Commit and push to main subsequently authorized; delivery includes V020, regression tests, seed documentation and this evidence.
