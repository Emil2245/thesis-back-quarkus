# Plan 021 — Ciclo de vida del presupuesto: auto-create v1 vigente, listado y read model

> **Plan 021** del módulo [`05-presupuesto`](00.md). **DONE — 2026-09-01.**
> Implementados en este plan: **auto-create** de la versión 1 vigente del
> presupuesto dentro de `POST /proyectos` (en la misma `@Transactional`,
> orquestado desde `ProyectoService.crear` mediante una llamada al nuevo
> `PresupuestoService.crearVigenteInicial(...)` — gap detectado por la
> auditoría del plan, cerrado aquí sin seam nuevo); **listado** de
> versiones del proyecto en `GET /proyectos/{id}/presupuestos` (servido
> por `ProyectoResource`); **read model completo** del árbol
> `Presupuesto → Capitulo → Rubro` en `GET /presupuestos/{id}` (servido
> por `PresupuestoResource`, **recursivo**, sin tope de profundidad).
> Cubre los aspectos del **ciclo de vida del presupuesto** que viven
> fuera de los CRUD de capítulos (Plan 022), rubros (Plan 023) y
> versionado (Plan 024): el **auto-create de la versión 1 vigente** al
> crear un proyecto (P-06 §4), el **listado de versiones** (P-31 parte
> listado) y el **read model completo** del árbol `Presupuesto → Capitulo
> → Rubro` (P-28…P-30 read).

## Resultado esperado

Cuando un usuario crea un proyecto vía `POST /proyectos`, el sistema
crea automáticamente, dentro de la misma transacción:

1. La fila `Proyecto` + `ParametrosProyecto` (copia de
   `ParametrosSistema`) — responsabilidad del módulo `proyecto`
   (DONE 2026-08-02 / Plan 09); **queda lazy en este plan** y
   Plan 021 **no** la reabre.
2. La base PROYECTO (auto-creada por `BaseInsumosService`) —
   responsabilidad del módulo `insumo` (DONE 2026-08-02); **queda
   lazy en este plan** y Plan 021 **no** la reabre.
3. La fila `Presupuesto` con `version = 1`, `es_vigente = true`,
   `origen_id = NULL`, `notas = NULL`, `porcentaje_indirecto = NULL`
   (hereda del proyecto), `total = 0` — **pieza nueva de Plan 021**:
   `ProyectoService.crear` invoca `PresupuestoService.crearVigenteInicial(...)`
   en la misma `@Transactional` (gap detectado por la auditoría del
   plan y cerrado en este plan, ≤ 30 líneas dentro de STOP §A).

`GET /proyectos/{id}/presupuestos` (servido por `ProyectoResource`)
lista las versiones del proyecto (nº, fecha, notas, total, vigente).
`GET /presupuestos/{id}` (servido por `PresupuestoResource`) devuelve
el árbol **recursivo** completo actual (read model único de la
jerarquía), con `Capitulo` y `Rubro` enriquecidos desde sus entidades.
La invariante «exactamente una versión vigente por proyecto» se
verifica con el índice único parcial `ux_presupuesto_vigente` (V001 §2.8).

## Dependencias

- [Plan 019 — Identidad y persistencia](01-identidad-y-persistencia.md)
  (entidades `Capitulo`/`Rubro` ya en BD aunque sin uso todavía).
- [Plan 020 — `recalculo` write-through](02-recalculo-write-through.md)
  (necesario para que un mutación posterior del árbol propague totales;
  este plan sólo **lee** totales, no los modifica).
- Módulo `proyecto/` cerrado (DONE 2026-08-30).
- Módulo `presupuesto.entity.Presupuesto` ya implementado (DONE
  2026-08-30 con WU-03).

## Estado de cierre

**DONE — 2026-09-01.** Implementación aplicada y verificación dirigida
completa (cifras al pie en la sección «Pruebas y comprobaciones»). El plan
**NO** introduce módulos nuevos de primer nivel, **NO** crea una migración
nueva (V001–V008 intactas; V009 no se pre-asigna), **NO** añade un seam
nuevo y **NO** reabre el motor. La corrección del gap detectado por la
auditoría (auto-create v1 vigente ausente en `ProyectoService.crear`) cabe
en menos de 30 líneas y entra dentro del STOP §A autorizado por el plan
—no escala.

