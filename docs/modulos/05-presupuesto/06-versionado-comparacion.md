# Plan 024 — Versionado de presupuesto: deep copy, vigente única, comparación (P-31)

> **Plan 024** del módulo [`05-presupuesto`](00.md). PLANNED / READY —
> 2026-08-31. **No implementado todavía.** Cubre P-31
> ([`design/03-procesos-detalle.md`](../../../../thesis-docs/plan/design/03-procesos-detalle.md) §E):
> crear versión (deep copy de la origen), marcar vigente (transaccional),
> comparar dos versiones, eliminar versión (protegiendo la vigente).
> El deep copy **copia estructuralmente** las filas `cronograma` y
> `actividad` si existen (DM §3) **sin** implementar el CRUD de
> cronograma (P-33…P-36 — I-08/I-09). La identidad pública UUIDv7
> para `cronograma` y `actividad` se difiere a una migración futura de I-08.

## Resultado esperado

El usuario puede crear una versión 2 a partir de la versión 1 vía
`POST /proyectos/{proyectoId}/presupuestos` (deep copy íntegro: árbol
de capítulos + rubros + APUs + secciones + detalles; cronograma +
actividades si existen; insumos NO se copian — la base es del
proyecto). La nueva versión arranca con `es_vigente = false` y
`total` recalculado idéntico al origen (TC-P31-01 exige tolerancia
0.00 bit-a-bit). `POST /presupuestos/{id}/vigente` marca esa versión
como vigente (transaccional; la anterior pasa a `false`).
`DELETE /presupuestos/{id}` elimina la versión **si no es vigente**
(409 `version-vigente-protegida` si lo es). `GET
/presupuestos/{id}/comparar?con={id2}` devuelve totales lado a lado.

## Dependencias

- [Plan 019 — Identidad y persistencia](01-identidad-y-persistencia.md).
- [Plan 020 — `recalculo` write-through](02-recalculo-write-through.md).
- [Plan 021 — Ciclo de presupuesto](03-ciclo-presupuesto.md).
- [Plan 022 — Capítulos](04-capitulos.md).
- [Plan 023 — Rubros, totales y resumen](05-rubros-totales-resumen.md).
- Módulo `cronograma`/`actividad` **NO implementado** en I-07 — el
  deep copy maneja su existencia condicionalmente.

## Estado de cierre

**PLANNED / READY — 2026-08-31.** El ejecutor actualiza esta sección al
término. Resultado esperado: «DONE (YYYY-MM-DD). TC-P31-01/02/03/04
verdes; deep copy bit-a-bit idéntico al origen (origen sin
modificación); vigente transaccional OK; comparación OK;
`git diff --check` limpio; regresión APU/capítulos/rubros/recalculo/
motor verde.»

---

## Contexto / estado actual

### Lo que ya existe

1. **`Presupuesto` + `PresupuestoRepository` + `PresupuestoService`**
   (Plan 019 + 021).
2. **`Capitulo`, `Rubro`, `Apu`, `ApuSeccion`, `ApuDetalle`,
   `Cronograma`, `Actividad`** como entidades (los APUs ya están
   mapeados en I-05; el resto se añade en este plan con lectura o
   con `findById(BIGINT)` para construir el snapshot del deep copy).
3. **Índice único parcial `ux_presupuesto_vigente`** (V001 §2.8) —
   garantiza la invariante «exactamente una vigente por proyecto».
4. **`RecalculoService` con `Alcance.Version(...)`** (Plan 020).
5. **No existe aún `VersionadoService`** — este plan lo crea.
6. **No existe endpoint de comparación** — este plan lo crea.

### Decisiones de diseño locked

- **Deep copy con remapeo de FKs** (DM §3):
  `Presupuesto` (nuevo) → `Capitulo` (con `parent_id` remapeado a la
  nueva jerarquía) → `Rubro` (con `capitulo_id` y `apu_id` remapeado
  a los nuevos IDs) → `Apu` (nuevos, en la nueva versión) → `ApuSeccion`
  (4 secciones copiadas) → `ApuDetalle` (filas copiadas).
  `cronograma` y `actividad` se copian **estructuralmente** si
  existen en el origen (DM §3: «la copia incluye cronograma con
  períodos»); **no** se implementa su CRUD en I-07.
- **Insumos NO se copian** (la base es del proyecto, compartida entre
  versiones — DM §3).
- **Totales idénticos al origen** (TC-P31-01, tolerancia 0.00): el
  deep copy termina invocando
  `RecalculoService.recalcular(Alcance.Version(presupuestoNuevoId))`
  para que `presupuesto.total` y todos los `capitulo.total`/
  `rubro.precio_total` coincidan bit-a-bit con el origen.
