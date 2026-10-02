# Normalize seeded cronograma advances

## Authorization
User approved appropriate solution after read-only diagnosis. Add forward V018 data migration for existing numeric JSONB advances; preserve values, periods, canonical string advances, empty maps, all unrelated data and Daule. No historical migration edits, API relaxation, reset, publish or commits. Apply through Flyway to existing local Compose propuestas and verify.

## Tasks
- [x] ADV-01: Add migration and deterministic regression tests for legacy numeric advance normalization; retain strict API decimal strings; observe RED/GREEN.
- [x] ADV-02: Apply actual Flyway migration locally and independently verify seed advances, snapshot/consolidation regression and preservation; document limitations and close.

## Progress
ADV-01 complete: observedRED17tests/1missingV018failure, GREEN17/17 (migration1 + strictparser16). ActualCMT/UCElinkedAPUrecalculation/consolidation passes isolatedDevServicesPostgres afterV017->V018. SQLexactprecision/scale, mixedstrings/malformedpreservation/nonobject/idempotence tested. ParentSQLreadback+diffcheckPASS. Humanauthorizedbackendpause; already stopped port8090free. ADV-02 complete: actual localFlyway017->018 (checksum1798088090), healthUP;310activities370advancesnumeric->strings, numeric0. ExactexpectedadvanceMD5e4a1e5b87804a1c4cfa41cc9861e60e8; nonadvanceMD583f9ec35a5147a9baa6d8b1570bda259 unchanged. Catalogcounts7/2142/950+digests preserved;old17Flywaychecksumsunchanged. Buildpassedafter scopedSpotlessformatfix. IndependentliveSQLpreservationPASS; independentfresh17/17testPASS via --rerun-tasks inisolatedDevServices. OwnPID928138stopped,8090/8091free. No UI/localAPI POST/fullsuite executed; nextstartbackend and retrytemplateadditioninUI. No reset/reseed/oldmigrationedits/commits. RDDoffassessmentunassessable untrackedfiles, separateverifier required.

## Evidence and acceptance
UCE-CON-2026-B has12activitymaps72numericentries; CMT-2023 has298numericentries. V004/V015 seedednumbers; currentCronogramaService writes Map<String,String>. VersionSnapshotBuilder strictparserrejects persistednumbers duringbudgetconsolidation. Migration changes only numeric JSONvalues to exactdecimalstrings; no float/double/no rounding. Existingstrings remain unchanged. Invalidtypes never silently coerced. Empty/nonobjectsafehandling. Focused APIparsertests and migrationregression; actualFlyway18 + independentread-only comparison. FullAPIuserflow only if isolated transaction/reversiblefixture justified; no auth mutation without need.

## Git
Branch fix/seed-cronograma-decimal-strings created from main; clean initialstatus. No commit unless explicitly requested.
