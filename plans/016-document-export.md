# Plan 016 — Document export (P-37, P-45)

**Status:** TODO
**Priority:** P2
**Effort:** L
**Depends on:** 014, 015
**Written against:** `97280ab`
**Spec:** API contract §8, P-45 (especificaciones tecnicas docx)
**Skill:** activate `/ponytail:ponytail` (full) before writing any code

## Why

The frontend export module and the ET panel expect server-side document generation.
No export package exists yet. The backend already has POI and OpenPDF as
dependencies (`build.gradle.kts`), so the libraries are in place.

## Scope

### In scope

New package `ec.uce.propuestas.documento`:

**Resource:** `DocumentoResource` at `/documentos`
- `GET /apu/{apuId}?formato=xlsx|pdf` — single APU sheet
- `GET /apus/{presupuestoId}?formato=` — all APUs of a version
- `GET /presupuesto/{presupuestoId}?formato=` — budget document
- `GET /cronograma/{presupuestoId}?formato=` — schedule document
- `GET /especificaciones-tecnicas/{presupuestoId}?formato=docx&titulo1=&titulo2=` — ET Word doc

All endpoints:
- Return `StreamingOutput` with `Content-Disposition: attachment`
- Validate user ownership of the presupuesto (via proyecto)
- Check `ValidacionPresupuestoResponse.exportable` -- if not, return 409
  `export-bloqueado` with the checklist items
- Rounding: display precision from `app.display.*` config (default 2 dp money, 4 dp %)

**Services:**
- `ExportApuService` — generates APU sheet (SERCOP format per `04-export-sercop-spec.md`)
- `ExportPresupuestoService` — budget workbook
- `ExportCronogramaService` — schedule workbook
- `ExportEspecificacionesService` — Word doc with one section per APU:
  - Title 1: `titulo_et_1` from proyecto (override via `?titulo1=`),
    default "ESPECIFICACIONES TECNICAS"
  - Title 2: `titulo_et_2` from proyecto (override via `?titulo2=`),
    default `<nombre_proyecto>`
  - Body: `Apu.especificacion_tecnica` (plain text, per-APU section with
    codigo + descripcion header)

**Schema addition for P-45:**
- Migration `V006__add_especificacion_tecnica.sql`:
  ```sql
  ALTER TABLE apu ADD COLUMN especificacion_tecnica TEXT;
  ALTER TABLE proyecto ADD COLUMN titulo_et_1 TEXT;
  ALTER TABLE proyecto ADD COLUMN titulo_et_2 TEXT;
  ```
- Update `Apu.java` entity: add `especificacionTecnica` field
- Update `Proyecto.java` entity: add `tituloEt1`, `tituloEt2` fields

**APU Resource addition for P-45:**
- `PUT /apus/{id}/especificacion-tecnica` in `ApuResource.java`:
  accepts `EspecificacionTecnicaRequest`, validates <= 64KB UTF-8,
  returns `ApuResponse`

**Display config endpoint:**
- `GET /api/v1/config/display` — returns `{ precisionDinero: 2, precisionPorcentaje: 4 }`
  from `application.yml` properties `app.display.precision-dinero` and
  `app.display.precision-porcentaje`
- Small resource, can live in `common/` package

### Out of scope

- PDF rendering optimizations
- Async generation (contract assumes sync+stream)
- The SERCOP export format spec is in `thesis-docs/plan/design/04-export-sercop-spec.md` --
  the executor should read it for layout details

## Key design decisions

1. **Sync + stream**: generate on-the-fly, stream to client. No job queue, no stored
   documents. If async is needed later, these GETs return 202 + Location (additive).

2. **POI for xlsx, OpenPDF for pdf, POI XWPF for docx**: all already in dependencies.

3. **Rounding only at export layer**: the motor stores at NUMERIC(14,6); the export
   service reads `precisionDinero` from config and rounds for display.

4. **ET export**: one Word document per presupuesto, not per APU. Each APU that has a
   non-null `especificacion_tecnica` gets a section. APUs without ET are listed with
   a placeholder note.

## Verification

```bash
./gradlew test
# DocumentoResourceIT:
# - generate APU xlsx, verify it's a valid xlsx (open with POI, check cell values)
# - generate ET docx, verify sections match APU count
# - attempt export on invalid presupuesto -> 409 with checklist
```
