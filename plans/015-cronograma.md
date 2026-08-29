# Plan 015 — Cronograma module (P-33..P-36)

**Status:** TODO
**Priority:** P1
**Effort:** M
**Depends on:** 014
**Written against:** `97280ab`
**Spec:** API contract §7, schema 06 §2.13
**Skill:** activate `/ponytail:ponytail` (full) before writing any code

## Why

The cronograma tables exist in V001 (`cronograma`, `actividad`) but there are no
entities, services, or resources. The frontend cronograma module is fully
implemented (frontend plan 029) and expects these endpoints.

## Scope

### In scope

New package `ec.uce.propuestas.cronograma`:

**Entities:**
- `Cronograma` (maps `cronograma` table)
- `Actividad` (maps `actividad` table; FK to `cronograma` + `rubro`)

**DTOs (per API contract §7):**
- `CronogramaResponse` (id, presupuestoId, unidadTiempo, numeroPeriodos,
  totalGeneral, totalGeneralRevisado?, fechaRevision?, desactualizado,
  actividades[], avancePorPeriodo[], avanceAcumulado[])
- `ActividadResponse` (id, rubroId, item, descripcion, precioTotal, pesoPonderado,
  avancePorPeriodo map, desviacion)
- `CronogramaCrearRequest` (unidadTiempo, numeroPeriodos)
- `CronogramaConfigurarRequest` (unidadTiempo?, numeroPeriodos?, confirmarPerdida?)
- `ActividadAvanceRequest` (avancePorPeriodo map)

**Resources:**
- `PresupuestoCronogramaResource` at `/presupuestos/{presupuestoId}/cronograma`
  - `GET` — get cronograma (404 if not configured)
  - `POST` — create (409 if already exists; auto-imports actividades 1:1 from rubros)
- `CronogramaResource` at `/cronogramas/{cronogramaId}`
  - `PUT` — configure (change unit/periods; 409 with `reduccion-periodos-requiere-confirmacion`
    if reducing periods loses data, unless `confirmarPerdida: true`)
  - `PATCH /actividades/{actividadId}` — update avance (validate period keys 1..n)
  - `POST /revisado` — snapshot `totalGeneralRevisado` + `fechaRevision`

**Services:**
- `CronogramaService`:
  - Create: auto-import actividades from rubros of the presupuesto
  - Configure: validate period reduction; if it would lose avance data, throw
    `reduccion-periodos-requiere-confirmacion` unless `confirmarPerdida`
  - Update avance: store in JSONB `avance_por_periodo`
  - Compute derived fields: `pesoPonderado` = rubro.precioTotal / presupuesto.total;
    `desviacion` = sum(avance) - pesoPonderado; totals rows from Motor
  - Mark revised: set `totalGeneralRevisado`, `fechaRevision`
  - `desactualizado` flag: true when `totalGeneral != totalGeneralRevisado`
    (the presupuesto changed since last revision)

**Mapper:** `CronogramaMapper`

**Integration into RecalculoService (plan 014):**
When rubros are added/removed from the presupuesto, the cronograma's actividades
must be synced (add missing, remove orphaned). This cascades through `rubro_id`
FK with `ON DELETE CASCADE`.

### Out of scope

- Gantt rendering (frontend-only)
- Document export (plan 016)

## Key design decisions

1. **1:1 rubro-to-actividad**: every rubro gets exactly one actividad, auto-imported.
   This is RNF-02's 100% linkage guarantee.

2. **Avance stored as JSONB**: keys are period numbers (1-based strings), values are
   decimal strings. The motor computes totals and accumulated values.

3. **Period reduction**: if the user reduces `numeroPeriodos` from 12 to 8, any avance
   data in periods 9-12 would be lost. The API returns a 409 listing what would be
   lost; the client retries with `confirmarPerdida: true`.

4. **desactualizado**: derived on read. The cronograma stores `totalGeneral` (from the
   presupuesto at creation/revision time); if the current presupuesto total differs,
   the flag is true.

## Verification

```bash
./gradlew test
# CronogramaResourceIT should cover:
# - create cronograma, verify actividades auto-imported
# - update avance, verify totals
# - reduce periods without confirmar -> 409
# - reduce periods with confirmar -> 200
# - mark revisado, verify snapshot
```
