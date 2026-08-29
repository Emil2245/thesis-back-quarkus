# Plan 018 — Project operations (P-09, P-10, P-46, logo, uso)

**Status:** TODO
**Priority:** P2
**Effort:** M
**Depends on:** 014
**Written against:** `97280ab`
**Spec:** API contract §3 (P-06/P-07/P-09/P-10), §5 (P-18, P-46)
**Skill:** activate `/ponytail:ponytail` (full) before writing any code

## Why

The proyecto resource exists with CRUD but is missing several operations the frontend
expects. The gaps are scattered single endpoints rather than a whole module.

## Scope

### In scope

**Missing endpoints on `ProyectoResource`:**

1. `POST /proyectos/{id}/duplicar` — duplicate project (P-09)
   - Accept `ProyectoDuplicarRequest` (`nombre`)
   - Deep copy: cabecera + parametros + firmantes + base insumos + vigente version
     (with chapters, rubros, APUs, sections, details)
   - Generate new codigo
   - Only copies the vigente version (D-04)
   - Return 201 + `ProyectoDetalleResponse`

2. `PUT /proyectos/{id}/logo` — upload logo (P-06/P-07)
   - Accept multipart image
   - Validate type (JPEG/PNG) and size (< 2MB)
   - Store in `proyecto.logo` BYTEA column
   - Return 204

3. `GET /proyectos/{id}/logo` — get logo (P-37 export)
   - Return image bytes with correct Content-Type
   - 404 if no logo

4. `GET /proyectos/{id}/insumos/{iid}/uso` — insumo usage report (P-18)
   - Return `InsumoUsoResponse[]` listing APUs that reference this insumo
   - Already partially in contract; query joins `apu_detalle.insumo_id`

**Plantilla Proyecto (P-46 -- new feature, N04 §A8):**

New package or sub-package:

- Entity `PlantillaProyecto`: new table needed (not in V001)
  - Migration `V008__plantilla_proyecto.sql`:
    ```sql
    CREATE TABLE plantilla_proyecto (
      id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
      usuario_id  BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
      nombre      TEXT NOT NULL,
      descripcion TEXT,
      snapshot    JSONB NOT NULL,
      created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    CREATE INDEX ix_plantilla_proyecto_usuario ON plantilla_proyecto(usuario_id);
    ```
  - The `snapshot` JSONB stores the full project structure: chapters hierarchy,
    rubros (without quantities), APU snapshots (sections/details without prices)

- `PlantillaProyectoResource` at `/plantillas-proyecto`:
  - `GET` — list user's templates
  - `POST` — create from existing project (`PlantillaProyectoCrearRequest`: `nombre`, `descripcion?`, `proyectoId`)
  - `DELETE /{id}` — delete

- `ProyectoDesdePlantillaResource` at `/proyectos/desde-plantilla/{plantillaId}`:
  - `POST` — create project from template (`ProyectoDesdePlantillaRequest`: `nombre`)
  - Creates new project with structure from snapshot
  - Same fallback mechanics as APU template (N04 §B.4): resolve insumo prices against
    CENTRAL/PERSONAL bases; unresolved get pending rows

### Out of scope

- Admin operations on projects (viewing other users' projects -- admin already has
  access via RNF-05 owner check that allows SUPER_ADMIN)
- ProyectoDetalleResponse enrichment (`versionVigente`, `alertas`) -- addressed in
  plan 014 as the presupuesto module enables it

## Key design decisions

1. **Deep copy for duplication**: copies everything from the vigente version. The new
   project gets its own independent copy of all data. Uses the same RecalculoService
   from plan 014 to recompute totals.

2. **Plantilla snapshot**: JSONB stores the full structure without prices. When
   creating from template, prices are resolved fresh from the user's available bases.

3. **Logo storage**: BYTEA in the proyecto table. Simple for v1 -- if it becomes a
   performance concern, move to object storage later.

## Verification

```bash
./gradlew test
# ProyectoOperationsIT:
# - duplicate project, verify independence
# - upload/download logo
# - insumo usage report
# - create/list/delete plantilla proyecto
# - create project from plantilla
```
