# Plan 021 — Ciclo de vida del presupuesto: auto-create v1 vigente, listado y read model

> **Plan 021** del módulo [`05-presupuesto`](00.md). PLANNED / READY —
> 2026-08-31. **No implementado todavía.** Cubre los aspectos del
> **ciclo de vida del presupuesto** que viven fuera de los CRUD de
> capítulos (Plan 022), rubros (Plan 023) y versionado (Plan 024):
> el **auto-create de la versión 1 vigente** al crear un proyecto (P-06
> §4, ya parcialmente implementado por `ProyectoService`), el
> **listado de versiones** (P-31 parte listado) y el **read model
> completo** del árbol `Presupuesto → Capitulo → Rubro` (P-28-30 read).

## Resultado esperado

Cuando un usuario crea un proyecto vía `POST /proyectos`, el sistema
crea automáticamente, dentro de la misma transacción:

1. La fila `Proyecto` + `ParametrosProyecto` (copia de
   `ParametrosSistema`).
2. La base PROYECTO (auto-creada por `BaseInsumosService`).
3. La fila `Presupuesto` con `version = 1`, `es_vigente = true`,
   `origen_id = NULL`, `notas = NULL`, `porcentaje_indirecto = NULL`
   (hereda del proyecto), `total = 0`.

`GET /proyectos/{id}/presupuestos` lista las versiones del proyecto
(nº, fecha, notas, total, vigente). `GET /presupuestos/{id}` devuelve
el árbol completo actual (read model único de la jerarquía), con
`Capitulo` y `Rubro` enriquecidos desde sus entidades. La invariante
«exactamente una versión vigente por proyecto» se verifica con el
índice único parcial `ux_presupuesto_vigente` (V001 §2.8).

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

**PLANNED / READY — 2026-08-31.** El ejecutor actualiza esta sección al
término. Resultado esperado: «DONE (YYYY-MM-DD). `presupuesto`
test-suite verde (entity + repository + service + resource); TC-P06-01
verifica auto-create; TC-P28-01 (sin árbol aún) pasa; spotless y
`git diff --check` limpios.»

---

## Contexto / estado actual

### Lo que ya existe (DONE, NO se reabre)

1. **`ProyectoService.crear` ya crea un `Presupuesto` v1 vigente** —
   el flujo P-06 lo exige (DM §3: «el sistema crea, en una
   transacción: `Proyecto`, `ParametrosProyecto`, `Presupuesto` versión
   1 vigente, …»). Verificación: tests existentes en
   `ec.uce.propuestas.proyecto.*` ya cubren que al crear un proyecto
   existe exactamente una versión vigente (TC-P06-01).
2. **`PresupuestoRepository.findVigenteDeProyecto`** (módulo
   `presupuesto`): devuelve el vigente de un proyecto. Listado de
   versiones (`findByProyectoIdOrderByVersionDesc`) NO existe todavía;
   este plan lo añade.
3. **Índice único parcial `ux_presupuesto_vigente`** (V001 §2.8):
   `CREATE UNIQUE INDEX ux_presupuesto_vigente ON presupuesto
   (proyecto_id) WHERE es_vigente;` — garantiza la invariante
   «exactamente una vigente por proyecto».
4. **`UUIDv7` parse canónico** en
   `ec.uce.propuestas.common.UuidV7` (Plan 07).
5. **`ProblemaException` + `GlobalExceptionMapper`** en
   `ec.uce.propuestas.common` (Plan 07).

### Lo que falta (este plan lo construye)

1. **`PresupuestoRepository.listarVersiones(Long proyectoId)`** —
   devuelve todas las versiones ordenadas por `version DESC`. Sin
   paginación (esperamos ≤ 5–10 versiones por proyecto).
2. **`PresupuestoService`** — capa de servicio (CRUD plano donde la
   lógica es trivial; este plan introduce lo mínimo):
   - `obtenerVigente(UUID proyectoId, Long callerUsuarioId)` —
     devuelve el vigente; 404 si no existe.
   - `listarVersiones(UUID proyectoId, Long callerUsuarioId)` —
     lista todas las versiones; 404 si el proyecto es ajeno.
   - `obtenerArbol(Long presupuestoId, Long callerUsuarioId)` —
     devuelve el árbol completo `Capitulo → Rubro` con totales
     write-through vigentes.
