# Project CI mode — backend

Objective: project-level opt-in for individual APU CI; default off for new projects; preserve existing overrides, including all CMT seeded APUs. Dedicated CI save with explicit preserve/reset, transactional recalculation, and an override count for confirmation. Frontend is a later task in the sibling repo.

Decisions: switching off clears overrides; reset clears overrides to NULL, never copies project rate. Preserve only recalculates inherited APUs when project CI changes. Consolidate each affected budget version once. Existing projects with explicit overrides should be enabled in a forward migration; retain existing data. Never edit V001–V016. No SDD. Critical money mutations: first focused failing test, then implementation. No commit absent explicit request.

Tasks:
- [ ] B1: project CI mode persistence, override count, dedicated API and guarded individual endpoint. Implemented; 9/9 focused integration tests passed; commit pending authorization.
- [ ] B2: V017 migration preserving all existing overrides and seed assertions. CMT 1/1 and CI 4/4 focused tests pass; commit pending authorization.
- [ ] B3: ordinary/manual/lote/duplicate creation shares project lock and project-before-budget ordering; final suite 797/800 pass, two documented CMT goldens and one skip; Spotless passes. No deterministic internal-interleaving harness exists without test-only seam. Commit pending.
- [ ] F1: sibling frontend `pnpm run verify` passed (702/702, build); Chromium modal test and refreshed manual screenshot pass; commit pending.

Checks: B1 focused Quarkus suite 9/9 passing; native ASSESS unassessable due to untracked files. Initial backend branch feat/project-ci-mode from 8bccf7a. Next: explicit work-unit commit authorization and reviewable slices; no commits made. Resolve CMT goldens separately. Each transition must reconcile with parent task file in thesis-front-react/odd/tasks/project-ci-mode.md; parent owns orchestration and evidence.
