# Plan 017 — APU advanced operations (P-19, P-24, P-26, P-27, P-45)

**Status:** TODO
**Priority:** P1
**Effort:** M
**Depends on:** 013, 014 (for RecalculoService)
**Written against:** `97280ab`
**Spec:** API contract §5
**Skill:** activate `/ponytail:ponytail` (full) before writing any code

## Why

The APU resource exists with basic CRUD but is missing several endpoints the frontend
already uses. These are operations that go beyond basic cell editing.

## Scope

### In scope

**Missing endpoints on `ApuResource` (`/apus/{apuId}`):**

1. `POST /duplicar` — duplicate APU within the same version
   - Accept optional `ApuDuplicarRequest` with `copiarET: true` (default false)
   - Copy all sections + details, generate new code (`APU-{n}`)
   - Copy `especificacion_tecnica` if `copiarET`
   - Copy override %CI and %descuento
   - Trigger recalculation
   - Return 201 + new `ApuResponse`

2. `POST /descuento` — per-rubro discount
   - Accept `DescuentoRubroRequest` (`porcentaje` 0-50%)
   - Set `Apu.porcentajeDescuento`, recalculate
   - 0% = revert
   - Return `ApuResponse`

3. `GET /calculo` — calculation breakdown
   - Return `ApuCalculoResponse` with formula strings per line
   - Per API contract Appendix B: `parametros`, `secciones[].lineas[].operacion/resultado`,
     `resumen` with `cd`, `cdAjustado`, `operacionCdAjustado`, `ci`, `ct`
   - Read-only: serves cached/write-through values + instanciated formulas (DM §16)

4. `POST /guardar-plantilla` — save as personal template
   - Accept `PlantillaApuCrearRequest` (`nombre`, `descripcionRubro?`)
   - Create `PlantillaApu` with `tipo=PERSONAL`, `usuario_id` from caller
   - Snapshot sections without prices (DM §12: snapshot stores insumo codes,
     cantidades, rendimientos, but NOT precio_unitario values)
   - ID is UUIDv7 string (public ID)
   - Return 201 + `PlantillaApuResumenResponse`

5. `PUT /especificacion-tecnica` — save ET text (P-45)
   - Accept `EspecificacionTecnicaRequest` (`texto`)
   - Validate <= 64KB UTF-8
   - Return `ApuResponse`

**Plantilla APU resource (new):**

`PlantillaApuResource` at `/plantillas-apu`:
- `GET ?tipo&q` — list templates (SISTEMA visible to all; PERSONAL only caller's)
- `GET /{id}` — detail with snapshot (UUIDv7 id)
- `PUT /{id}` — edit metadata only (name, descripcionRubro); PERSONAL own only
- `DELETE /{id}` — delete; PERSONAL own only; SISTEMA/ajena -> 404 (RNF-05)

**Plantilla-aware APU creation:**

Update `PresupuestoApuResource.POST /presupuestos/{id}/apus`:
- When `plantillaId` is present in `ApuCrearRequest`:
  - Load `PlantillaApu` by UUIDv7 id
  - Precarga snapshot sections/details
  - Resolve prices against base PROYECTO insumos (by codigo)
  - Unresolved insumos: create fila with `insumo_id = NULL`, override = 0,
    add `AdvertenciaPlantillaResponse` to response
  - Return 200 (with advertencias) or 201 (without)

**Entity additions:**
- `PlantillaApu` entity (maps `plantilla_apu` table; already exists in V001)
  - Add UUIDv7 public ID field (separate from the BIGINT PK? Or use the BIGINT
    and format as UUID in the DTO? The spec says "ID publico UUIDv7" -- needs a
    `VARCHAR(36)` or `UUID` column. If the table has `BIGINT`, add a migration
    `V007__plantilla_uuid.sql` adding `public_id UUID DEFAULT gen_random_uuid()`
    with a unique index)
- `PlantillaApuRepository`, `PlantillaApuMapper`, `PlantillaApuService`

### Out of scope

- Admin plantilla SISTEMA endpoints (plan 019)
- `bases-personales` (plan 020)
- Descuento global (in plan 014's scope)

## Key design decisions

1. **UUIDv7 for plantilla public IDs**: the spec requires string IDs. The existing
   `plantilla_apu` table uses BIGINT IDENTITY. Add a `public_id UUID` column, use it
   in all API responses, and look up by it in the resource layer.

2. **Snapshot format**: JSONB with section+detail structure but NO prices. When
   loading from template, prices are resolved against the project's insumo base.

3. **Advertencias on template load**: if an insumo codigo from the snapshot doesn't
   exist in the project's base, the fila is created with `insumo_id = NULL` and
   `precio_unitario_tarifa = 0` (or `tarifa_jornal = 0`). The response includes
   `advertencias[]` listing the unresolved codigos. Frontend shows a toast warning.

## Verification

```bash
./gradlew test
# ApuAdvancedIT:
# - duplicate APU, verify new code and independence
# - apply per-rubro discount, verify recalculated CT
# - get calculo, verify formula strings
# - save as template, verify snapshot has no prices
# - create APU from template, verify rows populated
# - create APU from template with missing insumo, verify advertencias + pending rows
```
