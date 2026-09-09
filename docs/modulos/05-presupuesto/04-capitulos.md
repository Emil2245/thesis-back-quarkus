# Plan 022 — CRUD de capítulos con renumeración atómica y prevención de ciclos (P-28)

> **Plan 022** del módulo [`05-presupuesto`](00.md). **DONE — 2026-09-01.**
> Cubre P-28
> ([`design/03-procesos-detalle.md`](../../../../thesis-docs/plan/design/03-procesos-detalle.md) §E):
> jerarquía ilimitada de capítulos, autogeneración de `item`
> (`"1"`, `"1.1"`, `"1.1.1"`…), renumeración atómica al mover/reordenar,
> eliminación con borrado en cascada del subárbol (los APUs sobreviven).

## Resultado esperado

El usuario puede crear/editar/mover/eliminar capítulos y subcapítulos
de cualquier profundidad (`presupuesto_id, item` único por versión;
profundidad sin tope — DM §4 + workbook IESS con profundidad 3). La
API exige UUIDv7 en path; los APUs sobreviven al borrado de un
capítulo (P-28 explícito: «los APUs siguen existiendo»); el
write-through de `capitulo.total` se propaga por `recalculo` (Plan 020).
La prevención de ciclos es estructural: `PATCH …/mover` rechaza
mover un capítulo dentro de su propio subárbol.

## Dependencias

- [Plan 019 — Identidad y persistencia](01-identidad-y-persistencia.md).
- [Plan 020 — `recalculo` write-through](02-recalculo-write-through.md).
- [Plan 021 — Ciclo de presupuesto](03-ciclo-presupuesto.md).
- `PresupuestoRepository`, `CapituloRepository`, `RubroRepository`
  disponibles (019).

## Estado de cierre

**DONE — 2026-09-01 — verificación dirigida completa.** CRUD completo
con UUIDv7 en frontera y owner-scope (404 si ajeno, 400 si UUIDv7
malformado/no-v7, RNF-05: nunca 403). Cuatro endpoints en un recurso
dedicado `CapituloResource`, todos con `presupuestoId` en el path
(la cohesión se mantiene dentro del recurso; `PresupuestoResource`
sigue sirviendo sólo el read model de Plan 021).

**Renumeración atómica (entidades gestionadas, dos pasadas).** Toda
mutación estructural termina en `CapituloService.renumerarArbol(...)`,
que recorre los capítulos en una sola carga (`listarPorPresupuesto`),
normaliza el `orden` de cada nivel a `1..n` contiguo y reescribe el
`item` de todo el árbol en dos pasadas: la primera aparca cada item
que va a cambiar bajo el prefijo temporal `~<id>` (espacio de nombres
disjunto de los items canónicos, que nunca empiezan por `~`; longitud
≤ 19 en `VARCHAR(20)`) y hace `flush()` para liberar los items
originales; la segunda asigna los items definitivos. Sin ese
aparcado, una permutación de hermanos (`"3" → "1"`, `"1" → "2"`…) o
un cambio de padre que recompone la rama violaría `UNIQUE
(presupuesto_id, item)` a mitad del flush. Las operaciones se hacen
siempre sobre entidades gestionadas — nunca con `UPDATE` JPQL — para
que el `RecalculoService`, llamado en la misma transacción, vea el
estado fresco de `item`/`orden`/`parentId` y no perseda totales sobre
filas stale.

**Prevención de ciclos (recorrido de ancestros en memoria).** El
camino caliente IESS tiene ≤ 4 niveles y las mutaciones no son masivas;
el plan activó su **STOP (A)** documentado y descartó `WITH RECURSIVE`
en favor de `CapituloRepository.esDescendienteOigual(movingId,
candidateParentId)`, que sube por la cadena de `parentId` con
`findById` hasta encontrar `movingId` o agotar la rama. El tope
defensivo (1024 saltos) protege contra un ciclo accidental en BD. La
detección se ejecuta **antes** de cualquier escritura: si el destino
es self o descendiente, `CapituloService.mover(...)` lanza
`ProblemaException.validacion(...)` → 400 `validacion` sin tocar el
árbol. `parentId == null` (mover a raíz) nunca crea ciclo.

**Renumeración del subárbol movido.** `asignarNivel(...)` recompone
recursivamente el `item` del nodo movido y de todos sus descendientes
(`padre.item + "." + orden`; raíz = `orden`), preservando el orden de
hermanos del nivel origen y destino. El conjunto `visitados` en el
recorrido es una defensa barata contra un ciclo accidental en BD.

