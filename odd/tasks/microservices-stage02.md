# Microservices stage 02 — Gradle and document generation
State: active on feat/microservices-stage02; stage 01 is closed as design only.
Scope: thesis-back-quarkus plan 02; no S3 deployment, frontend migration, cronograma relocation or PDF rebuild.

## Specs
S1. "Ok continua con el plan 2"
S2. "URL temporal de Core con proxy a S3 privado"
S3. "Enlace 5 minutos; archivo 24 horas (recomendado)"
S4. "ok deja el pdf epndiente entonces y esta esta etapa completa para continuar con lo siguiente?"

## Tasks
T1 | S1 | inline, risk 5 | done; commit pending: split Gradle/build/config and CI into two Quarkus apps while preserving root build and Core behavior.
T2 | S1-S3 | inline, risk 2 | in_progress; commit pending: versioned typed projection boundary, service JWT validation and Core capture, no Documentos DB.
T3 | S1 | inline | pending; commit pending: render budget/APU in Documentos from projected DTOs and retain legacy Core GET responses.
T4 | S1 | inline | pending; commit pending: separate specifications DOCX snapshot/render with post-success audit semantics.
T5 | S1-S4 | inline + independent verify (risk 2/5) | pending; commit pending: regression, structural/runtime checks, canonical docs and bounded closure.

## Log
L1. User request verbatim: "Ok continua con el plan 2"
L2. User decisions from stage 01: "URL temporal de Core con proxy a S3 privado" and "Enlace 5 minutos; archivo 24 horas (recomendado)". The public POST/S3/proxy itself is not implemented in plan 02; plan 03 owns storage and browser deployment.
L3. PDF explicitly deferred verbatim: "ok deja el pdf epndiente entonces y esta esta etapa completa para continuar con lo siguiente?"; do not retry unavailable Tectonic verifier.
L4. Source of detailed acceptance and exclusions: plans/separar-en-microservicios/02-build-y-generacion.md, plus odd/tasks/microservices-stage01.md and ../../../thesis-docs/plan/architecture/07-api-contract.md §8. Current single-project root build/src and DB-coupled capture are mapped; isolated branch created from clean stage01 branch. CI, dependencies, service auth and capture concurrency are high-risk.
L5. T1 RED: `./gradlew projects` exit 0 printed `No sub-projects`; committed contract check `bash scripts/check-multiproject.sh` exited 1 with `Missing Gradle application :core`. After split it passed. `./gradlew :core:compileJava :documentos:test -PexcludeKnownResiduals` exit 0 (Core 100 warnings; Documentos health test passed without PostgreSQL). Root `./gradlew build -x test -PexcludeKnownResiduals` exit 0. Moved **511 tracked src files only** into core/src, leaving two ignored legacy private key files under old src/main/resources/META-INF untouched; do not stage or delete them. Both local Docker images built successfully (`propuestas-api:stage02`, `propuestas-documentos:stage02`); Core GET resource regression `PresupuestoApuExportResourceIT` 2/2 passed (0 failures/errors/skips), Documentos health 1/1 passed. Documentos runtimeClasspath has no PostgreSQL/JPA/Flyway; CI matrix/path YAML check passed; the built Documentos container returned `/q/health` UP in 2 seconds without DB configuration and its task-owned ephemeral container was stopped. Two tracked JWT test fixtures were force-staged as exact R100 renames because `.gitignore` otherwise hid their new paths; the two ignored legacy private/public keys remain untouched and unstaged. First root full suite ran 949 tests, 2 failures and 1 skip: both `BrunoCobertura12AdminIT` tests looked up repository-root `api/bruno` relative to the new `core/` Gradle test working directory. Core Test now runs from the repository root (matching the old build); focused Bruno regression 2/2 passed. Second full suite ran 949 tests, 1 failure and 1 skip: `ParametrosProyectoCambioTest` still read `src/main/java` relative to the repository root. `ValorReferenciaNoEntraAlMotorTest` gave a false green against an empty leftover directory. Both structural tests now target `core/src/main/java`; their focused Gradle run passed (9/9); the full root suite passed: Core 949 tests (0 failed/errors, 1 skipped) and Documentos 1 test (0 failed/errors/skipped), including Spotless and both Quarkus builds. Independent T1/S1 structural verifier found no blocking issues; the focused Core GET regression had separately passed. Live CI runner and native Docker build were not executed. `graphify update .` completed after the code/test relocation (AST-only; SQL parser unavailable is a pre-existing graph limitation). No production route semantics were intentionally changed.
