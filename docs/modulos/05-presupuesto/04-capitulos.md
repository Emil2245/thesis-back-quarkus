# Plan 022 — CRUD de capítulos con renumeración atómica y prevención de ciclos (P-28)

> **Plan 022** del módulo [`05-presupuesto`](00.md). PLANNED / READY —
> 2026-08-31. **No implementado todavía.** Cubre P-28
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

**PLANNED / READY — 2026-08-31.** El ejecutor actualiza esta sección
al término. Resultado esperado: «DONE (YYYY-MM-DD). TC-P28-01/02/03
verdes; CRUD completo con UUIDv7 + owner-scope; `git diff --check`
limpio; regresión APU/recalculo verde.»

---

## Contexto / estado actual

### Lo que ya existe

1. **`Capitulo` y `CapituloRepository`** (Plan 019).
2. **`recalculo` write-through** (Plan 020) con `Alcance.Version`
   (no se introduce `Alcance.Capitulo` ni `Alcance.Rubro`; la
   propagación grano fino del agregado se sirve recorriendo el árbol
   por alcance de versión).
3. **`PresupuestoResponse` / `CapituloResponse` / `RubroResponse`**
   (Plan 021) — el árbol completo se devuelve desde `GET
   /presupuestos/{id}`. Las mutaciones de este plan devuelven **el
   mismo shape** (árbol recalculado), coherente con
   `07-api-contract.md` §6.

### Decisiones de diseño locked