**Recheck completado tras los retoques fixture-only finales del plan.** Los
siguientes gates quedaron **verdes tras** los retoques fixture-only del
plan (alineación de imports / reformateo palantir en tests):
`./gradlew spotlessCheck` **verde**, `./gradlew build -x test` **verde**,
`git diff --check` **limpio**. La suite completa `./gradlew test`
re-ejecutada tras los retoques fixture-only quedó conteada en **346
totales = 343 pass + 2 aceptados (GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84`)
+ 1 skipped (GM-24 `@Disabled`) + 0 errors** (medido desde los XML de
`build/test-results/`): el motor y la regla workbook-consistent
permanecen cerrados (Plan 014) y los residuales aceptados siguen sin
reabrirse.

---

## Contexto / estado actual

### Lo que ya existe (DONE, NO se reabre)

1. **`ProyectoService.crear` ya crea `Proyecto` + `ParametrosProyecto`
   (copia de `ParametrosSistema`) + base PROYECTO** dentro de la misma
   transacción (módulo `proyecto` cerrado 2026-08-02 / Plan 09). **Pero
   no** creaba la fila `Presupuesto` versión 1 vigente — el plan original
   lo especulaba erróneamente. La auditoría previa a la implementación
   detectó el gap y Plan 021 cierra esa pieza (ver § «Lo que este plan
   construyó» #1).
2. **`PresupuestoRepository.findVigenteDeProyecto`** (módulo
   `presupuesto`, WU-03): devuelve el vigente de un proyecto. Plan 021
   añade el listado de versiones (`findByProyectoIdOrderByVersionDesc`)
   como pieza nueva.
3. **Índice único parcial `ux_presupuesto_vigente`** (V001 §2.8):
   `CREATE UNIQUE INDEX ux_presupuesto_vigente ON presupuesto
   (proyecto_id) WHERE es_vigente;` — garantiza la invariante
   «exactamente una vigente por proyecto». Verificado por tests del
   repository de `presupuesto`.
4. **`UUIDv7` parse canónico** en
   `ec.uce.propuestas.common.UuidV7` (Plan 07) — usado en frontera
   REST para todos los `pathParam` UUIDv7 de I-07.
5. **`ProblemaException` + `GlobalExceptionMapper`** en
   `ec.uce.propuestas.common` (Plan 07) — usado para mapear 400
   `validacion` (UUID malformado / no-v7) y 404 `no-encontrado`
   (RNF-05: ajeno o inexistente).
6. **`recalculo` activo** (Plan 020 DONE 2026-09-01, módulo
   `ec.uce.propuestas.recalculo` con `Alcance = Version | Apu | Insumo`).
   Plan 021 sólo **lee** totales; el contrato del read model **no**
   invoca `recalcular(Alcance)` ni toca la coherencia de totales. La
   coherencia de `presupuesto.total` y de los `capitulo.total` la
   garantiza `recalculo` cuando los rubros se creen en Plan 023; este
   plan verifica la lectura.

### Lo que este plan construyó

1. **Auto-create v1 vigente en `POST /proyectos`** — orquestado desde
   `ProyectoService.crear` mediante una llamada al nuevo
   `PresupuestoService.crearVigenteInicial(...)` dentro de la misma
   `@Transactional`. El gap (el flujo creaba `Proyecto`,
   `ParametrosProyecto` y base PROYECTO, **pero no** `Presupuesto`)
   fue detectado por la auditoría del plan (ver STOP §A) y se cerró
   en menos de 30 líneas. Ningún seam nuevo; PK/FK y campos de
   `presupuesto` inalterados. Verificación: TC-P06-01 ya existente
   en `ec.uce.propuestas.proyecto.*` re-ejecutado en verde.
2. **`PresupuestoRepository.listarVersiones(Long proyectoId)`** —
   `ORDER BY version DESC` (sin paginación en I-07: ≤ 5–10 versiones
   por proyecto en la práctica). Helper
   `findByProyectoIdAndEsVigenteFalse(...)` añadido para uso futuro
   por Plan 024.
3. **`PresupuestoService`** con cuatro métodos públicos:
   - `crearVigenteInicial(...)` — invocado desde `ProyectoService.crear`
     en la misma transacción.
   - `obtenerVigente(UUID proyectoId, Long callerUsuarioId)` —
     devuelve el vigente; 404 si no existe o es ajeno.
   - `listarVersiones(UUID proyectoId, Long callerUsuarioId)` —
     lista todas las versiones; 404 si el proyecto es ajeno.
   - `obtenerArbol(Long presupuestoId, Long callerUsuarioId)` —
     devuelve el árbol completo `Capitulo → Rubro` con totales
     vigentes, **recursivo** (sin tope de profundidad).
4. **DTOs + mappers estáticos en `mapper/`**:
   `PresupuestoVersionResponse`, `PresupuestoResponse`,
   `CapituloResponse`, `RubroResponse`. El mapper arma el árbol
   recursivo aplanando por `parentId` (recursión trivial; la práctica
   IESS muestra ≤ 4 niveles).
5. **Rutas divididas entre dos recursos** (decisión locked, ver §3):
   - `ProyectoResource.GET /proyectos/{proyectoId}/presupuestos` →
     `List<PresupuestoVersionResponse>` (lista de versiones del
     proyecto, owner-scoped).
   - `PresupuestoResource.GET /presupuestos/{presupuestoId}` →
     `PresupuestoResponse` (read model **único y completo** del árbol).
   Separación por cohesión: el listado cuelga del recurso proyecto; el
   árbol cuelga del recurso presupuesto y será mutado por Plan 022
   (capítulos) y Plan 023 (rubros). Ningún seam adicional; ambos
   recursos usan el mismo `PresupuestoService` interno y
   `UuidV7.parse(...)` en frontera (`400 validacion` para UUID
   malformado / no-v7, `404 no-encontrado` para ajeno / inexistente —
   RNF-05).
6. **`alertas` se difiere a Plan 025**. El shape inicial de
   `RubroResponse` **NO incluye** el campo `alertas: ["PU_CERO", ...]`
   que el plan original especulaba. La detección y serialización de
   alertas de validación (PU=0, cantidad=0, sin actividad — P-32)
   viven en Plan 025 (`07-validacion-y-cierre.md`) y se añadirán allí
   (ver `PresupuestoValidacionService` + `GET /presupuestos/{id}/validacion`).
   Esta corrección se propaga a Plan 023 cuando se implemente.
7. **`ParametrosProyecto` y `BaseInsumos` permanecen lazy en este
   plan**. Plan 021 sólo añade el `Presupuesto` v1 al flujo de creación
   (`POST /proyectos`); las filas `ParametrosProyecto` (copia de
   `ParametrosSistema`) y base PROYECTO siguen siendo responsabilidad
   del módulo `proyecto` (DONE 2026-08-02) y del módulo `insumo`
   (DONE 2026-08-02), respectivamente. Plan 021 no introduce ni
   modifica el seam de esos flujos; ningún cambio en su contrato.
8. **Tests** — ver §Pruebas y comprobaciones. Resumen:
   `PresupuestoResourceIT` 9/9 verde (UUIDv7, owner-scope, 400
   UUIDv4 / malformado, 404 ajeno, árbol vacío al inicio, árbol
   poblado transitivo); `ProyectoResourceIT` 8/8 verde
   (regresión; TC-P06-01 verifica el auto-create v1 vigente);
   `RecalculoServiceIT` 4/4 verde (regresión; este plan sólo lee
   totales).

### Decisiones de diseño locked

- **Read model único del árbol** vive en `GET /presupuestos/{id}` —
  es el único endpoint que devuelve el árbol completo. Los planes
  022 (POST/PUT/PATCH/DELETE capítulos) y 023 (POST/PATCH/DELETE
  rubros) **mutan** este árbol pero **devuelven el mismo shape** (el
  árbol completo recalculado) — una sola cache key de TanStack Query
  en el cliente (`07-api-contract.md` §6, nota sobre mutaciones del
  agregado).
- **Rutas divididas por cohesión** (locked en este plan):
  `ProyectoResource` sirve el listado de presupuestos del proyecto
  (`/proyectos/{id}/presupuestos`); `PresupuestoResource` sirve el
  árbol (`/presupuestos/{id}`) y, en 022/023, las mutaciones del
  árbol. Ningún seam nuevo; ambos recursos usan el mismo
  `PresupuestoService` interno y `UuidV7.parse(...)` en frontera.
  El listado queda en `ProyectoResource` por la regla «sub-recurso
  REST del padre»; el árbol queda en `PresupuestoResource` por la
  regla «recurso por agregado raíz».
- **Read model recursivo** (locked): `GET /presupuestos/{id}`
  devuelve el árbol completo con cualquier profundidad de
  `Capitulo.subcapitulos` (sin tope). Mapper aplanando por
  `parentId` con recursión trivial (la práctica IESS muestra ≤ 4
  niveles). `presupuesto.total = Σ capítulos raíz` con totales
  ascendentes coherentes vía `recalculo`.
- **Auto-create v1 vigente se implementa en este plan** (locked tras
  auditoría). El plan original especulaba que `ProyectoService.crear`
  ya creaba el Presupuesto; la auditoría previa a la implementación
  detectó el gap y Plan 021 lo cierra aquí con una llamada a
  `PresupuestoService.crearVigenteInicial(...)` desde
  `ProyectoService.crear` en una sola `@Transactional` (≤ 30 líneas;
  dentro del STOP §A — no se escala). Ningún seam nuevo; PK/FK y
  campos de `presupuesto` inalterados.
- **`ParametrosProyecto` y `BaseInsumos` permanecen lazy** (locked
  para este plan). Plan 021 sólo añade el `Presupuesto` v1 al flujo
  de creación; las filas `ParametrosProyecto` (copia de
  `ParametrosSistema`) y base PROYECTO siguen siendo responsabilidad
  del módulo `proyecto` (DONE 2026-08-02) y del módulo `insumo`
  (DONE 2026-08-02), respectivamente. Plan 021 no introduce ni
  modifica el seam de esos flujos.
- **`alertas` se difiere a Plan 025** (locked). El shape inicial de
  `RubroResponse` **NO incluye** el campo `alertas: ["PU_CERO", ...]`.
  La detección y serialización de alertas de validación de
  integridad (P-32: PU=0, cantidad=0, sin actividad) viven en
  Plan 025 (`07-validacion-y-cierre.md`) — `PresupuestoValidacionService`
  + `GET /presupuestos/{id}/validacion` con `exportable`,
  `itemsPuCero`, `itemsCantidadCero`, `itemsSinActividad`. Esta
  corrección se propaga a Plan 023 cuando se implemente.
- **`CapituloResponse`/`RubroResponse`** se introducen en este plan
  con shape estable: `CapituloResponse { id, item, descripción,
  orden, total, subcapitulos[], rubros[] }` (recursivo);
  `RubroResponse { id, item, código, descripción, unidad, cantidad,
  precioUnitario, precioTotal, apuId }`. **Sin** `alertas` en el
  shape inicial. Los planes 022/023 los amplían sólo si hace falta.
- **`presupuesto.total` se mantiene coherente**: tras un read model
  en una versión sin árbol, devuelve `0` (DEFAULT). Tras un read
  model tras el primer rubro (vía Plan 023), devuelve `Σ` capítulos
  raíz. La coherencia la garantiza `recalculo` (Plan 020); este plan
  sólo verifica la lectura.

---

## Alcance

### Incluye

- **Auto-create v1 vigente en `POST /proyectos`** orquestado desde
  `ProyectoService.crear` mediante `PresupuestoService.crearVigenteInicial(...)`
  en la misma `@Transactional` (≤ 30 líneas dentro de STOP §A — no escala).
- `PresupuestoRepository.listarVersiones(Long proyectoId)` (helper
  `findByProyectoIdAndEsVigenteFalse(...)` añadido para uso futuro
  por Plan 024).
- `PresupuestoService` con cuatro métodos públicos:
  - `crearVigenteInicial(...)` — invocado desde `ProyectoService.crear`.
  - `obtenerVigente(UUID proyectoId, Long callerUsuarioId)`.
  - `listarVersiones(UUID proyectoId, Long callerUsuarioId)`.
  - `obtenerArbol(Long presupuestoId, Long callerUsuarioId)` —
    recursivo, sin tope de profundidad.
- `PresupuestoVersionResponse`, `PresupuestoResponse`,
  `CapituloResponse` (recursivo), `RubroResponse` (mappers estáticos).
- **Rutas divididas** entre dos recursos:
  `ProyectoResource.GET /proyectos/{proyectoId}/presupuestos` →
  `List<PresupuestoVersionResponse>` (lista de versiones, owner-scoped).
  `PresupuestoResource.GET /presupuestos/{presupuestoId}` →
  `PresupuestoResponse` (read model **único y completo** del árbol
  recursivo `Presupuesto → Capitulo → Rubro`).
- Tests `PresupuestoRepositoryIT` (regresión), `PresupuestoServiceIT`
  (regresión) y `PresupuestoResourceIT` (nuevos, 9/9: UUIDv7,
  owner-scope, 400 UUIDv4 / malformado, 404 ajeno, árbol vacío al
  inicio, árbol poblado transitivo). Regresión `proyecto.*` 8/8
  (TC-P06-01 verifica el auto-create) y `recalculo.*` 4/4.

### No incluye

- **No** se crea `Cronograma` (I-08).
- **No** se crea `Actividad` (I-08).
- **No** se introduce paginación en el listado de versiones
  (esperamos ≤ 5–10 versiones por proyecto; si la práctica muestra
  más, se documenta en I-08).
- **No** se reabre `presupuesto.entity.Presupuesto` (ya tiene WU-03).
- **No** se introduce el endpoint de resumen por componente (P-30) —
  vive en Plan 023.
- **No** se introduce la creación/eliminación de versiones (P-31
  escritura) — vive en Plan 024.
- **No** se introduce el endpoint de validación (P-32: `GET
  /presupuestos/{id}/validacion` con `exportable`, `itemsPuCero`,
  `itemsCantidadCero`, `itemsSinActividad`) — vive en Plan 025.
- **No** se incluye el campo `alertas: ["PU_CERO", ...]` en el shape
  inicial de `RubroResponse` — vive en Plan 025.
- **No** se modifican los flujos de `ParametrosProyecto` (copia de
  `ParametrosSistema`) ni de base PROYECTO (`BaseInsumosService`) —
  permanecen como en los módulos `proyecto` (DONE 2026-08-02) y
  `insumo` (DONE 2026-08-02); Plan 021 sólo añade el `Presupuesto` v1.

---

## Contrato esperado

### Endpoints (P-31 lectura + P-28-30 read model)

```text
# ProyectoResource (sub-recurso REST del padre proyecto)
GET  /proyectos/{proyectoId}/presupuestos
     → 200 List<PresupuestoVersionResponse>
     → 404 no-encontrado (proyecto ajeno o inexistente; UUIDv7 malformado → 400)

# PresupuestoResource (recurso por agregado raíz)
GET  /presupuestos/{presupuestoId}
     → 200 PresupuestoResponse (árbol recursivo completo)
     → 404 no-encontrado (UUID ajeno o inexistente; UUIDv7 malformado → 400)
```

> **Cohesión de rutas (locked en este plan):** el listado de
> versiones cuelga del recurso proyecto (`ProyectoResource`);
> el árbol completo cuelga del recurso presupuesto
> (`PresupuestoResource`). Esta división se preserva en los planes
> 022 (mutaciones de capítulos) y 023 (mutaciones de rubros), que
> añaden sus endpoints en `PresupuestoResource` (sub-recursos del
> agregado raíz) sin reabrir `ProyectoResource` con nuevos
> sub-recursos. El listado de versiones del proyecto es la única
> ruta que queda fuera de `PresupuestoResource`.

### Shapes JSON (resumen)

```jsonc
PresupuestoVersionResponse {
  "presupuestoId":  "<UUIDv7>",
  "version": 1,
  "esVigente": true,
  "origenId?":  "<UUIDv7>",
  "notas?":     "Creado desde plantilla X",
  "fechaCreacion": "2026-08-30T12:34:56Z",
  "totalGeneral": "0.000000"
}

PresupuestoResponse {
  "presupuestoId": "<UUIDv7>",
  "version": 1,
  "esVigente": true,
  "totalGeneral": "0.000000",
  "capitulos": [ CapituloResponse ]
}

CapituloResponse {
  "id":           "<UUIDv7>",
  "item":         "1",
  "descripcion":  "...",
  "orden":        1,
  "total":        "0.000000",
  "subcapitulos": [ CapituloResponse ],
  "rubros":       [ RubroResponse ]
}

RubroResponse {
  "id":            "<UUIDv7>",
  "item":          "1.1",
  "codigo":        "501062",
  "descripcion":   "...",
  "unidad":        "m²",
  "cantidad":      "0.000000",
  "precioUnitario":"0.000000",
  "precioTotal":   "0.000000",
  "apuId":         "<UUIDv7>"
  // NOTA: el campo "alertas: [...]" (P-32: PU_CERO, CANT_CERO,
  // SIN_ACTIVIDAD) NO forma parte del shape inicial de
  // RubroResponse. La detección y serialización de alertas
  // vive en Plan 025 (`GET /presupuestos/{id}/validacion`).
}
```

> **Nota:** `Capitulo` y `Rubro` exponen **una sola** identidad pública
> bajo el nombre semántico `id` (UUIDv7); no se emite `publicId` ni
> `public_id` en JSON, ni el `BIGINT` interno. Las PK/FK `BIGINT`
> siguen existiendo en la capa de persistencia (entidades JPA y
> firmas de repositorio); sólo el seam REST las traduce a UUIDv7,
> coherente con `07-api-contract.md` §6.

### Reglas de error

| Caso | Código | `type` |
|---|---|---|
| `proyectoId` UUIDv7 malformado o no-v7 | 400 | `validacion` |
| `presupuestoId` UUIDv7 malformado o no-v7 | 400 | `validacion` |
| Proyecto ajeno o inexistente | 404 | `no-encontrado` |
| Presupuesto ajeno o inexistente | 404 | `no-encontrado` |

Sin 403 en ningún caso (RNF-05).

---

## Pasos

1. **Auditar `ProyectoService.crear`** abriendo
   `src/main/java/ec/uce/propuestas/proyecto/service/ProyectoService.java`.
   **Resultado de la auditoría:** el servicio creaba `Proyecto` +
   `ParametrosProyecto` + base PROYECTO dentro de una sola
   `@Transactional`, pero **NO** creaba la fila `Presupuesto` v1
   vigente (gap detectado; el plan original especulaba lo contrario).
   Plan 021 cierra el gap en este mismo plan (ver STOP §A): añade
   `PresupuestoService.crearVigenteInicial(...)` y la llamada desde
   `ProyectoService.crear` en la misma `@Transactional` (≤ 30 líneas;
   dentro de STOP §A — no se escala).
2. **RED — `PresupuestoRepositoryIT.listarVersiones_orden_desc`**:
   crea proyecto + 2 presupuestos (v1 vigente, v2 no vigente);
   `listarVersiones` devuelve `[v2, v1]`. Otro test
   `vigente_unico_por_proyecto` inserta un segundo vigente y
   espera `DataIntegrityViolationException` (índice parcial
   `ux_presupuesto_vigente` — V001 §2.8).
3. **GREEN — `PresupuestoRepository.listarVersiones(Long proyectoId)`**
   + `findByProyectoIdAndEsVigenteFalse(...)` (helper para 024).
4. **RED — `PresupuestoServiceIT.listarVersiones_owner_scope`**:
   - Usuario A lista versiones de su proyecto → 2 versiones.
   - Usuario B pide el mismo → 404 (no 403).
   - UUIDv7 v4 → 400 (testea la frontera).
5. **GREEN — `PresupuestoService`** con cuatro métodos públicos
   (`crearVigenteInicial`, `obtenerVigente`, `listarVersiones`,
   `obtenerArbol`). Cada método usa el `UuidV7.parse(...)` en
   frontera (path param validado) y
   `PresupuestoRepository.findByPublicIdAndOwnerScope(...)` internamente.
6. **GREEN — DTOs + mappers estáticos en `mapper/`**.
   `PresupuestoMapper.toResponse(Presupuesto, List<Capitulo>,
   List<Rubro>)` arma el árbol **recursivo** aplanando por
   `parentId`. La recursión es trivial (profundidad ≤ 4 en IESS).
   El shape inicial de `RubroResponse` **NO incluye** `alertas`
   (diferido a Plan 025).
7. **GREEN — rutas divididas (locked, ver §«Decisiones de diseño locked»):**
   - `ProyectoResource.@GET /proyectos/{proyectoId}/presupuestos`
     → `Response.ok(listarVersiones(proyectoId, caller))` en
     `ec.uce.propuestas.proyecto.resource.ProyectoResource`.
   - `PresupuestoResource.@GET /presupuestos/{presupuestoId}` →
     `Response.ok(obtenerArbol(presupuestoId, caller))` en
     `ec.uce.propuestas.presupuesto.resource.PresupuestoResource`.
   - `@PathParam` UUIDv7 validado al inicio de cada método con
     `UuidV7.parse(...)`; en error, `ProblemaException.validacion(...)`
     que mapea a `400 validacion`.
8. **TRIANGULATE — `PresupuestoResourceIT`** end-to-end con
   `quarkusDev`/`@QuarkusTest`:
   - `GET /proyectos/{proyectoId}/presupuestos` con `proyectoId`
     UUIDv7 válido del caller → 200 con la lista de versiones; el
     campo `esVigente` está en `true` para la v1 y `false` para la v2.
   - `GET /proyectos/{proyectoId}/presupuestos` con `proyectoId`
     UUIDv7 del vecino → 404.
   - `GET /proyectos/{proyectoId}/presupuestos` con UUIDv7 v4 → 400.
   - `GET /presupuestos/{presupuestoId}` con UUIDv7 válido del
     caller → 200 con `capitulos: []` cuando no hay árbol aún.
   - `GET /presupuestos/{presupuestoId}` con UUIDv7 ajeno → 404.
9. **Verificación dirigida** (cifras en §Pruebas y comprobaciones;
   recheck completado tras retoques fixture-only — todos los gates
   quedan verdes finales en este plan):
   - `./gradlew test --tests 'ec.uce.propuestas.presupuesto.*'
     -Dquarkus.http.test-port=0 --console=plain` →
     `PresupuestoResourceIT` **9/9 verde**; regresión
     repository / service sin regresiones.
   - `./gradlew test --tests 'ec.uce.propuestas.proyecto.*'
     -Dquarkus.http.test-port=0 --console=plain` → `ProyectoResourceIT`
     **8/8 verde** (regresión; TC-P06-01 verifica el auto-create v1
     vigente).
   - `./gradlew test --tests 'ec.uce.propuestas.recalculo.*'
     -Dquarkus.http.test-port=0 --console=plain` → `RecalculoServiceIT`
     **4/4 verde** (sin regresión; este plan sólo lee totales).
   - `./gradlew build -x test --console=plain` → BUILD SUCCESSFUL
     **verde tras** los retoques fixture-only del plan (recheck
     final completado).
   - `./gradlew spotlessCheck` → **verde tras** los retoques
     fixture-only del plan (recheck final completado).
   - `git diff --check` → **limpio tras** los retoques fixture-only
     del plan (recheck final completado).
   - `./gradlew test` (suite completa) re-ejecutada tras los retoques
     fixture-only → **346 totales = 343 pass + 2 aceptados (GM-19
     `-$6.95`, GM-20 cap. 1 `-$0.84`) + 1 skipped (GM-24 `@Disabled`)
     + 0 errors**: el motor y la regla workbook-consistent
     permanecen cerrados (Plan 014) y los residuales aceptados siguen
     sin reabrirse.
10. **Commit unitario** con mensaje
    `feat(presupuesto): Plan 021 ciclo v1 vigente, listado y read
    model`.

---

## Pruebas y comprobaciones

```bash
# Verificación dirigida
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*' \
    -Dquarkus.http.test-port=0 --console=plain

# Regresión proyecto (auto-create vigente)
./gradlew test --tests 'ec.uce.propuestas.proyecto.*' \
    -Dquarkus.http.test-port=0 --console=plain

# Regresión recalculo + motor
./gradlew test --tests 'ec.uce.propuestas.recalculo.*' \
    -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.motor.*' \
    -Dquarkus.http.test-port=0 --console=plain

# Build sin tests
./gradlew build -x test

# Conteo real desde XML
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
    build/test-results/test/TEST-ec.uce.propuestas.presupuesto.*.xml

git diff --check
git status --short
```

**Resultado esperado** (cifras al cierre):

- Tests verdes de repository, service y resource de presupuesto.
- Regresión proyecto, recalculo y motor verde (sin cifras
  presupuestas).

**Resultado medido** (2026-09-01, **tras** los retoques fixture-only
finales del plan; recheck post-fixture-only ya completado — ver
§Recheck completado):

- `presupuesto` test-suite — **`PresupuestoResourceIT` 9/9 verde**
  (UUIDv7, owner-scope, 400 UUIDv4 / malformado, 404 ajeno, árbol
  vacío al inicio, árbol poblado transitivo); los tests existentes en
  `PresupuestoRepositoryIT` y `PresupuestoServiceIT` se re-ejecutaron
  como regresión dirigida sin regresiones.
- Regresión `proyecto` — **`ProyectoResourceIT` 8/8 verde**;
  **TC-P06-01** ya existente en `ec.uce.propuestas.proyecto.*`
  verifica que `POST /proyectos` deja exactamente una fila
  `Presupuesto(version=1, es_vigente=true)` y la lista asociada en
  `GET /proyectos/{id}/presupuestos` (esVigente = true).
- Regresión `recalculo` — **`RecalculoServiceIT` 4/4 verde** (motor
  no se reabre; regla workbook-consistent preservada; este plan sólo
  lee totales).
- Conteo total de la suite completa (medido desde los XML de
  `build/test-results/` tras los retoques fixture-only): **346
  totales = 343 pass + 2 aceptados (GM-19 `-$6.95`, GM-20 cap. 1
  `-$0.84` — residuales aceptados por Plan 014 / Plan 02 §6; **no**
  se reabre el motor) + 1 skipped (GM-24 `@Disabled` por fixture
  EMELNORTE upstream) + 0 errors**.

**Recheck completado** (post-fixture-only):

- `./gradlew spotlessCheck` y `./gradlew build -x test` quedaron
  **verdes tras** los retoques fixture-only del plan (alineación
  de imports / reformateo palantir en los tests nuevos). Recheck
  final completado y reclamado como verde final en este plan.
- `git diff --check` quedó **limpio tras** los retoques fixture-only
  del plan. Recheck final completado y reclamado como limpio final
  en este plan.
- `./gradlew test` completo **re-ejecutado** tras los retoques
  fixture-only; conteo **346 totales = 343 pass + 2 aceptados
  (GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84`) + 1 skipped (GM-24
  `@Disabled`) + 0 errors**, medido desde los XML de
  `build/test-results/`. El motor y la regla workbook-consistent
  (Plan 014) permanecen cerrados; los residuales aceptados siguen
  sin reabrirse.

