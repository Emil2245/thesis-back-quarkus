# Backend final regression fixes

## Scope

User authorized a bounded attempt to resolve final failures, without SDD/RDD.
Preserve financial calculations, all assertions, original reports, live services,
DB isolation, and the unchanged heap. No automatic main integration or delivery.
Do not claim failed checks passed or mark an unresolved blocker fixed.

Base: `ab3c36ca75064733df427f6c780533a1c93a5dab`, feature branch
`feat/backend-batch-recalculation-apu`; main remains `0757d9a`.
Previous complete partitioned coverage: 114 classes, 895 tests;
891 passed, 3 failed, 1 skipped. This historical result remains unchanged.

## Tasks

- [ ] F01: Isolate the V015 schedule test at Flyway target 15 in
      `src/test/java/ec/uce/propuestas/schema/CentroMedicoTulcanScheduleIT.java`.
      Preserve every assertion. Verify the test and the V018-to-V019 repair test
      against disposable PostgreSQL with test credentials and HTTP port zero.
- [ ] F02: Record a bounded disposition for the two pre-existing Motor goldens,
      the missing EMELNORTE fixture skip, and the unresolved single-JVM OOM.
      No unproven production, golden, fixture, or configuration edits.
- [ ] F03: Verify formatting, source scope and affected checks; report resolved
      failures separately from deferred blockers. Commit requires an explicit
      user request; no staging, commit, merge, push or PR in this attempt.

## Evidence

- Eight Motor/test/fixture/calculation paths and seven schedule/migration paths
  are byte-identical to main. These failures precede the batch optimization.
- Diagnostic Motor run: 4 tests, 1 pass, 2 failures, 1 skip.
  GM-19: expected 395115.32, actual 395108.37; GM-20 chapter 1:
  expected 158908.05, actual 158907.21. Canonical PU truncation and PT precision
  are unchanged; workbook rounding residuals alone do not justify new goldens.
- GM-24 is disabled because the upstream EMELNORTE fixture lacks APU line data;
  the diagnostic XML confirms the skip. No new disable was introduced.
- Diagnostic schedule run: one failure, expected zero invalid activities,
  actual 298. The V015-focused test migrates through V020; V019 intentionally
  replaces V015 full-completion values with weighted advances.
- RED reports are under the original ignored continuation directory, in
  `motor-consolidation-red-20261014T170658Z-245016/` and
  `schedule-it-red-20261014T170822Z-247500/`. Never overwrite them.
- Original monolithic 512 MiB heap exhaustion remains uncorrected. The complete
  partitioned run changes test topology, not the retention cause.

## Verified outcome and bounded disposition

- F01 functional GREEN: 2/2 tests passed, no failures/errors/skips. The V015
  schedule test and the V018-to-V019 repair/idempotence test both passed after
  adding only a comment and `.target("15")` to the migration builder.
- GREEN run: 2026-10-14 17:23:30–17:24:06 UTC, exit 0. Reports are in
  `build/b04-diagnostics/schedule-fix-verify-20261014T172330Z-270235/`.
  PostgreSQL 18 mapped ports: 32863/32861; HTTP: 44293/37177. Init guards and
  fresh DevServices endpoints confirm test isolation; no direct SQL
  connection-metadata assertion was emitted.
- F02 disposition: GM-19/20 are baseline failures, not caused by the batch
  optimization. No financial/golden/tolerance edits are justified by this
  bounded investigation. They remain deferred defects, not passing tests.
- GM-24 skip is now explained by the missing upstream EMELNORTE APU fixture.
  Repairing that source artifact was not attempted; no disable was introduced.
- Single-JVM OOM remains unresolved. Partitioning completed execution, but does
  not establish a retained-object cause or justify a permanent configuration fix.
- F03 checks: SpotlessCheck, scoped Markdown Prettier and diff-check passed;
  all nine protected batch paths match HEAD. Historical RED reports remain.
  Only this test changed in source; no production/configuration/migration edits.

The bounded functional attempt has finished; no further test replay is planned.
The user explicitly authorized one local work-unit commit of this record and
its verified test fix. Task checkboxes and commit evidence are finalized after
observing that commit. Global verification remains blocked by the two Motor
assertions and the single-JVM OOM; this is not approval or integration into main.
