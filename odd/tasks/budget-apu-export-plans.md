# Budget and APU document export execution plans

## Scope and authorization
Create execution plans only under docs/modulos/07-exportacion-presupuestos-apus/. No implementation, sample modifications, DB operations, commits or pushes. Backend all capabilities and verification must finish before final frontend phase. Preserve pre-existing deleted resources Compunex_Contenido_a_Completar(1).xlsx and Compunex_Contenido_a_Completar.xlsx.

## Tasks
- [x] PLAN-01: Audit backend pipeline, requirements and complete example inventory (9 PDF, 5 XLSX, 3 ZIP without originals).
- [x] PLAN-02: Resolve product scope/presentation with user.
- [x] PLAN-03: Author index and four execution plans, structurally verify consistency and evidence.

## Decisions
Only unique APUs linked to selected budget rubros. XLSX both per-APU tabs and one stacked sheet. Budget XLSX/PDF; PDF A4 default with landscape option for wide columns. APU single PDF A4 portrait, each APU starts new page and long ones continue. Use configured project precision and existing project/responsible data, no new logos/digital signatures. User asks cronograma landscape/A3: current CronogramaPdfWriter.java60-63 hardcodes842x595 A4 landscape; plan compatible A3 landscape option and preserve current default. Reuse mechanical cronograma export pipeline but not schedule completion blockers for budget/APU. Follow v1.3 budget validity preflight and read-only canonical values; no recalculation/versioncreation.

## Progress
PLAN-03 complete on docs/budget-apu-export-plans. Created index plus four proposed execution plans (609 lines). Independent verification PASS: 33 valid local links, 17 inventoried paths, 51 pending implementation checklists, all five export outputs plus cronograma A3 and backend-before-frontend gate. Passive documentation has no meaningful RED; structural/path/link/content checks and git diff --check passed. No Gradle/runtime/full visual verification; these are future implementation checks. Existing precision is global, with project presentation toggles; plans reuse actual configuration rather than inventing per-project precision. No implementation, commit or push.