- **Marcar vigente** es transaccional: una sola operación SQL atómica
  o dos UPDATE en una transacción:
  `UPDATE presupuesto SET es_vigente = false WHERE proyecto_id = ? AND
  es_vigente = true; UPDATE presupuesto SET es_vigente = true WHERE id
  = ?;`. Si la segunda falla, la primera hace rollback. La invariante
  «exactamente una vigente» la garantiza el índice parcial.
- **Eliminar versión no vigente** = borrar físicamente:
  cascadea `capitulo.presupuesto_id CASCADE`, `apu.presupuesto_id
  CASCADE`, `cronograma.presupuesto_id CASCADE`. Los APUs que sólo
  viven en esta versión se borran con sus secciones/detalles. Si un
  APU ya está referenciado por un rubro de OTRA versión, no se
  borra (la FK `rubro.apu_id RESTRICT` lo impide al intentar borrar
  la fila `apu` — pero como `apu.presupuesto_id` no es restrictivo,
  en la práctica las versiones comparten APUs por vínculo 1:1; el
  deep copy crea APUs nuevos, no comparte).
  > **Nota arquitectónica (decision consciente):** en el modelo
  > actual, un APU pertenece a UN presupuesto (`apu.presupuesto_id`
  > NOT NULL, FK CASCADE). El deep copy **duplica los APUs** para que
  > la versión nueva sea independiente. Esto difiere de la lectura
  > "los APUs se comparten" — la realidad es que la versión copia
  > duplica APUs **con FK a la nueva versión**. El test TC-P31-01
  > exige que esto sea bit-a-bit idéntico.
- **Comparación** = dos `PresupuestoResponse` lado a lado + `porCapituloRaiz`
  (totales por capítulo raíz). El frontend lo renderiza.
- **No se reabre V001–V007.** No se introduce la migración futura aquí (cronograma/
  actividad UUIDv7).

---

## Alcance

### Incluye

- `VersionadoService` con métodos:
  - `copiarVersion(Long proyectoId, Long callerUsuarioId,
    PresupuestoVersionCrearRequest)`.
  - `marcarVigente(Long presupuestoId, Long callerUsuarioId)`.
  - `eliminar(Long presupuestoId, Long callerUsuarioId)`.
- `PresupuestoVersionResource` con 3 endpoints:
  - `POST /proyectos/{proyectoId}/presupuestos` (P-31 crear).
  - `POST /presupuestos/{presupuestoId}/vigente` (P-31 vigente).
  - `DELETE /presupuestos/{presupuestoId}` (P-31 eliminar).
- `ComparacionResource` con 1 endpoint:
  - `GET /presupuestos/{presupuestoId}/comparar?con={id2}`.
- DTOs `PresupuestoVersionCrearRequest`,
  `ComparacionVersionesResponse`, `ComparacionItem`.
- Tests TC-P31-01/02/03/04 verdes.

### No incluye

- **No** se introduce CRUD de `cronograma` ni `actividad` (P-33…P-36
  → I-08/I-09). El deep copy maneja su existencia si la fila existe
  en el origen.
- **No** se introduce la migración futura (UUIDv7 para `cronograma`/`actividad` —
  I-08). El deep copy **no** asigna `public_id` a estas tablas en
  I-07; las deja con `id` interno BIGINT como hoy.
- **No** se introduce el endpoint de duplicar proyecto (P-09 → N02 §3
  excluido).
- **No** se introduce merge de versiones.
- **No** se introduce archivado de versiones (se borran físicamente).
- **No** se reabre el motor ni se cambia la regla workbook-consistent.

---

## Contrato esperado

### Endpoints (P-31)

```text
POST   /proyectos/{proyectoId}/presupuestos
       Body: { "origenId": "<UUIDv7>", "notas?": "Ajuste 5% descuento" }
       → 201 PresupuestoVersionResponse (la nueva versión)
       · 400 validacion (origenId no UUIDv7)
       · 404 no-encontrado (origen ajeno/inexistente)

POST   /presupuestos/{presupuestoId}/vigente
       → 200 PresupuestoVersionResponse (la versión marcada)
       · 404 no-encontrado

DELETE /presupuestos/{presupuestoId}
       → 204 No Content
       · 404 no-encontrado  · 409 version-vigente-protegida

GET    /presupuestos/{presupuestoId}/comparar?con=<UUIDv7>
       → 200 ComparacionVersionesResponse
       · 400 validacion (con UUIDv7 malformado o == self o distinto
         proyecto)
       · 404 no-encontrado
```