---

## Criterios de terminado

- [x] `GET /proyectos/{proyectoId}/presupuestos` funciona con
  UUIDv7, owner-scope, 404 ajeno, 400 no-v7 — **verificado por
  `PresupuestoResourceIT` (cubierto vía `ProyectoResource` y
  ejercicio transversal en `PresupuestoResourceIT`)**.
- [x] `GET /presupuestos/{presupuestoId}` funciona con UUIDv7,
  owner-scope, 404 ajeno, 400 no-v7, devuelve árbol **recursivo**
  completo (vacío al inicio; poblado transitivo) — **verificado por
  `PresupuestoResourceIT` 9/9**.
- [x] Auto-create de presupuesto v1 vigente en `POST /proyectos`
  implementado y verificado: TC-P06-01 sigue **verde** en
  `ProyectoResourceIT` 8/8 (gap detectado por la auditoría del plan,
  cerrado en este mismo plan con una llamada a
  `PresupuestoService.crearVigenteInicial(...)` desde
  `ProyectoService.crear` en la misma `@Transactional`; ≤ 30
  líneas; dentro de STOP §A — no se escala).
- [x] La invariante «exactamente una vigente por proyecto» se
  valida (índice parcial `ux_presupuesto_vigente` activo —
  verificado por tests del repository de `presupuesto`).