3. **`PresupuestoResponse`** + `PresupuestoVersionResponse` +
   mappers estáticos en `mapper/`.
4. **`PresupuestoResource`** (versión 1: GET only) —
   - `GET /proyectos/{proyectoId}/presupuestos` →
     `List<PresupuestoVersionResponse>`.
   - `GET /presupuestos/{presupuestoId}` → `PresupuestoResponse`
     (árbol completo).
5. **Tests** que cubran:
   - Auto-create vigente en `ProyectoService.crear` (verificación:
     TC-P06-01, ya cubierto por proyecto; este plan **NO** añade
     test nuevo allí — verifica que sigue verde tras tocar
     `presupuesto.repository`).
   - Listado de versiones (varias versiones, vigente marcada).
   - Read model devuelve árbol vacío cuando no hay capítulos.
   - Read model devuelve árbol con capítulos y rubros tras Plan 022
     + 023 (estos tests vivirán en 022/023; este plan sólo verifica
     el camino vacío).

### Decisiones de diseño locked

- **Read model único del árbol** vive en `GET /presupuestos/{id}` —
  es el único endpoint que devuelve el árbol completo. Los planes
  022 (POST/PUT/PATCH/DELETE capítulos) y 023 (POST/PATCH/DELETE
  rubros) **mutan** este árbol pero **devuelven el mismo shape** (el
  árbol completo recalculado) — una sola cache key de TanStack Query
  en el cliente (`07-api-contract.md` §6, nota sobre mutaciones del
  agregado).
- **`CapituloResponse`/`RubroResponse`** se introducen en este plan
  con shape estable pero campos **mínimos** (id, item, descripción,
  orden, total para capítulos; id, item, código, descripción,
  unidad, cantidad, precioUnitario, precioTotal, apuId para rubros).
  Los planes 022/023 los amplían sólo si hace falta (debería ser
  innecesario — el shape ya cubre el árbol).
- **Auto-create vigente no se duplica**. Si
  `ProyectoService.crear` ya lo hace (verificación 1 arriba), este
  plan sólo **documenta** el contrato. Si NO lo hace (gap detectado
  por el test de regresión), este plan lo implementa como
  `PresupuestoService.crearVigenteInicial(...)` y lo invoca desde
  `ProyectoService.crear` en una sola `@Transactional`.
- **`presupuesto.total` se mantiene coherente**: tras un read model
  en una versión sin árbol, devuelve `0` (DEFAULT). Tras un read
  model tras el primer rubro, devuelve `Σ` capítulos raíz. La
  coherencia la garantiza `recalculo` (Plan 020); este plan sólo
  verifica la lectura.

---

## Alcance

### Incluye

- `PresupuestoRepository.listarVersiones(Long proyectoId)` +
  `listarHijosPorCapitulos(...)` + `listarRubrosPorCapitulos(...)`.
- `PresupuestoService` con tres métodos públicos:
  - `obtenerVigente(UUID proyectoId, Long callerUsuarioId)`.
  - `listarVersiones(UUID proyectoId, Long callerUsuarioId)`.
  - `obtenerArbol(Long presupuestoId, Long callerUsuarioId)`.
- `PresupuestoVersionResponse`, `PresupuestoResponse`,
  `CapituloResponse`, `RubroResponse` (mappers estáticos).
- `PresupuestoResource` con 2 endpoints (GET only).
- Tests `PresupuestoRepositoryIT`, `PresupuestoServiceIT`,
  `PresupuestoResourceIT` con UUIDv7, owner-scope, árbol vacío y
  árbol poblado (vía seeds manuales en test, no vía Plan 022/023).

### No incluye

- **No** se crea `Cronograma` (I-08).
- **No** se crea `Actividad` (I-08).
- **No** se introduce paginación en el listado de versiones
  (esperamos ≤ 5–10 versiones por proyecto; si la práctica muestra
  más, se documenta en I-08).
- **No** se modifica `ProyectoService.crear` si ya crea el
  presupuesto v1 vigente. Si no lo crea (gap), se modifica en
  este plan con una sola llamada transaccional.
- **No** se reabre `presupuesto.entity.Presupuesto` (ya tiene WU-03).
- **No** se introduce el endpoint de resumen por componente (P-30) —
  vive en Plan 023.
- **No** se introduce el endpoint de validación (P-32) — vive en
  Plan 025.