### Shapes JSON

```jsonc
PresupuestoVersionCrearRequest { "origenId": "<UUIDv7>", "notas?": "" }
PresupuestoVersionResponse     {
  "presupuestoId":  "<UUIDv7>",
  "version":        2,
  "esVigente":      false,
  "origenId?":      "<UUIDv7>",
  "notas?":         "",
  "fechaCreacion":  "2026-08-30T...",
  "totalGeneral":   "395115.320000"
}

ComparacionVersionesResponse {
  "versiones": [
    { "presupuestoId": "<UUIDv7>", "version": 1, "totalGeneral": "...",
      "porCapituloRaiz": [
        { "item": "1", "descripcion": "...", "total": "..." }
      ] },
    { "presupuestoId": "<UUIDv7>", "version": 2, "totalGeneral": "...",
      "porCapituloRaiz": [ ... ] }
  ]
}
```

### Reglas de error

| Caso | Código | `type` |
|---|---|---|
| UUIDv7 malformado o no-v7 en path o body | 400 | `validacion` |
| `origenId` no pertenece al proyecto del path | 400 | `validacion` |
| `con` distinto proyecto o == self | 400 | `validacion` |
| Versión vigente al intentar DELETE | 409 | `version-vigente-protegida` |
| Proyecto/versión ajeno o inexistente | 404 | `no-encontrado` |

---

## Pasos

1. **Auditar entidades cronograma/actividad** — confirmar que existen
   como entidades JPA (probablemente sólo como rastro del seed V004).
   Si NO existen, este plan **no las introduce** (I-08); el deep
   copy maneja su existencia condicionalmente con
   `entityManager.find(Cronograma.class, presupuestoId) == null ? skip
   : copy`.
2. **RED — `VersionadoServiceIT.copiarVersion_crea_arbol_identico`**:
   - Sembrar proyecto + presupuesto v1 + árbol IESS (33 capítulos,
     298 rubros, APUs con secciones).
   - `VersionadoService.copiarVersion(proyectoId, caller,
     { origenId: v1.id })`.
   - Verificar:
     - Nueva versión con `version = 2`, `es_vigente = false`.
     - `presupuesto.total` de v2 == v1.total (bit-a-bit).
     - `capitulo.total` recursivos coinciden bit-a-bit.
     - `rubro.precio_total` coinciden bit-a-bit.
     - `rubro.codigo` y `rubro.item` son espejos correctos de la
       jerarquía copiada.
     - El origen v1 NO se modificó.
3. **GREEN — `VersionadoService.copiarVersion`**:
   - Una sola `@Transactional`.
   - INSERT en `presupuesto` (nuevo, `version = max + 1`,
     `es_vigente = false`, `origen_id = origen.id`, `total = 0`
     temporalmente).
   - Recorrer el árbol del origen en **una sola pasada** (BFS o DFS):
     - INSERT `capitulo` con `parent_id` remapeado; preservar `item`,
       `descripcion`, `orden`, `total` (temporalmente 0).
     - INSERT `rubro` con `capitulo_id` remapeado, `apu_id` null
       temporalmente.
     - INSERT `apu` (nuevo, con FK al nuevo presupuesto); preservar
       `codigo`, `descripcion`, `unidad`, `porcentaje_indirecto`,
       `porcentaje_descuento`, `especificacion_tecnica`,
       `costo_*` (temporalmente 0).
     - INSERT 4 `apu_seccion` por APU.
     - INSERT `apu_detalle` filas por sección.
     - UPDATE `rubro.apu_id = apu_nuevo.id` (remap FK).
     - INSERT `cronograma` si existe (FK remapeada).
     - INSERT `actividad` si existe (FK `rubro_id` remapeada).
   - `RecalculoService.recalcular(Alcance.Version(presupuestoNuevoId))`
     al final → totales idénticos al origen.
4. **RED — `VersionadoServiceIT.marcarVigente_transaccional`**:
   - Sembrar v1 vigente + v2 no vigente.
   - `marcarVigente(v2.id, caller)`.
   - Verificar: v2 ahora vigente; v1 ahora no vigente; ambos
     cambios en una sola transacción.
   - Test cruzado: el índice único parcial rechaza 2 vigentes (la
     prueba directa está en Plan 021; este plan verifica el flujo).
5. **GREEN — `VersionadoService.marcarVigente`** con SQL
   transaccional (dos UPDATE consecutivos + commit).
6. **RED — `VersionadoServiceIT.eliminar_protege_vigente`**:
   - `eliminar(v1.vigente)` → 409 `version-vigente-protegida`.
   - `eliminar(v2.noVigente)` → 204; v1 sigue vigente; v2 borrada.
