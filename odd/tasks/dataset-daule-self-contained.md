# Self-contained Daule V1 dataset

## Authorization and scope
User authorized copying the entire original export directory into backend database/dataset_ingestion/sources/dataset_maestro_daule_v1, preserving original and making the backend independent of sibling repositories. Initial scope was no DB mutation. User subsequently explicitly authorized backend docker compose down -v, clean startup and dataset load to prove reconstruction from scratch. This deletes existing local projects/data; no publishing or commits authorized.

## Tasks
- [x] COPY-01: Copy all export files byte-identically, update default operator/tests, explicitly reseal only operator_hashes/release_sha256 preserving all UUIDs, payload and source hashes; document relocation and historical receipt seal distinction.
- [x] COPY-02: Verify copy and invariants, then reset only backend Compose volume with down -v, start PostgreSQL and backend migrations/seeds, load bundled dataset and verify replay/readback from clean database; close tracking.

## Progress
COPY-01 complete: 16 files,9550677bytes byte-identical, source untouched; observedRED12tests/1intendedfailure thenGREEN12/12. Only2release seal fields changed,2310UUIDs/payload/sourcehashes unchanged; newrelease111a736b9f60ec6ba8ccfe35ce3686f239f0db9be79fa2e7c3a9c8ec66b8c45e. Parent whitespace spotcheckPASS. COPY-02 complete: parent verified canonical thesis-backend target and executed authorized down -v successfully. New PostgreSQL started empty; offline build passed, actual Flyway17 migrations/seeds ran. Initial HTTP startup failed on occupied8090, preserved existing process; temporary8091 startup/healthUP succeeded and ownPID884660 shut down gracefully. Backend-only dry-run/apply/verify/replay/finalverify passed. Independent12tests/copy/seals/full SQLpayload PASS; totals6/2140/950, dataset1/1372/937. Recorded27baselinecount/MD5 entries exactly equal final preserved rows; live MD5 reproduction not performed. Nativeassessmentunassessable dueuntrackedfiles,RDDoff; independentverifier required. No commits requested.

## Acceptance and verification
Observed test-first RED/GREEN for backend-local source default. Complete directory inventory/hash equality; original unchanged. Ten existing tests plus focused relocation/seal tests pass. Writer copying files performs no DB writes. Following copy, delegated clean-rebuild executor may run authorized Compose reset, startup/migrations, dry-run/apply/verify/replay and emit receipts. Functional validation proves clean reconstruction; distinguish any skipped API/runtime checks. Preserve staging. Historical receipts retain historical release checksum, explicitly documented. License remains unverified; no publication.

## Completion evidence
- New receipts under database/dataset_ingestion/releases/daule-v1/: receipt-dry-run-1790917461660039940.json; receipt-apply-1790917471114804611.json; receipt-verify-1790917481410029501.json; receipt-apply-1790917483867238234.json; receipt-verify-1790917485096547019.json. All use new seal111a736b9f60ec6ba8ccfe35ce3686f239f0db9be79fa2e7c3a9c8ec66b8c45e.
- Startup healthUP and shutdown independently confirmed; HTTP200 worker-observed, transport status not durably captured. All27 preservation entries compared from recorded files, not independent liveMD5 recomputation.
- Full Quarkus suite, API adoption/roundtrip, exports and concurrency not tested. Existing Graphify updated; SQL extraction warning missing tree_sitter_sql. No installation, publication or commits.
- PostgreSQL remains running; temporary8091 instance stopped. Original export unchanged. Backend source defaults no longer depend on thesis-docs.
