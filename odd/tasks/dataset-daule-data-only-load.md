# Daule V1 data-only local ingestion

## Objective and authorization
Implement a repeatable operator for adding Daule V1 data to existing `base_insumos`, `insumo` and `plantilla_apu` tables, then load and verify the existing local PostgreSQL from backend Docker Compose.

User approved implementation, the running local Compose database, and destructive volume reset only if needed. Prefer preserving the current database; do not reset without a demonstrated need. No schema/Java/backend redesign, applied seed edits, project templates or fake project containers. No publishing or commits requested.

## Decisions
- Exact canonical insumo identity only; no fuzzy merging. Distinct specifications remain distinct.
- One existing positive original observation per canonical non-HM insumo, selected by stable source/dataset/observation ordering; no averaging. Preserve all source observations externally.
- Target: local backend Compose PostgreSQL `propuestas`; actual schema/seeds verified SELECT-only. No reset needed.
- Current schema stores operational catalog only; aliases, price history and origins remain immutable external CSV evidence.
- User approved 5% HM throughout current adoption, documenting source 1% exceptions (Malecón APUs584/838) for future consideration. Preserve HM as existing snapshot placeholder and original CSV1% values; no project parameter/motor changes. Later project percentage edits still govern templates.
- Local dataset use is authorized; licensing remains unverified and does not authorize redistribution.

## Tasks
- [x] LOAD-01: Inspect live local schema/seeds/collisions and resolve actual compatibility gates.
  Acceptance: SELECT-only baseline recorded, target confirmed, required decisions identified; no resets or writes.
- [x] LOAD-02: Implement standalone data-only preparation/load operator and focused deterministic tests; record source/UUID map and policies in docs.
  Acceptance: test-first RED/GREEN observed for applicable transformations/replay/conflicts; existing Java/schema/seeds untouched, generated artifacts confined to declared surfaces; no secrets.
- [x] LOAD-03: Run authorized local transactional load, exact replay and independent verification.
  Acceptance: seed baseline unchanged, loaded payload readback exact, repeat inserts zero, original-price selection verified; failure or unknown commit never blindly replayed; source HM/CI limits explicit.

## Progress
- LOAD-01: completed. Live SELECT-only schema/seed/collision verification passed; user explicitly approved 5% HM adaptation.
- LOAD-02: completed. Writer RED absent-module, GREEN6/6 then10/10; independent10/10 tests and SQL dry-run PASS. ROLLBACK rehearsal readback1CENTRAL/1372insumos/937templates, existing9table hashes/Flyway unchanged. Durable release/source hashes and current snapshot reviewed; no deterministic blocker.
- LOAD-03: completed for database ingestion. Apply1/1372/937 committed; replay0/0/0 NOOP; two operator verify runs passed. Independent SELECT-only full payload/prices/UUIDs/source15hashes/nine-table preservation checks PASS and10testsPASS. Documentation closure115links/11files zero errors, whitespace clean. Runtime/API and dynamic concurrency checks NOT EXECUTED; global functional status remains PARTIAL, not a claimed application roundtrip.

## Evidence
- Parent `docker compose ps` confirmed postgres18 service up, mapped local port5436.
- Live read-only baseline: 27 public tables; base_insumos5 (CENTRAL1/PERSONAL1/PROYECTO3), insumo768, plantilla_apu13 (SISTEMA12/PERSONAL1), proyecto3,presupuesto2,apu311,rubro310; all17 Flyway migrations successful. CENTRAL DV1 collisions0. Actual system/all3project HM0.0500.
- Content digests: base_insumos c01c58e798ff4d2134ee8b5766e46abf; insumo9c628b6e418109b1d1a56daa7d67a305; plantilla_apu f8c1002a0b60aa19f974b69942dbddd5. Recapture preservation baseline immediately before load; these are not concurrency-frozen.
- Data verification:1372 nonHM insumos each positive original observation; max unit width10, EQ/MO h, all nonHM quantities positive; MAT/TR rendimiento intentionally blank. Two source variants HM1% versus live project5%; user approved current5% with source exceptions documented.
- Backend `main` already contains staged planning Markdown from previous task; preserve staging and do not commit unrelated work.
- Earlier documentation tasks completed; this task supersedes their documentation-only authorization, not their safety contracts.

## Checks and delivery
Delegated writer self-checks focused tests with RED/GREEN; independent verifier executes database load checks if appropriate. No automatic Gradle/server restart or notebook execution. Native review only if user switch enabled. No commit without explicit user request.

## Next step
Database ingestion is complete; next optional user-approved work is testing adoption/search through the running application in a designated project fixture. Do not reopen schema/motor or replay to fake runtime evidence.

## Final evidence and limitations
- Release SHA256 e2e19accb4e723b3c7920f4344cead6a3aadc570a009014a8986bd285fcdff35, original15sourcehashes intact. NOTAS is separate.
- Receipts under database/dataset_ingestion/releases/daule-v1: dry-run1790908072082620946; apply1790908265663817904; verify1790908271288836158; NOOPapply1790908276797904678; finalverify1790908280695717978 (receipt-<operation>-<id>.json).
- Final totals base_insumos6,insumo2140,plantilla_apu950. Existing5/768/13 preserved. Independent2310UUIDv7unique,27tables245columnsFlyway17 confirmed.
- Nine existing-tableMD5preservation digests independently match rehearsal. Worker all27tableSHA9db0ade8b1537e88c26d260edc5ef3875fcdbc71fcb7b1ae1db07bf1706dd526 and completeDDLbaseline are NOT independently reproduced (serialization unavailable); do not overclaim.
- RuntimeAPI/adoption, fullGradle and dynamic concurrency/conflicts not exercised. License/source provenance gaps unchanged. No Dockerreset,Java/schema/migration/seed/sourceCSVchanges or commits. Natural sequence gaps from rollback expected.
- Feature branch feat/daule-v1-data-load; earlier staging preserved. Data-only implementation and documented DB outcome complete; broader application validation pending.
