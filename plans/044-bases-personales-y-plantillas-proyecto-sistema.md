# Plan 044: Insumos en bases personales y plantillas de proyecto SISTEMA

## Estado

- **Prioridad**: P1
- **Esfuerzo**: M
- **Riesgo**: MEDIUM — owner-scope (RNF-05) en endpoints nuevos y una migración
  que relaja `plantilla_proyecto.usuario_id NOT NULL`.
- **Origen**: `thesis-front-react/plans/pendientes2/bugs-pendientes.md` §5 y §6.
- **Estado**: DONE (2026-09-23, rama `bugs/cronograma`, sin commit)

## Qué faltaba

1. **§5 — insumos.** `/bases-personales` sólo gestionaba la base (listar, crear,
   borrar); no había forma de poner insumos dentro. Tampoco había lectura de los
   insumos de una base central para un USUARIO (ni para el admin: S-39 del
   frontend mostraba "el servidor no expone el listado"). Y la copia a un
   proyecto sólo aceptaba `CENTRAL | PROYECTO`.
2. **§6 — plantillas de proyecto.** `plantilla_proyecto` no tenía `tipo`: todo
   era personal (`usuario_id NOT NULL`, P-46). No había plantillas de sistema ni
   forma de gestionarlas.

## Qué se hizo

### Insumos (§5)

| Endpoint | Rol | Notas |
|---|---|---|
| `GET /bases-centrales/{id}/insumos` | USUARIO, SUPER_ADMIN | Solo lectura. Mismos filtros (`tipo`, `q`, `desactualizados`, `page`, `size`) que `/proyectos/{id}/insumos`. Base no CENTRAL → 404. |
| `GET /bases-personales/{id}/insumos` | dueño | Paginado, mismos filtros. |
| `POST /bases-personales/{id}/insumos` | dueño | `InsumoCrearRequest`. |
| `PUT /bases-personales/{id}/insumos/{insumoId}` | dueño | `InsumoEditarRequest`. |
| `DELETE /bases-personales/{id}/insumos/{insumoId}` | dueño | D-08 igual que PROYECTO. |
| `POST /bases-personales/{id}/insumos/importar` | dueño | Multipart CSV, `/importar` como el proyecto. |
| `POST /proyectos/{id}/insumos/copiar` | dueño | `fuenteTipo` acepta ahora `PERSONAL` (owner-scoped). |

Todo reutiliza `InsumoCrudService` / `ImportacionInsumoService` /
`BaseInsumosService.listarInsumosBase` con el BIGINT de la base, igual que ya
hacía `AdminBaseCentralResource`: no hay una segunda copia de las reglas D-06,
D-08 ni de unicidad `(base, codigo)`. La base personal se resuelve siempre con
`BasesPersonalesService.buscarPorPublicId` (ajena, CENTRAL o PROYECTO → 404).

### Plantillas de proyecto (§6)

- `V013__plantilla_proyecto_tipo.sql`: `tipo VARCHAR(10) NOT NULL DEFAULT
  'PERSONAL'`, `usuario_id` nullable, CHECK `SISTEMA ⇔ usuario_id IS NULL` —
  el mismo modelo de `plantilla_apu` (V001 §2.12). Las filas existentes quedan
  PERSONAL sin backfill.
- `PlantillaProyecto.tipo` (reusa `PlantillaApu.Tipo`), `PlantillaProyectoResponse.tipo`.
- `GET /plantillas-proyecto?tipo=` → SISTEMA + PERSONALES del caller; `tipo`
  opcional, inválido → 400.
- `GET /plantillas-proyecto/{id}` y `POST /proyectos/desde-plantilla/{id}`
  aceptan SISTEMA. `DELETE /plantillas-proyecto/{id}` sigue owner-only: SISTEMA
  → 404 (como `plantilla_apu`).
- `/admin/plantillas-proyecto` (SUPER_ADMIN): `GET` paginado con `q`, `POST
  {desdeProyectoId, nombre, descripcion?}`, `PUT` con semántica de presencia
  (`nombre`, `descripcion`), `DELETE`. Espejo de `/admin/plantillas-apu`. El
  snapshot sale de `construirSnapshotDesdeProyecto`, el mismo que usa el
  guardado personal. Auditoría: `ADMIN_PLANTILLA_EDITADA` con entidad
  `plantilla_proyecto`.

## Verificación

```bash
./gradlew test --tests 'ec.uce.propuestas.plantilla.*' --tests 'ec.uce.propuestas.insumo.*' \
  --tests 'ec.uce.propuestas.schema.*' --tests 'ec.uce.propuestas.identifier.*'
```

Resultado 2026-09-23: **243/243 verdes**, incluidos los nuevos
`BasesPersonalesInsumosIT` (5) y `PlantillaProyectoSistemaIT` (3). La suite
completa no se ejecutó en esta pasada.

## Pendiente / decisiones abiertas

- `thesis-docs` (`06-database-schema.md`, `07-api-contract.md`) no se ha
  actualizado todavía con V013 ni con los endpoints nuevos.
- No hay seed de plantillas de proyecto SISTEMA (bugs-pendientes §3).