- **Profundidad sin tope** (DM §4, decisión §17 #14). El UI puede
  sugerir jerarquías planas; el backend no restringe.
- **`item` autogenerado** en backend: el cliente nunca envía el
  `item`; lo calcula el servicio tras la inserción/movimiento. El
  shape `CapituloCrearRequest` no incluye `item` (sólo
  `descripcion`, `parentId?`, `orden?` opcional).
- **Prevención de ciclos estructural**:
  - Al hacer `PATCH …/mover`, el destino `parentId` no puede ser un
    descendiente del capítulo movido. Se detecta con una sola query
    recursiva (`WITH RECURSIVE`) o comparando contra el conjunto de
    descendientes pre-calculado.
  - Si `parentId == null` (mover a raíz), siempre válido (no crea
    ciclos).
  - Si `parentId == capitulo.id`, siempre inválido (es él mismo).
- **Mover `orden` reordena hermanos** dentro del mismo padre. El
  orden es 1-based y contiguo `[1..n]`; tras un mover se renumeran
  TODOS los hermanos para preservar contigüidad (similar a APU
  detalle MOVE atómico, Plan 03 / `07-api-contract.md` §5 fila P-21).
- **Eliminar capítulo con subárbol**: cascadea en `capitulo.parent_id`
  y `rubro.capitulo_id`. Los APUs sobreviven (D-09 + V001 §3: cascadea
  a rubros, no a APUs; el `rubro.apu_id` FK se mantiene).
- **Write-through**:
  - Tras crear/editar/mover/eliminar, se invoca
    `RecalculoService.recalcular(Alcance.Version(presupuestoId))` —
    el orquestador de `Version` recorre el árbol y propaga totales.
  - Decisión recomendada: `Alcance.Version(...)` para todas las
    mutaciones del árbol (crear, editar, mover, eliminar); el
    recorrido grano fino se hace dentro del orquestador.
- **Numeración jerárquica**: el backend computa el `item` como la
  concatenación del `item` del padre + `"." + orden` (1-based). Para
  raíz, `"<orden>"`.

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
  el backend ajusta a contigüidad (clamp).

---

## Contrato esperado

### Endpoints (P-28 completo)

```text
POST   /presupuestos/{presupuestoId}/capitulos
       Body: { "descripcion": "OBRA CIVIL", "parentId?": "<UUIDv7>", "orden?": 1 }
       → 201 PresupuestoResponse (árbol recalculado)
       → 400 validacion  · 404 no-encontrado  · 409 si parentId es descendiente (no aplica al crear)

PUT    /presupuestos/{presupuestoId}/capitulos/{capituloId}
       Body: { "descripcion": "OBRA CIVIL" }
       → 200 PresupuestoResponse (árbol recalculado)
       → 400 validacion  · 404 no-encontrado

PATCH  /presupuestos/{presupuestoId}/capitulos/{capituloId}/mover
       Body: { "parentId?": "<UUIDv7>", "orden": 2 }
       → 200 PresupuestoResponse (árbol recalculado con nueva numeración)
       → 400 validacion (parentId = él mismo o descendiente)
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

### Reglas de error

| Caso | Código | `type` |
|---|---|---|
| `presupuestoId` UUIDv7 malformado | 400 | `validacion` |
| `capituloId` UUIDv7 malformado | 400 | `validacion` |
| `parentId` (en crear o mover) UUIDv7 malformado | 400 | `validacion` |
| `parentId` == `capituloId` | 400 | `validacion` |
| `parentId` es descendiente de `capituloId` (al mover) | 400 | `validacion` |
| `descripcion` vacía o > longitud máxima | 400 | `validacion` |
| `orden` < 1 o > hermanos + 1 (en mover) | 400 | `validacion` |
| Proyecto ajeno o inexistente | 404 | `no-encontrado` |
| Capítulo ajeno o inexistente | 404 | `no-encontrado` |
| `parentId` no pertenece al mismo presupuesto | 400 | `validacion` |

Sin 403 en ningún caso (RNF-05).

---

## Pasos

1. **RED — `CapituloRepositoryIT.crear_raiz_y_subcapitulo`**:
   crea capítulo raíz + subcapítulo + sub-sub; los `item`s son
   `"1"`, `"1.1"`, `"1.1.1"` autogenerados; `UNIQUE
   (presupuesto_id, item)` se respeta.
2. **RED — `CapituloRepositoryIT.mover_crea_ciclo_rechaza`**
   (estructural): usar `WITH RECURSIVE` para detectar el
   descendiente. La query está en el repo y devuelve un boolean.
3. **RED — `CapituloServiceIT.item_autogenerado_y_renumerado`**:
   - Crear 3 raíces con `orden` 1, 2, 3 → `item` "1", "2", "3".
   - Mover la 3ª a la posición 1 → el resultado debe tener `item`
     "1" (la antigua 1), "2" (la antigua 2 que ahora es 3ª → "3" si
     se inserta antes), "2"… en realidad la implementación inserta
     en `orden` 1 y empuja las demás +1. Verifica contigüidad
     1..n post-mover.
   - Mover a sub-capítulo → el `item` cambia de `"1"` a `"1.1"`,
     `"1.2"`, etc.
4. **GREEN — `CapituloService`** con la numeración:
   - Helper privado `calcularItem(parent, ordenHermanos)`.
   - Para raíz: `item = String.valueOf(orden)`.
   - Para subcapítulo: `item = parent.item + "." + orden`.
   - Helper `renumerarHermanosExcepto(Long padreId, Long exceptId)`
     que ajusta contigüidad.
   - Helper `renumerarSubarbol(Long capituloId)` que ajusta el
     `item` de toda la rama tras un cambio de padre.
5. **GREEN — `mover(...)`** con detección de ciclos:
   - Si `parentId == null`, mover a raíz.
   - Si `parentId == capituloId`, `ProblemaException.validacion(...)`.
   - Si `parentId` está en el conjunto de descendientes de
     `capituloId` (consulta recursiva), `ProblemaException.validacion(...)`.
   - Insertar en `orden` y desplazar hermanos ±1 atómicamente
     (transacción única).
   - Renumerar el árbol (la rama completa) tras cambiar padre.
   - Invocar `RecalculoService.recalcular(Alcance.Version(presupuestoId))`
     al final.
6. **GREEN — `eliminar(...)`**:
   - Cascade `capitulo.parent_id` borra subárbol.
   - Cascade `rubro.capitulo_id` borra rubros del subárbol.
   - Los APUs sobreviven (FK `rubro.apu_id` se elimina al cascadear
     rubros; los APUs no se tocan).
   - `RecalculoService.recalcular(Alcance.Version(...))`.
7. **GREEN — `CapituloResource`** con los 4 verbos + `@PathParam`
   UUIDv7 validados en frontera.
8. **TRIANGULATE — `CapituloResourceIT`** end-to-end:
   - `TC-P28-01` árbol IESS (33 capítulos) — al menos una rama con
     profundidad 3.
   - `TC-P28-02` mover una rama y verificar renumeración + ciclo
     prevenido.
   - `TC-P28-03` eliminar capítulo con ítems → subárbol borrado,
     APUs sobreviven.
   - UUIDv7 v4 en cualquier path → 400.
   - Capítulo ajeno → 404.
9. **Verificación dirigida**:
   - `./gradlew test --tests 'ec.uce.propuestas.presupuesto.*'
     -Dquarkus.http.test-port=0 --console=plain` → verde.
   - `./gradlew test --tests 'ec.uce.propuestas.apu.*'
     -Dquarkus.http.test-port=0 --console=plain` → regresión APU
     verde (los APUs no se borran).
   - `./gradlew test --tests 'ec.uce.propuestas.recalculo.*'
     -Dquarkus.http.test-port=0 --console=plain` → regresión
     recalculo verde.
   - `./gradlew build -x test --console=plain` →
     BUILD SUCCESSFUL.
   - `git diff --check` → sin salida.
10. **Commit unitario** con mensaje
    `feat(presupuesto): Plan 022 CRUD capítulos con renumeración
    atómica`.

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
./gradlew test --tests 'ec.uce.propuestas.motor.*' \
    -Dquarkus.http.test-port=0 --console=plain

# Build sin tests
./gradlew build -x test

# Conteo real
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
    build/test-results/test/TEST-ec.uce.propuestas.presupuesto.*.xml

git diff --check
git status --short
```

**Resultado esperado** (cifras al cierre): TC-P28-01/02/03 verdes;
regresión APU/recalculo/motor verde; `git diff --check` limpio.

---

## Criterios de terminado

- [ ] CRUD completo de capítulos con UUIDv7, owner-scope, errores
  canónicos.
- [ ] Numeración jerárquica autogenerada en backend.
- [ ] Renumeración atómica de hermanos al mover (orden contiguo
  1..n post-operación).
- [ ] Renumeración de la rama al cambiar de padre.
- [ ] Detección y rechazo de ciclos al mover (`parentId` en
  descendientes o == self).
- [ ] Eliminación con cascade a subárbol y rubros (APUs sobreviven).
- [ ] `RecalculoService` invocado al final de cada mutación.
- [ ] Tests verdes: TC-P28-01 (árbol IESS), TC-P28-02 (mover +
  renumeración + ciclo), TC-P28-03 (eliminar subárbol).
- [ ] Regresión APU/recalculo/motor verde.
- [ ] `git diff --check` limpio.
- [ ] Plan 023 (rubros) puede crear ítems contra el árbol resultante.

---

## STOP conditions (específicas de este plan)

- **(A)** Si la detección de ciclos con `WITH RECURSIVE` es
  prohibitiva (> 100 ms para profundidad 4), **STOP** — usar
  pre-cálculo en memoria del conjunto de descendientes con un SELECT
  simple (`SELECT id FROM capitulo WHERE presupuesto_id = ? AND
  item LIKE '<padre.item>.%'`). Documentar la simplificación.
- **(B)** Si al mover un capítulo con 100+ descendientes la
  renumeración de la rama excede 5 segundos (lock pesimista), **STOP**
  — escalar para decisión arquitectónica (batch update vs deep copy).
- **(C)** Si `RecalculoService.recalcular(Alcance.Version(...))`
  tarda > 1 segundo para el caso real (298 rubros), **STOP** — Plan
  020 §2.4 fija «sub-segundo»; si no se cumple, revisar cache en
  motor o el `VersionSnapshotBuilder`.
- **(D)** Si aparece evidencia de que el `WITH RECURSIVE` no detecta
  un ciclo (test falla), **STOP** — no improvisar; revisar la query
  y los índices.

---

## Siguiente plan ejecutable

[`05-rubros-totales-resumen.md`](05-rubros-totales-resumen.md) (Plan
023) — CRUD de rubros (ítems) vinculados 1:1 al APU, cantidades de
obra, write-through de `precio_total`/`capitulo.total`/
`presupuesto.total`, y resumen por componente (P-30).