**Cascade + supervivencia de APU.** El `DELETE` delega en la FK
`capitulo.parent_id ON DELETE CASCADE` (Postgres — no Hibernate) para
borrar el subárbol de capítulos y, transitivamente, la FK
`rubro.capitulo_id ON DELETE CASCADE` borra los rubros del subárbol.
`CapituloService.eliminar(...)` hace `delete → flush() → clear()` para
que la sesión no siga cacheando filas que la cascade acaba de borrar;
luego renumera el árbol superviviente (los hermanos se compactan a
`1..n`). Los APUs sobreviven (D-09 + V001 §3: la FK
`rubro.apu_id → apu.id` es `ON DELETE RESTRICT`, y al desaparecer los
rubros el catálogo de APUs queda intacto y reutilizable).

**Write-through por mutación.** Cada mutación (`crear`,
`editarDescripcion`, `mover`, `eliminar`) termina con
`RecalculoService.recalcular(new Alcance.Version(presupuestoId))` —
único seam introducido en I-07 (Plan 020) — siempre después de un
`flush()` para que el `VersionSnapshotBuilder` vea los nuevos
`item`/`orden`/`parentId`. La respuesta es el `PresupuestoResponse`
completo y fresco (árbol recalculado), coherente con el contrato de
Plan 021; el resource decide el status (201 en crear, 200 en el resto).

**DTOs.** `CapituloCrearRequest(@NotBlank @Size(max=255) descripcion,
parentId?, orden?)`; `CapituloEditarRequest(@NotBlank @Size(max=255)
descripcion)`; `CapituloMoverRequest(parentId?, @NotNull @Min(1)
orden)`. La regla «orden omitido = append al final» vive en el
servicio; el rechazo explícito de `orden` fuera de `[1, hermanos+1]` es
`CapituloService.validarPosicion(...)` → 400 `validacion` (bean
validation sólo cubre `@NotNull @Min(1)` en mover; la cota superior
depende del tamaño del nivel destino).

**Decisiones locked vs. plan original.**

- **STOP (A) activado:** `WITH RECURSIVE` → recorrido `findById` en
  memoria (`esDescendienteOigual`). Documentado en el javadoc del
  repository y aquí.
- **Renumeración en dos pasadas + item temporal pre-asignado a
  capítulos nuevos** (`itemAparcadoProvisional()`): no estaba en el
  plan; descubierto durante el TDD al implementar `TC_P22_30` (mover
  que permuta hermanos). Sin esa invariante, el flush viola la
  constraint UNIQUE.
- **Managed entities exclusivamente:** no estaba explícito en el plan;
  surge de que `RecalculoService` reescribe `total` en la misma
  transacción y un `UPDATE` JPQL dejaría la caché de primer nivel
  stale.

**Evidencia (medida en `build/test-results/test/`).**

- `CapituloResourceIT` **30/30 verdes** (TC_P22_01…TC_P22_30;
  incluye 03/29/30 para permutación de hermanos, 16/26 para
  renumeración de subárbol al cruzar padre, 17/18 para ciclos
  self/descendiente, 21 para cascade + supervivencia de APU, 27 para
  owner-scope 404).
- `PresupuestoResourceIT` **9/9** (sin regresión; Plan 021).
- `RecalculoServiceIT` **4/4** (sin regresión; Plan 020).
- `apu.*` **49/49** verde (sin regresión).
- `motor.*` **45 totales = 42 verdes + 2 rojos (GM-19 `-$6.95`,
  GM-20 cap. 1 `-$0.84` — residuales aceptados por Plan 014, no se
  reabre el motor) + 1 omitido (GM-24 `@Disabled` por fixture
  EMELNORTE upstream)**.
- Suite completa **376 totales = 373 pass + 2 aceptados (GM-19 +
  GM-20) + 1 skipped (GM-24) + 0 errors**.
- `./gradlew spotlessCheck` verde, `./gradlew build -x test` verde,
  `git diff --check` limpio.

**Siguiente plan ejecutable:** [`05-rubros-totales-resumen.md`](05-rubros-totales-resumen.md)
(Plan 023) — CRUD de rubros (ítems) vinculados 1:1 al APU, cantidades
de obra, write-through de `precio_total`/`capitulo.total`/
`presupuesto.total`, y resumen por componente (P-30). DAG I-07 sigue
desbloqueado.

---

## Contexto / estado actual

### Lo que ya existe

