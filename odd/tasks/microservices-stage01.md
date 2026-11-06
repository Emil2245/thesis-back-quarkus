# Microservices stage 01 — feasibility and contracts
State: stage 01 design closed; scope is stage 01 only, across thesis-back-quarkus plans and thesis-docs canonical contract.
PDF regeneration is explicitly deferred; no service implementation, deployment or plan 02 work.

## Specs
S1. "Si caro continua con la etapa uno hasta terminar"
S2. "ok deja el pdf epndiente entonces y esta esta etapa completa para continuar con lo siguiente?"
S3. "URL temporal de Core con proxy a S3 privado"
S4. "Enlace 5 minutos; archivo 24 horas (recomendado)"

## Tasks
T1 | S1-S4 | inline | done; backend commit 4d8ec51466d1a9b0e63e3dcd48c36ebc1d931e82: reconciled scope, branch and tracked evidence.
T2 | S1,S3-S4 | inline | done; docs commit 4db588d26d811bea4afef30f24a97f90db411b67; backend commit 4d8ec51466d1a9b0e63e3dcd48c36ebc1d931e82: planned POST/ticket and Core-to-Documentos contract, no service code modified.
T3 | S1-S4 | inline | done; backend commit 4d8ec51466d1a9b0e63e3dcd48c36ebc1d931e82: design gate closed; implementation/deployment checks explicitly belong to plans 02–04.
T4 | S1-S4 | inline + independent verify (security risk) | done; backend commit 4d8ec51466d1a9b0e63e3dcd48c36ebc1d931e82 and docs commit 4db588d26d811bea4afef30f24a97f90db411b67: structural checks, independent verifier design verdict and reviewable commits.

## Log
L1. User request verbatim: "Si caro continua con la etapa uno hasta terminar"
L2. Prior user disposition verbatim: "ok deja el pdf epndiente entonces y esta esta etapa completa para continuar con lo siguiente?"
L3. User selected exactly "URL temporal de Core con proxy a S3 privado" over direct S3 presigned URL; this is a design decision, not evidence of deployed proxy.
L4. User selected exactly "Enlace 5 minutos; archivo 24 horas (recomendado)"; the link and object have independent lifetimes.
L5. Existing local FLoCI 2.4.0 proof and its limits are recorded in plans/separar-en-microservicios/01-factibilidad-y-contratos.md. Integrated deployment 2.3.0 evidence remains historical; no thesis-deploy repository or EKS proof in this workspace. Previously generated manual PDF was explicitly deferred after Tectonic bundle download failed; do not retry it in this task.
L6. Canonical target (not running code) recorded in ../../../thesis-docs/plan/architecture/07-api-contract.md §8 and ../../../thesis-docs/plan/quality/02-catalogo-pruebas.md; local link/whitespace checks and `git -C thesis-docs diff --check` passed. The path was deliberately selected as a Core proxy instead of asserting external S3 browser support; plan 03 now owns actual SDK/pod/Ingress/cleanup/browser proof, and plan 04 owns cronograma consistency.
L7. High-risk independent verifier initially found five security contract gaps; after bounded corrections it confirmed all five closed **at design level**, not implementation. No executable API behavior or EKS/Pod Identity/browser proof exists yet. Source structural/link checks passed; no application tests apply to docs-only changes. Docs feature-branch commits: 91a9964e3dcac6750851f1ccf1808435d2634c56 (historical manual + local 2.4 S3 evidence), 4db588d26d811bea4afef30f24a97f90db411b67 (canonical planned API and quality checks). Native assessor unavailable due unrelated nested Git repo; no retry. An unrelated broken ../thesis-docs reference in pre-existing plans/031-exportacion-cronograma.md remains outside this stage.
L8. Backend feature-branch work unit 4d8ec51466d1a9b0e63e3dcd48c36ebc1d931e82 holds plan 01 closure and plans 02–04 prerequisites. This closing tracker update records those receipts; no push, PR, deployment or functional API claim follows from the commits.