7. **GREEN — `VersionadoService.eliminar`**:
   - DELETE en `presupuesto` con `id = ? AND es_vigente = false`.
     Si afecta 0 filas → 409 (la versión es vigente o no existe).
   - Los cascades de V001 hacen el resto.
8. **GREEN — `ComparacionService`**:
   - Carga ambos presupuestos (con traversal owner-scope).
   - Verifica que pertenecen al mismo proyecto (400 si no).
   - Construye `ComparacionVersionesResponse` con totales y
     `porCapituloRaiz` (sólo capítulos raíz de cada versión, con
     `item` y `descripcion`).
9. **GREEN — endpoints REST** con `@PathParam` UUIDv7 validados.
10. **TRIANGULATE — `VersionadoServiceIT`** + tests e2e:
    - `TC-P31-01` deep copy bit-a-bit idéntico al origen.
    - `TC-P31-02` marcar vigente transaccional.
    - `TC-P31-03` eliminar versión vigente → 409.
    - `TC-P31-04` editar la copia no afecta el origen.
    - Comparación entre v1 y v2.
    - UUIDv7 v4 en path → 400; recurso ajeno → 404.
11. **Verificación dirigida**:
    - `./gradlew test --tests 'ec.uce.propuestas.presupuesto.*'
      -Dquarkus.http.test-port=0 --console=plain` → verde.
    - `./gradlew test --tests 'ec.uce.propuestas.apu.*'
      -Dquarkus.http.test-port=0 --console=plain` → regresión APU
      verde.
    - `./gradlew test --tests 'ec.uce.propuestas.recalculo.*'
      -Dquarkus.http.test-port=0 --console=plain` → regresión
      recalculo verde.
    - `./gradlew test --tests 'ec.uce.propuestas.motor.*'
      -Dquarkus.http.test-port=0 --console=plain` → regresión motor
      verde.
    - `./gradlew build -x test --console=plain` →
      BUILD SUCCESSFUL.
    - `git diff --check` → sin salida.
12. **Commit unitario** con mensaje
    `feat(presupuesto): Plan 024 versionado, deep copy, vigente y
    comparación`.

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

**Resultado esperado** (cifras al cierre): TC-P31-01/02/03/04 +
comparación verdes; totales bit-a-bit idénticos al origen;
regresión APU/capítulos/rubros/recalculo/motor verde; `git diff
--check` limpio.

---

## Criterios de terminado

- [ ] Deep copy crea árbol completo bit-a-bit idéntico al origen.
- [ ] Marcar vigente transaccional: 0 vigentes → 1 vigente; la
  anterior pasa a no vigente.
- [ ] Eliminar versión no vigente = 204; eliminar vigente = 409
  `version-vigente-protegida`.
- [ ] Comparación devuelve totales y `porCapituloRaiz` para ambas
  versiones.
- [ ] El origen NO se modifica al crear/eliminar la copia.
- [ ] Tests TC-P31-01/02/03/04 + comparación verdes.
- [ ] Regresión APU/capítulos/rubros/recalculo/motor verde.
- [ ] `git diff --check` limpio.
- [ ] Plan 025 (validación + cierre) puede operar sobre la versión
  vigente.

---

## STOP conditions (específicas de este plan)

- **(A)** Si el deep copy tarda > 5 segundos para el árbol IESS
  (33 capítulos / 298 rubros), **STOP** — revisar si el round-trip
  por entidad se puede batchear (JDBC batch insert).
- **(B)** Si los totales de la copia no coinciden bit-a-bit con el
  origen después de `RecalculoService.recalcular(...)`, **STOP** —
  documentar el residual esperado (no debe exceder el aceptado en
  GM-19/GM-20 — `-$6.95` / `-$0.84`). Si lo excede, hay un bug.
- **(C)** Si al eliminar una versión no vigente, una FK CASCADE
  falla por un APU referenciado desde otra versión, **STOP** — el
  modelo asume que los APUs se duplican en el deep copy; verificar.
- **(D)** Si la actividad/cronograma no existe como entidad JPA pero
  existe como rastro en el seed, **STOP** — este plan no introduce
  las entidades (es I-08); el deep copy maneja su ausencia
  silenciosamente.

---

## Siguiente plan ejecutable

[`07-validacion-y-cierre.md`](07-validacion-y-cierre.md) (Plan 025) —
validación de integridad (PU=0, cantidad=0, ítems sin actividad),
Bruno `api/bruno/10-presupuesto/`, Graphify refresh, cierre
documental. Depende de 019–024.