1. **`Capitulo` y `CapituloRepository`** (Plan 019, base para los
   métodos owner-scoped añadidos en este plan).
2. **`recalculo` write-through** (Plan 020) con `Alcance.Version` —
   único seam de I-07. No se introduce `Alcance.Capitulo` ni
   `Alcance.Rubro`; la propagación grano fino del agregado se sirve
   recorriendo el árbol por alcance de versión.
3. **`PresupuestoResponse` / `CapituloResponse` / `RubroResponse`**
   (Plan 021) — el árbol completo se devuelve desde `GET
   /presupuestos/{id}`. Las mutaciones de este plan devuelven **el
   mismo shape** (árbol recalculado), coherente con
   `07-api-contract.md` §6.

### Decisiones de diseño locked (alineadas con la implementación)

- **Profundidad sin tope** (DM §4, decisión §17 #14). El UI puede
  sugerir jerarquías planas; el backend no restringe.
- **`item` autogenerado** en backend: el cliente nunca envía el
  `item`; lo calcula el servicio. `CapituloCrearRequest` sólo lleva
  `descripcion`, `parentId?`, `orden?` opcional.
- **Prevención de ciclos estructural**:
  - `PATCH …/mover` rechaza con 400 `validacion` cuando el destino es
    self o descendiente. Implementación:
    `CapituloRepository.esDescendienteOigual(movingId,
    candidateParentId)` recorre la cadena de `parentId` con `findById`
    en memoria (**STOP (A) activado** — `WITH RECURSIVE` descartado;
    ver Estado de cierre).
  - `parentId == null` (mover a raíz) siempre válido.
  - `parentId == capitulo.id` rechazado por el caso trivial de la
    misma función.
- **Mover `orden` reordena hermanos** dentro del mismo padre. El
  orden es 1-based y contiguo `[1..n]`; tras un mover se renumeran
  TODOS los hermanos para preservar contigüidad.
- **Eliminar capítulo con subárbol**: cascadea en `capitulo.parent_id`
  y `rubro.capitulo_id` (FKs `ON DELETE CASCADE`). Los APUs sobreviven
  (D-09 + V001 §3: cascadea a rubros, no a APUs; el `rubro.apu_id`
  FK se elimina junto con el rubro, pero `apu.id` permanece intacto y
  reutilizable).
- **Write-through**: tras crear/editar/mover/eliminar se invoca
  `RecalculoService.recalcular(new Alcance.Version(presupuestoId))`,
  siempre después de un `flush()` para que el snapshot vea los nuevos
  `item`/`orden`/`parentId`.
- **Numeración jerárquica**: `item = orden` para raíz;
  `item = padre.item + "." + orden` para subcapítulo. Recomputada
  recursivamente tras cada mutación estructural, en dos pasadas para
  no violar `UNIQUE (presupuesto_id, item)` durante el flush (ver
  Estado de cierre).

---

## Alcance

### Incluye

- `CapituloService` con métodos:
  - `crear(Long presupuestoId, Long callerUsuarioId,
    CapituloCrearRequest)`.
  - `editar(Long capituloId, Long callerUsuarioId,
    CapituloEditarRequest)`.
  - `mover(Long capituloId, Long callerUsuarioId,
    CapituloMoverRequest)`.
  - `eliminar(Long capituloId, Long callerUsuarioId)`.
- `CapituloResource` con 5 endpoints:
  - `POST /presupuestos/{presupuestoId}/capitulos` (P-28 crear).
  - `PUT /presupuestos/{presupuestoId}/capitulos/{capituloId}` (P-28 editar).
  - `PATCH /presupuestos/{presupuestoId}/capitulos/{capituloId}/mover`
    (P-28 mover atómico).
  - `DELETE /presupuestos/{presupuestoId}/capitulos/{capituloId}` (P-28 eliminar).
- DTOs + mapper estático (`CapituloMapper`).
- Numeración automática del `item` en backend.
- Detección y rechazo de ciclos en mover.
- Renumeración atómica de hermanos al mover (orden contiguo).
- Renumeración atómica del árbol al cambiar `parentId` (los `item`s
  de la rama cambian).
- Cascade a subárbol (`capitulo.parent_id CASCADE`) y a rubros
  (`rubro.capitulo_id CASCADE`) en DELETE.

### No incluye

- **No** se introduce `numero_secuencia` global (DM §5: derivable al
  exportar).
- **No** se permite editar `presupuestoId` (capítulo pertenece a su
  versión; para mover entre versiones, deep copy en Plan 024).
- **No** se introduce soft-delete ni archivado de capítulos.
- **No** se modifican las tablas V001–V007 (V008 ya aplicado en 019).
- **No** se introduce el endpoint de rubros (Plan 023).
- **No** se introduce `parentId = null` con `orden > siblings + 1`:
  el backend rechaza con 400 `validacion` (no hay clamp silencioso;
  la cota superior vive en `CapituloService.validarPosicion`).

---

## Contrato implementado

### Endpoints (P-28 completo) — recurso dedicado `CapituloResource`

Todas las rutas cuelgan de `CapituloResource` con
`@Path("/presupuestos/{presupuestoId}/capitulos")`; el `presupuestoId`
forma parte de **cada** ruta por contrato (split por cohesión frente a
`PresupuestoResource`, que sólo sirve el read model de Plan 021).
UUIDv7 se parsea en frontera con `UuidV7.parse(...)` para que UUID
malformado o no-v7 rechace con 400 `validacion` antes de cualquier
acceso al repositorio.

```text
POST   /presupuestos/{presupuestoId}/capitulos
       Body: { "descripcion": "OBRA CIVIL", "parentId?": "<UUIDv7>", "orden?": 1 }
       → 201 PresupuestoResponse (árbol recalculado)
       → 400 validacion  · 404 no-encontrado  · 400 si parentId pertenece a otro presupuesto

PUT    /presupuestos/{presupuestoId}/capitulos/{capituloId}
       Body: { "descripcion": "OBRA CIVIL" }
       → 200 PresupuestoResponse (árbol recalculado)
       → 400 validacion  · 404 no-encontrado

PATCH  /presupuestos/{presupuestoId}/capitulos/{capituloId}/mover
       Body: { "parentId?": "<UUIDv7>", "orden": 2 }
       → 200 PresupuestoResponse (árbol recalculado con nueva numeración)
       → 400 validacion (parentId self/descendiente, orden fuera de [1, hermanos+1],
                         parentId de otro presupuesto)
       · 404 no-encontrado

DELETE /presupuestos/{presupuestoId}/capitulos/{capituloId}
       → 200 PresupuestoResponse (árbol recalculado tras cascade)
       · 404 no-encontrado
```

### Shapes JSON

```jsonc
CapituloCrearRequest  { "descripcion": "", "parentId?": "<UUIDv7>", "orden?": 1 }
CapituloEditarRequest { "descripcion": "" }
CapituloMoverRequest  { "parentId?": "<UUIDv7>", "orden": 2 }
```

`parentId` null en `CapituloMoverRequest` = mover a raíz.

### Reglas de error (canónicas verificadas por `CapituloResourceIT`)

| Caso | Código | `type` | Test |
|---|---|---|---|
| `presupuestoId` UUIDv7 malformado o no-v7 | 400 | `validacion` | TC_P22_10, TC_P22_23 |
| `capituloId` UUIDv7 malformado o no-v7 | 400 | `validacion` | TC_P22_23 |
| `parentId` (en crear o mover) UUIDv7 malformado | 400 | `validacion` | (cubierto por frontera UuidV7) |
| `parentId` == `capituloId` (auto-ciclo) | 400 | `validacion` | TC_P22_17 |
| `parentId` es descendiente de `capituloId` | 400 | `validacion` | TC_P22_18 |
| `descripcion` vacía | 400 | `validacion` | TC_P22_07, TC_P22_14 |
| `descripcion` > 255 caracteres | 400 | `validacion` | TC_P22_08 |
| `orden` < 1 (en crear o mover) | 400 | `validacion` | TC_P22_05, TC_P22_19 |
| `orden` > hermanos + 1 (en crear o mover) | 400 | `validacion` | TC_P22_06, TC_P22_19 |
| `parentId` pertenece a otro presupuesto (cross-version o cross-project del mismo owner) | 400 | `validacion` | TC_P22_09, TC_P22_20, TC_P22_28 |
| Proyecto / presupuesto ajeno o inexistente | 404 | `no-encontrado` | TC_P22_11 |
| Capítulo ajeno o inexistente | 404 | `no-encontrado` | TC_P22_12, TC_P22_22, TC_P22_27 |
| Intruso opera sobre capítulos de otro owner | 404 | `no-encontrado` | TC_P22_27 |

Sin 403 en ningún caso (RNF-05): «ajeno» se sirve siempre como 404,
nunca 403.

---

## TDD ejecutado (RED → GREEN → TRIANGULATE)

1. **RED — `CapituloRepositoryIT` y `CapituloServiceIT`** (entrega
   previa a Plan 019) sentaron las bases de `Capitulo` + repos. Plan
   022 extendió con métodos owner-scoped adicionales (`findByPublicId-
   EnPresupuesto`, `existeEnOtroPresupuestoDelOwner`,
   `listarPorPresupuesto`, `listarHermanosEnPresupuesto`,
   `esDescendienteOigual`).
2. **GREEN — `CapituloService`** con la numeración:
   - Inserción con `item` temporal pre-asignado
     (`itemAparcadoProvisional()`) para evitar que el INSERT choque con
     el item del hermano que aún lo ocupa.
   - `renumerarArbol(...)`: normaliza `orden` por nivel y reescribe
     `item` en dos pasadas (aparcado `~<id>` → flush → items
     definitivos).
   - `asignarNivel(...)`: recursión protegida por un `Set<Long>
     visitados` (defensa contra un ciclo accidental en BD).
3. **GREEN — `mover(...)`** con detección de ciclos (STOP (A)
   activado: recorrido `findById` en memoria en lugar de `WITH
   RECURSIVE`):
   - `parentId == null` → mover a raíz.
   - `parentId == capituloId` o descendiente →
     `ProblemaException.validacion(...)` antes de escribir nada.
   - Insertar en `orden` y desplazar hermanos ±1 atómicamente (en la
     misma `@Transactional`).
   - Renumerar el árbol completo (la rama y los hermanos de origen y
     destino) tras cambiar padre.
   - Invocar
     `RecalculoService.recalcular(new Alcance.Version(presupuestoId))`
     al final, tras `flush()`.
4. **GREEN — `eliminar(...)`**:
   - `delete → flush() → clear()` para que la sesión no cachee filas
     que la cascade acaba de borrar.
   - `capitulo.parent_id CASCADE` borra subárbol; `rubro.capitulo_id
     CASCADE` borra rubros del subárbol. Los APUs sobreviven
     (`rubro.apu_id → apu.id` es `ON DELETE RESTRICT`, pero al
     desaparecer los rubros el catálogo de APUs queda intacto).
   - Renumerar el árbol superviviente (los hermanos se compactan a
     `1..n`).
   - `RecalculoService.recalcular(new Alcance.Version(presupuestoId))`.
5. **GREEN — `CapituloResource`** con los 4 verbos + `@PathParam`
   UUIDv7 parseados en frontera con `UuidV7.parse(...)` y
   `@RolesAllowed({"USUARIO", "SUPER_ADMIN"})`.
6. **TRIANGULATE — `CapituloResourceIT`** end-to-end (30 casos):
   - `TC_P22_01…04` crear (raíz, sub, orden explícito, append).
   - `TC_P22_05…09` frontera de validación en crear (orden inválido,
     descripción vacía/larga, parent de otro presupuesto).
   - `TC_P22_10…12` UUIDv7 malformado/inexistente en path
     (presupuesto + capítulo).
   - `TC_P22_13…14` editar descripción (incluida frontera 400).
   - `TC_P22_15…16` mover (mismo padre, cruzar padre con
     renumeración de subárbol de profundidad 3).
   - `TC_P22_17…18` ciclos self/descendiente rechazados con 400.
   - `TC_P22_19` orden fuera de rango en mover.
   - `TC_P22_20` parent de otro presupuesto en mover.
   - `TC_P22_21` eliminar con cascade — APUs sobreviven
     (`apuSigueExistiendo(apuId) == 1L` tras el `DELETE`).
   - `TC_P22_22…23` 404 / 400 en eliminar y verbos con UUID no-v7.
   - `TC_P22_24…25` recalcula totales tras mutación (write-through).
   - `TC_P22_26` profundidad 3: items `"1.1.1.1"` al cruzar padre.
   - `TC_P22_27` intruso 404 en PUT/POST/DELETE.
   - `TC_P22_28` parent cross-version del mismo proyecto → 400.
   - `TC_P22_29` compactar tras eliminar hermano intermedio.
   - `TC_P22_30` permutación de hermanos dentro del mismo padre (caso
     que activa la invariante de dos pasadas).

---

## Pruebas y comprobaciones

```bash
# Verificación dirigida
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*' \
    -Dquarkus.http.test-port=0 --console=plain

# Regresiones obligatorias
./gradlew test --tests 'ec.uce.propuestas.apu.*' \
    -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.recalculo.*' \
    -Dquarkus.http.test-port=0 --console=plain

# Motor: la suite dirigida se reporta por separado; las residuales
# aceptadas (GM-19/GM-20) y el `@Disabled` GM-24 son contrato vigente
# (Plan 014) — no se reabre el motor.

# Build y formato
./gradlew spotlessCheck
./gradlew build -x test

# Conteo real desde los XML (no se presupone)
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
    build/test-results/test/TEST-*.xml

git diff --check
```

**Resultado medido al cierre** (2026-09-01, desde
`build/test-results/test/`):

- `CapituloResourceIT` 30/30 verde; `PresupuestoResourceIT` 9/9
  verde; `RecalculoServiceIT` 4/4 verde; `apu.*` 49/49 verde;
  `motor.*` 45 totales / 42 verdes + 2 rojos (GM-19 `-$6.95`,
  GM-20 cap. 1 `-$0.84` — residuales aceptados por Plan 014) + 1
  omitido (GM-24 `@Disabled`).
- Suite completa **376 = 373 pass + 2 aceptados (GM-19 + GM-20) +
  1 skipped (GM-24) + 0 errors**.
- `./gradlew spotlessCheck` verde, `./gradlew build -x test` verde,
  `git diff --check` limpio.

---

## Criterios de terminado

- [x] CRUD completo de capítulos con UUIDv7, owner-scope, errores
  canónicos (TC_P22_01…30 verde).
- [x] Numeración jerárquica autogenerada en backend (`item` =
  `padre.item + "." + orden` para sub; `orden` para raíz).
- [x] Renumeración atómica de hermanos al mover (orden contiguo
  `1..n` post-operación; verificada por TC_P22_03 y TC_P22_15).
- [x] Renumeración de la rama al cambiar de padre (TC_P22_16,
  TC_P22_26 — profundidad 3 → `"1.1.1.1"`).
- [x] Detección y rechazo de ciclos al mover (`parentId` self o
  descendiente, TC_P22_17, TC_P22_18).
- [x] Eliminación con cascade a subárbol y rubros; APUs sobreviven
  (TC_P22_21: `apuSigueExistiendo(apuId) == 1L` tras `DELETE`).
- [x] `RecalculoService.recalcular(new Alcance.Version(...))` al
  final de cada mutación (write-through).
- [x] Recurso dedicado `CapituloResource` con las 4 rutas, todas
  con `presupuestoId` en el path.
- [x] Regresión APU/recalculo verde (49/49 + 4/4); suite completa
  sin regresión (sólo los residuales aceptados del motor).
- [x] `git diff --check` limpio, `spotlessCheck` verde, `build -x
  test` verde.
- [x] Plan 023 (rubros) puede crear ítems contra el árbol resultante.

---

## STOP conditions (específicas de este plan)

- **(A)** ~~`WITH RECURSIVE` prohibitiva~~ → **ACTIVADO**: la
  detección de ciclos usa `CapituloRepository.esDescendienteOigual`
  (recorrido `findById` por la cadena de padres en memoria) en lugar
  de una CTE recursiva. La jerarquía IESS tiene ≤ 4 niveles y la
  decisión se ejecuta antes de cualquier escritura, así que el costo
  no es material. Documentado en el javadoc del repository y en
  Estado de cierre.
- **(B)** Renumeración con 100+ descendientes ≤ 5 s — **no activado**:
  el ciclo de TDD no generó un caso > 50 descendientes y los 30 tests
  pasan en < 1 s total. Queda como guard futuro: si un escenario real
  lo dispara, abrir `plans/0NN-capitulo-batch-update.md` antes de
  cualquier optimización.
- **(C)** `RecalculoService.recalcular(Alcance.Version(...))` ≤ 1 s
  para 298 rubros — **no activado**: el `RecalculoServiceIT` 4/4
  pasa y Plan 020 ya cubre la sub-segunda como contrato (no se
  re-ejecuta aquí).
- **(D)** `WITH RECURSIVE` no detecta ciclo — **no aplica**: STOP (A)
  ya simplificó la detección a `findById` en memoria.

---

## Siguiente plan ejecutable

[`05-rubros-totales-resumen.md`](05-rubros-totales-resumen.md) (Plan
023) — CRUD de rubros (ítems) vinculados 1:1 al APU, cantidades de
obra, write-through de `precio_total`/`capitulo.total`/
`presupuesto.total`, y resumen por componente (P-30). DAG I-07 sigue
desbloqueado.
