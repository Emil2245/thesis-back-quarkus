# Daule data-only ingestion plans

## Objective
Create sequential Spanish documentation under `docs/ingreso-datasets/` for loading Daule V1 and future datasets through the existing schema and backend contracts.

## Authorization and constraints
- Authorized now: documentation only; no dataset loading, SQL execution, backend edits, schema changes, migrations, dependency changes, or commits.
- User explicitly requires future ingestion to preserve the existing backend and database schema.
- The earlier separate-catalog/new-table proposal is superseded and NOT authorized.
- Do not create project templates, dummy projects/budgets, new snapshot formats or source-qualified resolver changes.
- Use existing CENTRAL bases, ordinary insumos and SISTEMA APU templates; retain provenance and observations externally where the DB cannot represent them.
- Documentary-price, HM adaptation, units, legal/source gaps remain decision gates, not accepted assumptions.

## Tasks
- [x] DOC-01: Create linked diagnosis, sequential data-only plans and progress ledger; add a discovery link in `docs/INDICE.md`.
  - Acceptance: plans have dependencies, inputs, allowed/forbidden actions, STOP conditions, checklists, evidence and next links; no schema/backend redesign; all future execution starts pending/blocked.
- [x] DOC-02: Independently verify scope, links and factual consistency of the documentation.
  - Acceptance: structural checks pass; no source/schema/dataset writes; limitations and unapproved decisions are explicit.

## Progress
- DOC-01: completed. Nine Spanish documents created with six sequential operational plans; index linked. Writer structural verification: 10 documents / 112 local links / zero errors; diff whitespace clean after correcting nine dataset relative paths.
- DOC-02: completed. Independent verifier checked all documents, links, dataset counts and existing mapper/resolver/admin contracts. It found one ambiguous lock phrase in four documents; corrected mechanically to exclusive transaction-scoped operator serialization, same protocol/key, held through commit/rollback, with unrelated-writer coordination preserved. Final targeted checks: 10 documents, 112 local links, zero errors; 4/4 lock corrections pass; git diff --check clean.

## Verification strategy
Passive Markdown changes have no meaningful deterministic behavior RED/GREEN. Use structural link checks, diff whitespace checks, scope readback and factual review. No Gradle, database or notebook execution is required for this documentation task.

## Evidence
- Backend initially clean on `main`; no commit requested, so no work-unit commit will be created without explicit authorization.
- Prior read-only audit verified V1 CSV counts, relationships, fingerprints and documentary medians. Instance state is unverified.
- Final Git scope: docs/INDICE.md, docs/ingreso-datasets/, this task document only. No database/schema/backend/seed/dataset changes, load, Gradle run or commit.
- Native risk assessment unavailable (parent root contains unrelated untracked repository; explicit backend root requires untracked declaration). Followed returned conservative plan with independent verifier. RDD off; no native lifecycle started.
- Memory mirror is maintained in the active proyecto-grado project because backend satellite registration is unavailable. No claimed backend-project mirror.

## Next step
User can start docs/ingreso-datasets/01-preflight-y-aprobaciones.md and record approved decisions in SEGUIMIENTO.md. Operational plans 01–06 remain pending/blocked; documentation completion is not load authorization.