- **No** se introduce la creación/eliminación de versiones (P-31
  escritura) — vive en Plan 024.

---

## Contrato esperado

### Endpoints (P-31 lectura + P-28-30 read model)

```text
GET  /proyectos/{proyectoId}/presupuestos
     → 200 List<PresupuestoVersionResponse>
     → 404 no-encontrado (proyecto ajeno o inexistente; UUIDv7 malformado → 400)

GET  /presupuestos/{presupuestoId}
     → 200 PresupuestoResponse (árbol completo)
     → 404 no-encontrado (UUID ajeno o inexistente; UUIDv7 malformado → 400)
```

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
  "apuId":         "<UUIDv7>",
  "alertas":       [ "PU_CERO" ]   // presente sólo si aplica
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
   `src/main/java/ec/uce/propuestas/proyecto/service/ProyectoService.java`
   y verificando que ya crea la fila `Presupuesto(version=1,
   es_vigente=true)`. Si la crea, este plan sólo verifica; si no,
   implementar el gap en este mismo plan (ver STOP §A abajo).
2. **RED — `PresupuestoRepositoryIT.listarVersiones_orden_desc`**:
   crea proyecto + 2 presupuestos (v1 vigente, v2 no vigente);
   `listarVersiones` devuelve `[v2, v1]`. Otro test
   `vigente_unico_por_proyecto` inserta un segundo vigente y
   espera `DataIntegrityViolationException` (índice parcial).
3. **GREEN — `PresupuestoRepository.listarVersiones(Long proyectoId)`**
   + `findByProyectoIdAndEsVigenteFalse(...)` (helper para 024).
4. **RED — `PresupuestoServiceIT.listarVersiones_owner_scope`**:
   - Usuario A lista versiones de su proyecto → 2 versiones.
   - Usuario B pide el mismo → 404 (no 403).
   - UUIDv7 v4 → 400 (testea la frontera).
5. **GREEN — `PresupuestoService`** con los tres métodos públicos.
   Cada método usa el `UuidV7.parse(...)` en frontera (path param
   validado) y `PresupuestoRepository.findByPublicIdAndOwnerScope(...)`
   internamente.
6. **GREEN — DTOs + mappers estáticos en `mapper/`**.
   `PresupuestoMapper.toResponse(Presupuesto, List<Capitulo>,
   List<Rubro>)` arma el árbol aplanando por `parentId` recursivo.
   La recursión es trivial (profundidad ≤ 4 en IESS).
7. **GREEN — `PresupuestoResource`**:
   - `@GET /proyectos/{proyectoId}/presupuestos` →
     `Response.ok(listarVersiones(proyectoId, caller))`.
   - `@GET /presupuestos/{presupuestoId}` →
     `Response.ok(obtenerArbol(presupuestoId, caller))`.
   - `@PathParam` UUIDv7 validado al inicio con `UuidV7.parse(...)`;
     en error, `ProblemaException.validacion(...)`.
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
9. **Verificación dirigida**:
   - `./gradlew test --tests 'ec.uce.propuestas.presupuesto.*'
     -Dquarkus.http.test-port=0 --console=plain` → tests verdes.
   - `./gradlew test --tests 'ec.uce.propuestas.proyecto.*'
     -Dquarkus.http.test-port=0 --console=plain` → regresión
     proyecto verde (verifica auto-create sigue OK).
   - `./gradlew build -x test --console=plain` →
     BUILD SUCCESSFUL.
   - `git diff --check` → sin salida.
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

---

## Criterios de terminado

- [ ] `GET /proyectos/{proyectoId}/presupuestos` funciona con
  UUIDv7, owner-scope, 404 ajeno, 400 no-v7.
- [ ] `GET /presupuestos/{presupuestoId}` funciona con UUIDv7,
  owner-scope, 404 ajeno, 400 no-v7, devuelve árbol completo (vacío
  al inicio).
- [ ] Auto-create de presupuesto v1 vigente en `POST /proyectos`
  verificado: el test TC-P06-01 sigue verde.
- [ ] La invariante «exactamente una vigente por proyecto» se
  valida (índice parcial activo).
- [ ] `git diff --check` limpio.
- [ ] Plan 022 (capítulos) puede crear capítulos contra la misma
  ruta `GET /presupuestos/{id}` y verlos reflejados.

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