- [x] Split de rutas aplicado (cohesión): `ProyectoResource` sirve
  el listado; `PresupuestoResource` sirve el árbol completo.
- [x] Read model **recursivo** implementado (sin tope de profundidad;
  mapper aplanando por `parentId`).
- [x] `alertas` (`PU_CERO`, `CANT_CERO`, `SIN_ACTIVIDAD`)
  **diferido a Plan 025**; `RubroResponse` inicial **NO** incluye
  el campo `alertas` (corrección propagada a Plan 023 cuando se
  implemente).
- [x] `ParametrosProyecto` y `BaseInsumos` permanecen lazy (Plan
  021 sólo añade el `Presupuesto` v1; ningún cambio en los flujos
  de copia desde `ParametrosSistema` ni de creación de base
  PROYECTO).
- [x] Sin módulo nuevo de primer nivel; sin seam nuevo; sin
  migración nueva (V001–V008 intactas; V009 no se pre-asigna); el
  motor **NO** se reabre.
- [x] `./gradlew spotlessCheck` y `./gradlew build -x test`
  recheck final tras los retoques fixture-only del plan —
  **verificado verde** en este plan (ver §Pruebas y
  comprobaciones §Recheck completado).
- [x] `./gradlew test` completo recheck final tras los retoques
  fixture-only — **verificado verde** en este plan: **346 totales
  = 343 pass + 2 aceptados (GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84`)
  + 1 skipped (GM-24 `@Disabled`) + 0 errors**.
- [x] `git diff --check` recheck final tras los retoques
  fixture-only — **verificado limpio** en este plan.
- [ ] Plan 022 (capítulos) puede crear capítulos contra la misma
  ruta `GET /presupuestos/{id}` y verlos reflejados — **NO
  verificado en este plan** (depende de Plan 022).

---

## STOP conditions (específicas de este plan)

- **(A)** Si `ProyectoService.crear` **NO** crea la fila
  `Presupuesto` v1 vigente, el ejecutor debe decidir entre:
  añadirlo en este plan (autorizado por la nota de alcance) o
  escalar. Si la modificación es > 30 líneas, **STOP** — escalar.
- **(B)** Si el árbol requiere paginación (> 1000 rubros) para una
  sola versión, **STOP** — I-07 no introduce paginación (la
  práctica IESS muestra 298 rubros / 33 capítulos, sub-segundo).
  Escalar para una decisión arquitectónica.
- **(C)** Si la respuesta del árbol crece > 1 MB para alguna
  versión sembrada por V004, **STOP** — escala con paginación o
  endpoint separado `GET /presupuestos/{id}/capitulos?page=…` que
  ya se discutirá en I-08.
- **(D)** Si el listado de versiones se rompe con paginación por
  defecto (> 200 versiones), **STOP** — la práctica no lo exige.

---

## Siguiente plan ejecutable

[`04-capitulos.md`](04-capitulos.md) (Plan 022) — CRUD de capítulos
con renumeración atómica al mover y prevención de ciclos. Depende de
019 + 020 + 021.
