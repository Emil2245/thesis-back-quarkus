# Microservices stage 05 — retire legacy byte-download endpoints
State: active on feat/microservices-stage05 (backend, frontend, thesis-docs), branched from stage04.
Scope: remove legacy GET byte downloads and the code only they use; keep preflight GETs, POST exportaciones, ticket GET and Documentos `/internal/v1/exports`.
Delivery: local work-unit commits only, no push/PR (user decision). TDD: strict (global CLAUDE.md); runners `./gradlew` (backend) and project Vitest (frontend).

## Specs
S1. "ok esta bien entonces sontinua con la etapa 5 para quitar codiog deprecado."
S2. Evidence ITs backing CHK: "Portarlas (Recomendado)" — keep the same value assertions through the new flow or Documentos renderers.
S3. Delivery: "Solo commits locales (Recomendado)".

## Acceptance
- `GET /documentos/{presupuesto|apus|cronograma|especificaciones-tecnicas}/{id}` return 404 (no byte route); preflights, POST exportaciones, ticket GET unchanged and green.
- Core main has no POI/OpenPDF writers; `poi-ooxml`/`openpdf` dropped from Core main deps; Documentos dead `/internal/v1/render` and specifications endpoints removed.
- D-13 `documento.exportado` still produced (by POST flow) and covered by tests.
- Frontend has no Blob download path; tests/typecheck/lint/build green.
- Canonical contract (`thesis-docs/plan/architecture/07-api-contract.md`), plans, Bruno, README/CLAUDE.md updated; thesis-deploy k6 moved to POST+ticket.
- Full suites run; GM-19/GM-20 accepted residuals reported, not hidden.

## Tasks
T1 | S1 | delegated writer (fork) | done in fae4ecb — moved nested types out of Core writers (`CronogramaPdfWriter.Papel`, `CronogramaXlsxWriter.{Fila,FilaHoja,CapituloHoja,ProyeccionXlsx}`, `CronogramaMspdiWriter.{ProyeccionMspdi,RubroMspdi}`, `ArchivoGenerado` media types, `DocumentosHttpClient` size limits) into neutral types; behavior-preserving, existing tests green.
T2 | S1-S2 | delegated writer | pending — Core: RED test that legacy GETs 404; remove legacy resources methods, services, render gateways, writers, fonts, deps; delete legacy-only tests; adapt mixed tests (CronogramaExportResourceIT, PresupuestoApuExportResourceIT, LogActividadCoberturaD13CompletaTest, Plan07Uuidv7FronterasTest); port 3 evidence ITs.
T3 | S1 | delegated writer | pending — Documentos: remove InternalRenderResource/InternalSpecificationsResource and tests if no caller remains.
T4 | S1 | delegated writer (writer trigger: 11 frontend files) | done; thesis-front-react 58ac778: legacy Blob download helpers, GET-409 branch, MSW legacy handlers and tests removed; RED hook-exposes-no-legacy test then GREEN; full Vitest 102 files/763 tests, typecheck, oxlint, guard:adr9, build, Prettier, diff check PASS (Node v24.13.1).
T5a | S1 | delegated writer | done; thesis-docs 74cdfdb (07-api-contract §5/§8/App. A, 05-diagramas, 02-catalogo-pruebas, 03-trazabilidad, 07-decisiones note) and thesis-deploy 78a96a6 on new branch feat/microservices-stage05 (k6 common.js POST+Idempotency-Key UUIDv7+ticket GET; `node --check` PASS; k6 not installed, load test not run). Thesis chapters left untouched as historical (L3).
T5b | S1 | delegated writer | pending — backend docs, plans, README/CLAUDE.md, Bruno (after T2/T3).
T6 | S1 | inline | pending — verification: full backend + frontend suites, build, contract script, local stack smoke if available.

## Log
L1. Mapping (read-only explorer): new POST flow uses `RemoteExportUploadGateway`/`DocumentosExportClient`; both render gateways serve only legacy GETs. Only legacy cronograma GET still renders bytes inside Core. No writer is called by the new flow, reservation, audit or Documentos reconcile. `commons-csv` stays (CSV insumo import). D-13 `documento.exportado` already emitted by `ExportReservationService.finalizeAudit`/`finalizeCronogramaAudit`. Visible change: legacy filenames and `X-Cronograma-Desactualizado` header disappear.
L2. T1 done in fae4ecb, pure refactor (no RED: behavior-preserving, safety net = existing tests). New top-level types: `cronograma/export/PapelCronograma` (was `CronogramaPdfWriter.Papel`), `HojaCronograma.{Fila,FilaHoja,CapituloHoja,ProyeccionXlsx}` (was nested in `CronogramaXlsxWriter`), `MspdiCronograma.{RubroMspdi,ProyeccionMspdi}` (was nested in `CronogramaMspdiWriter`), `documento/exportacion/DocumentoMediaTypes` (PrivateDownloadResource now uses it; `ArchivoGenerado` constants delegate), `documento/exportacion/DocumentosLimites` (new-flow parsers/fingerprint/export client use it; `DocumentosHttpClient` constants delegate). Writers still compile against the moved types; user-facing validation messages unchanged. Checks: `:core:spotlessApply` then `:core:spotlessCheck :core:compileTestJava` PASS; `./gradlew :core:test -PexcludeKnownResiduals -Dquarkus.http.test-port=0 --tests '*Cronograma*' --tests '*Export*' --tests '*PrivateDownload*' --tests '*Fingerprint*'` → 38 classes, 217 tests, 0 failures/errors/skips (XML). Full suite deferred to T6.
L3. T5a stale references intentionally untouched (thesis text/dated audits; ask user): thesis-docs `tesis/apendices/anexo-6-documentos.tex:5`, `tesis/capitulos/05-resultados/03-conformidad.tex:57`, `tesis/figuras/capturas/README.md:245`, `tesis/_insumos/estado-real-software.md:240` (dated 2026-10-13 audit, broadly stale). Open check for T2/T6: 07-api-contract §8 says some export 409 return typed preflight `application/json` instead of problem+json — verify POST 409 behavior.
