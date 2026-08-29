# Plan 013 — Remove esAuxiliar / no-links (N04 §2)

**Status:** TODO
**Priority:** P0 (blocks all other v1.3 plans)
**Effort:** S-M
**Written against:** `97280ab`
**Spec:** N04 §2, API contract §5 (SUPERSEDED notes), schema 06 diff since `053ebc1`
**Skill:** activate `/ponytail:ponytail` (full) before writing any code

## Why

The v1.3 spec eliminates the `es_auxiliar` flag on APUs and the `apu_auxiliar_id`
FK on `apu_detalle`. No APU references another APU -- all filas reference insumos
from the base PROYECTO only. The backend still has:

- `Apu.esAuxiliar` field (entity + DDL column `apu.es_auxiliar`)
- `ApuDetalle` entity likely still has `apuAuxiliarId` (column `apu_auxiliar_id`)
- `Motor.java` line 119: `if (in.esAuxiliar()) { costoIndirecto = ZERO }`
- `ApuSnapshot.esAuxiliar()` record field
- Various DTOs and mappers referencing the flag
- V001 DDL: the column, CHECK constraints, and index `ix_apu_detalle_auxiliar`

The frontend has already been cleaned (frontend plan 030). This plan aligns the
Quarkus side.

## Scope

### In scope

- Flyway migration `V005__remove_es_auxiliar.sql` (or next available number):
  - `ALTER TABLE apu DROP COLUMN es_auxiliar;`
  - `ALTER TABLE apu_detalle DROP COLUMN apu_auxiliar_id;`
  - `DROP INDEX IF EXISTS ix_apu_detalle_auxiliar;`
  - Drop the CHECK constraint `NOT (insumo_id IS NOT NULL AND apu_auxiliar_id IS NOT NULL)`
  - Relax `tarifa_jornal` and `precio_unitario_tarifa` CHECKs from `> 0` to `>= 0`
    (plan 017 / V005 structural: filas pendientes from template fallback need `= 0`)
- Entity `Apu.java`: remove `esAuxiliar` field
- Entity `ApuDetalle.java` (if `apuAuxiliarId` exists): remove it
- `ApuSnapshot` record: remove `esAuxiliar` parameter
- `Motor.java` lines 118-123: remove the `esAuxiliar` branch -- CI always applies
- `ApuCalculado` record: remove `esAuxiliar` parameter
- All DTOs (`ApuResponse`, `ApuResumenResponse`, `ApuCrearRequest`, `ApuPatchRequest`):
  remove any `esAuxiliar` field
- All mappers: remove references
- `ApuCrudService`: remove any logic that validates/sets `esAuxiliar`
- `PresupuestoApuResource`: remove `soloAuxiliares` query param if present
- Update existing tests:
  - `MotorApuTest`, `MotorConsolidacionTest`, `Fixtures`: remove auxiliary fixtures,
    verify CI now always applies
  - `ApuResourceIT`: remove any test that toggles `esAuxiliar`

### Out of scope

- Presupuesto/cronograma resources (don't exist yet)
- Frontend changes (already done)
- Any other v1.3 feature

## Steps

1. Create `V005__remove_es_auxiliar.sql` in `src/main/resources/db/migration/`:
   ```sql
   -- V005 — Remove esAuxiliar / no-links (N04 §2)
   ALTER TABLE apu DROP COLUMN IF EXISTS es_auxiliar;
   ALTER TABLE apu_detalle DROP COLUMN IF EXISTS apu_auxiliar_id;
   DROP INDEX IF EXISTS ix_apu_detalle_auxiliar;
   -- Relax tarifa/precio checks for template fallback pending rows
   ALTER TABLE apu_detalle DROP CONSTRAINT IF EXISTS apu_detalle_tarifa_jornal_check;
   ALTER TABLE apu_detalle ADD CONSTRAINT apu_detalle_tarifa_jornal_check CHECK (tarifa_jornal >= 0);
   ALTER TABLE apu_detalle DROP CONSTRAINT IF EXISTS apu_detalle_precio_unitario_tarifa_check;
   ALTER TABLE apu_detalle ADD CONSTRAINT apu_detalle_precio_unitario_tarifa_check CHECK (precio_unitario_tarifa >= 0);
   -- Drop the mutual-exclusion check (insumo vs auxiliar)
   ALTER TABLE apu_detalle DROP CONSTRAINT IF EXISTS apu_detalle_check;
   -- Re-add the HM-only check without apu_auxiliar_id
   ALTER TABLE apu_detalle ADD CONSTRAINT apu_detalle_hm_check
     CHECK (NOT es_herramienta_menor OR (insumo_id IS NULL
            AND tarifa_jornal IS NULL AND rendimiento IS NULL AND cantidad IS NULL));
   ```

2. Update `Apu.java`: remove `esAuxiliar` field and its `@Column` annotation.

3. Update `ApuDetalle.java` (check if `apuAuxiliarId` exists): remove it.

4. Update `ApuSnapshot` record: remove `esAuxiliar`. All call sites that construct
   the snapshot must stop passing it.

5. Update `Motor.java`:
   - Remove the `if (in.esAuxiliar())` branch at line ~119. CI always applies:
     `costoIndirecto = costoDirectoAjustado.multiply(pctCi, MC);`
   - Remove `esAuxiliar` from `ApuCalculado` constructor call.

6. Update `ApuCalculado` record: remove `esAuxiliar` parameter.

7. Update all DTOs and mappers that reference `esAuxiliar` or `apuAuxiliarId`.

8. Update `ApuCrudService`: remove any validation around the auxiliar flag.

9. Update tests: `Fixtures.java` snapshots, `MotorApuTest` assertions, `ApuResourceIT`.

## Verification

```bash
./gradlew test          # all tests pass
./gradlew quarkusDev    # starts without Flyway errors
```

## Escape hatch

If the V001 CHECK constraint names don't match (Postgres auto-generates names),
use `\d+ apu_detalle` in psql to find actual names and adjust the migration.

## Maintenance

Plans 014+ depend on this being done first. The motor's `esAuxiliar` branch removal
is the single most important correctness change -- every APU now computes CI.
