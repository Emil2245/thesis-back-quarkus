# Plan 014 — Presupuesto, capitulos, rubros, versiones (P-28..P-32)

**Status:** TODO
**Priority:** P0 (highest-leverage unblock)
**Effort:** L
**Depends on:** 013
**Written against:** `97280ab`
**Spec:** API contract §6 (P-28..P-32), schema 06 §§2.8-2.11
**Skill:** activate `/ponytail:ponytail` (full) before writing any code

## Why

The presupuesto module is the **single highest-leverage gap** in the backend. Without
`GET /proyectos/{id}/presupuestos`, the frontend has no `presupuestoId`, and the
already-implemented APU module is unreachable from the UI. The DB tables exist
(`presupuesto`, `capitulo`, `rubro`) but there are no entities, repositories, services,
or JAX-RS resources for them.

## Scope

### In scope

New Quarkus package `ec.uce.propuestas.presupuesto` with:

**Entities:**
- `Presupuesto` (maps `presupuesto` table)
- `Capitulo` (maps `capitulo` table; self-referencing `parent_id`)
- `Rubro` (maps `rubro` table; FK to `capitulo` and `apu`)

**DTOs (per API contract §6 + Appendix B):**
- `PresupuestoVersionResponse` (`presupuestoId`, `version`, `esVigente`, `origenId?`, `notas?`, `fechaCreacion`, `totalGeneral`)
- `PresupuestoVersionCrearRequest` (`origenId`, `notas?`)
- `PresupuestoResponse` (full tree: `presupuestoId`, `version`, `esVigente`, `totalGeneral`, `capitulos[]`)
- `CapituloResponse` (recursive: `id`, `item`, `descripcion`, `orden`, `total`, `subcapitulos[]`, `rubros[]`)
- `RubroResponse` (`id`, `item`, `codigo`, `descripcion`, `unidad`, `cantidad`, `precioUnitario`, `precioTotal`, `apuId`, `alertas[]`)
- `CapituloCrearRequest` (`descripcion`, `parentId?`, `orden?`)
- `CapituloEditarRequest` (`descripcion`)
- `CapituloMoverRequest` (`parentId?`, `orden`)
- `RubroCrearRequest` (`apuId`, `cantidad`)
- `RubroPatchRequest` (`cantidad`)
- `ResumenComponentesResponse`
- `ComparacionVersionesResponse`
- `ValidacionPresupuestoResponse`, `RubroRefResponse`
- `DescuentoGlobalPreviewResponse`, `DescuentoGlobalRequest`

**Resources (JAX-RS):**
- `PresupuestoVersionResource` at `/proyectos/{proyectoId}/presupuestos`
  - `GET` — list versions
  - `POST` — create version (deep copy from `origenId`)
- `PresupuestoResource` at `/presupuestos/{presupuestoId}`
  - `GET` — full tree
  - `DELETE` — delete version (409 if vigente)
  - `POST /vigente` — mark as vigente
  - `GET /resumen` — component breakdown
  - `GET /comparar?con={id2}` — version comparison
  - `GET /validacion` — integrity check (PU=0, qty=0, sin actividad)
  - `GET /descuento-global/preview?porcentaje=` — preview
  - `POST /descuento-global` — apply (N04 §A1 FORMA 1)
- `CapituloResource` at `/presupuestos/{presupuestoId}/capitulos`
  - `POST` — create
  - `PUT /{cid}` — edit description
  - `PATCH /{cid}/mover` — move (reorder/reparent + renumber)
  - `DELETE /{cid}` — delete subtree
- `RubroResource` at `/presupuestos/{presupuestoId}/capitulos/{cid}/rubros`
  - `POST` — create (link APU; 1:1 unique constraint)
  - `PATCH /{rid}` — edit quantity
  - `DELETE /{rid}` — delete (cascade actividad)

**Services:**
- `PresupuestoService` — CRUD, deep copy, mark vigente
- `CapituloService` — create, edit, move (renumber `item` field), delete subtree
- `RubroService` — create (validate APU not already linked), edit qty, delete
- `RecalculoService` — after any mutation that affects totals, re-run
  `Motor.consolidar()` on the affected version's snapshot and write-through:
  section subtotals, APU CD/CI/CT, rubro precioTotal, chapter totals, version total.
  This is the write-through pattern from the API contract.
- `DescuentoGlobalService` — preview + apply (N04 §A1 FORMA 1: modifies specific
  columns in base PROYECTO insumos, not APU-level %)

**Mappers:**
- `PresupuestoMapper`, `CapituloMapper`, `RubroMapper`

**Integration tests:**
- `PresupuestoResourceIT` — full lifecycle: create version, add chapters, add rubros,
  verify totals, duplicate version, compare versions, mark vigente, delete non-vigente

### Out of scope

- Cronograma (plan 015)
- Document export (plan 016)
- `Capitulo.item` renumbering algorithm is complex (hierarchical with siblings) --
  the plan specifies the contract; implementation uses a depth-first walk

## Key design decisions

1. **Write-through totals**: every mutation that changes a cost must call
   `RecalculoService.recalcular(presupuestoId)` which snapshots the full version,
   runs `Motor.consolidar()`, and writes back all derived fields. This is the
   single source of truth -- the motor never runs in the read path (GETs are safe).

2. **Item numbering**: `Capitulo.item` is a hierarchical string ("1", "1.1", "1.1.1").
   On any structural change (add/move/delete), renumber the entire branch. The
   `item` column in `rubro` is derived as `{capitulo.item}.{rubro.orden}`.

3. **Deep copy** for version creation: copy all chapters, rubros, APUs (with sections
   and details), and reset the new version's total via recalculation. The source
   version is the `origenId` in the request. APU `presupuesto_id` points to the new
   version.

4. **Descuento global (N04 §A1 FORMA 1)**: reduces `Insumo.precio_unitario` in the
   base PROYECTO (tarifa in EQUIPO/TRANSPORTE, `precio_unitario` in MATERIAL; MO
   exempt by law). Then triggers full recalculation. Reversible with 0%.

5. **Validation endpoint** (`GET /validacion`): checks rubros for PU=0, cantidad=0,
   and rubros without a cronograma actividad. Returns the list -- the frontend
   renders it as a checklist.

## Verification

```bash
./gradlew test
# PresupuestoResourceIT should cover:
# - create version, add chapters/rubros, verify tree response
# - write-through: edit APU detail -> rubro.precioTotal updates
# - deep copy: create version from existing, verify independence
# - mark vigente: only one vigente per project
# - delete: 409 on vigente, 204 on non-vigente
```

## Maintenance

This is the core aggregated resource. Plans 015 (cronograma), 016 (export), and
017 (APU advanced) all depend on it. The `RecalculoService` will be called from
multiple places -- keep it stateless and transactional.
