# Project CI mode — backend

Objective: project-level opt-in for individual APU CI; default off for new projects; preserve existing overrides, including all CMT seeded APUs. Dedicated CI save with explicit preserve/reset, transactional recalculation, and an override count for confirmation. Frontend is a later task in the sibling repo.

Decisions: switching off clears overrides; reset clears overrides to NULL, never copies project rate. Preserve only recalculates inherited APUs when project CI changes. Consolidate each affected budget version once. Existing projects with explicit overrides should be enabled in a forward migration; retain existing data. Never edit V001–V016. No SDD. Critical money mutations: first focused failing test, then implementation. No commit absent explicit request.

Tasks:
- [ ] B1: project CI mode persistence, override count, dedicated API and guarded individual endpoint. Integrated commit `ba1cb63`; 48/48 post-commit focused tests passed.
- [ ] B2: V017 migration preserves existing overrides; 298/298 CMT rates unchanged. Integrated commit `ba1cb63`.
- [ ] B3: integrated commit `ba1cb63` covered CI recalculation and guarded APU creation; full suite 797/800 passed (two documented CMT goldens, one skip). Post-commit verifier found unguarded detail mutations; a two-file correction takes the project lock before add/edit/delete and verifies blocking against the holder's PostgreSQL PID, 39/39 focused tests and Spotless passing. Insumo-price mutation race remains a separate cross-module risk.
- [ ] F1: sibling frontend commits `ddbd090` and `e6c6060` cover UI and a critical fraction-to-percentage editor correction; verify 703/703 and build passed.

Checks: independent focused verification, Spotless and diff check pass. Initial backend branch feat/project-ci-mode from 8bccf7a. User authorized local commits, chose future independent PRs to main; no push or PR authorized. `ba1cb63` has 1,644 authored changed lines, above normal 400-line review slice budget; size exception or decomposition required before PR. Native ASSESS was schema-incompatible and inspect reported rdd_disabled, so independent verifier used. Next: commit bounded detail-lock correction, then decide whether to expand into insumo-price concurrency. Resolve CMT goldens separately. Each transition must reconcile with parent task file in thesis-front-react/odd/tasks/project-ci-mode.md; parent owns orchestration and evidence.
