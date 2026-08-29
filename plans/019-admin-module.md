# Plan 019 — Super-admin module (P-38..P-42)

**Status:** TODO
**Priority:** P2
**Effort:** L
**Depends on:** 013 (for clean model), 014 (parametros-sistema enrichment)
**Written against:** `97280ab`
**Spec:** API contract §9
**Skill:** activate `/ponytail:ponytail` (full) before writing any code

## Why

The admin panel in the frontend is fully built. The backend only has
`GET /proyectos/parametros-sistema` (a read endpoint on the proyecto resource).
All admin-specific resources under `/admin/*` are missing.

## Scope

### In scope

New package `ec.uce.propuestas.admin`:

**User management (P-38):**
- `AdminUsuarioResource` at `/admin/usuarios`
  - `GET ?q&activo&page...` — paginated list with `UsuarioAdminResponse`
  - `POST` — invite user (`UsuarioInvitarRequest`: nombre, email, rol)
    - Creates user with `activo=false`, `emailVerificado=false`
    - Sends invitation email with 72h token (D-11)
  - `PUT /{id}` — edit (`UsuarioAdminEditarRequest`: nombre, email, rol)
  - `POST /{id}/desactivar` — deactivate
  - `POST /{id}/reactivar` — reactivate
  - `DELETE /{id}` — delete (409 if has projects, RESTRICT)

**Central bases admin (P-39, N04 §D-12):**
- `AdminBasesCentralesResource` at `/admin/bases-centrales`
  - `GET ?incluirArchivadas=` — list (include archived if flag set)
  - `POST` — create (`BaseInsumosCrearRequest`)
  - `PUT /{id}` — rename
  - `POST /{id}/archivar` — archive (toggle: oculta del catalogo)
  - `DELETE /{id}` — delete (no FK block per N04 §D-12: copied data in projects persists)
  - `POST /{id}/insumos` — add insumo
  - `PUT /{id}/insumos/{iid}` — edit insumo (no propagation to APUs per N04 §A9)
  - `DELETE /{id}/insumos/{iid}` — delete insumo
  - `POST /{id}/insumos/import?soloValidar=` — CSV import (reuse ImportacionInsumoService)

**System plantillas APU admin (P-40):**
- `AdminPlantillaApuResource` at `/admin/plantillas-apu`
  - `GET` — list SISTEMA type
  - `POST` — create from reference APU (`PlantillaSistemaCrearRequest`: `desdeApuId`, `nombre`, `descripcionRubro?`)
  - `PUT /{id}` — edit metadata
  - `DELETE /{id}` — delete

**System parameters (P-41):**
- `AdminParametrosSistemaResource` at `/admin/parametros-sistema`
  - `GET` — singleton `ParametrosSistemaResponse`
    - Include range fields: `rangoHmMin/Max`, `rangoCiMin/Max`,
      `rangoDescuentoMin/Max`, `rangoIvaMin/Max` (configurable ranges per N04 §A6)
  - `PUT` — update (`ParametrosSistemaActualizarRequest`)
    - Only affects new projects (DM §15)

- `AdminValoresReferenciaResource` at `/admin/valores-referencia`
  - `GET` — list all
  - `PUT /{clave}` — upsert
  - `DELETE /{clave}` — delete

**Activity logs (P-42):**
- `AdminLogResource` at `/admin/logs`
  - `GET ?usuarioId&evento&desde&hasta&page...` — paginated
  - `LogActividadResponse` (id, usuarioId, usuarioNombre, evento, detalle, fecha)

- `LogService` — called from key operations to record events:
  - `apu.creado`, `apu.eliminado`, `proyecto.creado`, `usuario.invitado`,
    `base.archivada`, `parametros.actualizados`, etc.
  - Writes to `log_actividad` table (already in V001)
  - No PII in detail (RNF-08)

**Schema additions:**
- Migration `V009__admin_ranges.sql` (or embedded in an earlier migration):
  ```sql
  ALTER TABLE parametros_sistema ADD COLUMN rango_hm_min NUMERIC(5,4) DEFAULT 0;
  ALTER TABLE parametros_sistema ADD COLUMN rango_hm_max NUMERIC(5,4) DEFAULT 0.2000;
  ALTER TABLE parametros_sistema ADD COLUMN rango_ci_min NUMERIC(5,4) DEFAULT 0;
  ALTER TABLE parametros_sistema ADD COLUMN rango_ci_max NUMERIC(5,4) DEFAULT 1.0000;
  ALTER TABLE parametros_sistema ADD COLUMN rango_descuento_min NUMERIC(5,4) DEFAULT 0;
  ALTER TABLE parametros_sistema ADD COLUMN rango_descuento_max NUMERIC(5,4) DEFAULT 0.5000;
  ALTER TABLE parametros_sistema ADD COLUMN rango_iva_min NUMERIC(5,4) DEFAULT 0;
  ALTER TABLE parametros_sistema ADD COLUMN rango_iva_max NUMERIC(5,4) DEFAULT 0.3000;
  ```
- Update `ParametrosSistema` entity with range fields

### Out of scope

- Bases personales (plan 020)
- Display config endpoint (plan 016)

## Key design decisions

1. **All `/admin/*` routes are `@RolesAllowed("SUPER_ADMIN")`**. This is the single
   authorization gate.

2. **Archiving (N04 §D-12)**: archive = set `archivada=true`. The base disappears from
   `GET /bases-centrales` (user-facing), but stays visible in
   `GET /admin/bases-centrales?incluirArchivadas=true`. Delete is allowed even if
   archived -- copied data in projects persists (no FK from project insumos to central).

3. **Log events**: fire-and-forget writes to `log_actividad`. No async queue needed at
   this scale. The `LogService` is injected into other services and called after the
   main transaction succeeds (or use `@Transactional(REQUIRES_NEW)` to not fail the
   main operation if logging fails).

4. **Range fields on parametros_sistema**: the frontend already reads these
   for dynamic validation. The backend enforces them in the validation layer.

## Verification

```bash
./gradlew test
# AdminUsuarioResourceIT: invite, list, edit, deactivate, reactivate, delete
# AdminBasesCentralesResourceIT: CRUD, archive, import CSV
# AdminParametrosSistemaResourceIT: get, update, verify ranges
# AdminLogResourceIT: verify events are recorded after operations
```
