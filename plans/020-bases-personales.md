# Plan 020 — Bases personales (N04 §A9)

**Status:** TODO
**Priority:** P3
**Effort:** S
**Depends on:** 013
**Written against:** `97280ab`
**Spec:** API contract §5 (N04 §A9)
**Skill:** activate `/ponytail:ponytail` (full) before writing any code

## Why

N04 §A9 introduces personal insumo bases -- collections of insumos owned by a user
that can be shared across their projects (unlike project bases which are per-project).
The frontend added this as a gated module. The backend needs two new endpoints.

## Scope

### In scope

The existing `base_insumos` table already supports `tipo = 'PROYECTO'` (per-project).
Personal bases reuse the same table with a new type:

**Schema migration `V010__bases_personales.sql`:**
```sql
-- Allow PERSONAL type in base_insumos
ALTER TABLE base_insumos DROP CONSTRAINT IF EXISTS base_insumos_tipo_check;
ALTER TABLE base_insumos ADD CONSTRAINT base_insumos_tipo_check
  CHECK (tipo IN ('CENTRAL', 'PROYECTO', 'PERSONAL'));
-- Add usuario_id for PERSONAL bases
ALTER TABLE base_insumos ADD COLUMN usuario_id BIGINT REFERENCES usuario(id) ON DELETE CASCADE;
-- Update the cross-check
ALTER TABLE base_insumos DROP CONSTRAINT IF EXISTS base_insumos_check;
ALTER TABLE base_insumos ADD CONSTRAINT base_insumos_check
  CHECK (
    (tipo = 'CENTRAL'  AND proyecto_id IS NULL AND usuario_id IS NULL) OR
    (tipo = 'PROYECTO' AND proyecto_id IS NOT NULL) OR
    (tipo = 'PERSONAL' AND proyecto_id IS NULL AND usuario_id IS NOT NULL)
  );
CREATE INDEX ix_base_insumos_usuario ON base_insumos(usuario_id);
```

**Update entities:**
- `BaseInsumos.java`: add `usuarioId` field
- `TipoBase` enum: add `PERSONAL`

**New resource:** `BasePersonalResource` at `/bases-personales`
- `GET` — list user's personal bases (`BaseInsumosResponse[]`)
- `POST` — create (`BasePersonalCrearRequest`: `nombre`)

**Service:** `BasePersonalService` (or extend `BaseInsumosService`)
- Create: set `tipo=PERSONAL`, `usuario_id` from caller, `proyecto_id=null`
- Insumo CRUD inside personal bases: reuse existing `InsumoResource` pattern
  (or add sub-routes `/bases-personales/{id}/insumos`)

### Out of scope

- Admin management of personal bases (admin doesn't manage these)
- Sharing personal bases between users (not in spec)

## Key design decisions

1. **Reuse base_insumos table**: personal bases are just another `tipo` in the same
   table. This means the existing insumo CRUD, CSV import, and selector
   (`busqueda?fuente=`) all work with minimal changes.

2. **Selector integration**: `GET /proyectos/{id}/insumos/busqueda?fuente=PERSONAL`
   should also search the user's personal bases. This requires updating the
   `InsumoCatalogoService` to include personal bases in the search.

## Verification

```bash
./gradlew test
# BasePersonalResourceIT:
# - create personal base
# - add insumos to it
# - list personal bases
# - verify personal base insumos appear in busqueda?fuente=PERSONAL
```
