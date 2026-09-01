# Plan 024 — Versionado de presupuesto: deep copy, vigente única, comparación (P-31)

> **Plan 024** del módulo [`05-presupuesto`](00.md). **DONE (2026-09-01).**
> Implementación aplicada bajo `ec.uce.propuestas.presupuesto`. Cubre P-31
> ([`design/03-procesos-detalle.md`](../../../../thesis-docs/plan/design/03-procesos-detalle.md) §E):
> crear versión (deep copy de la origen), marcar vigente (transaccional),
> comparar dos versiones, eliminar versión (protegiendo la vigente).
> El deep copy **copia estructuralmente** las filas `cronograma` y
> `actividad` si existen (DM §3) **sin** implementar el CRUD de
> cronograma (P-33…P-36 — I-08/I-09); ver nota en
> [§Implementación y evidencia](#implementación-y-evidencia) sobre la
> copia con SQL nativo. La identidad pública UUIDv7 para `cronograma`
> y `actividad` se difiere a una migración futura de I-08.

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

**DONE (2026-09-01).** Implementación aplicada; cierre documental
ejecutado en este pase. Sin seam nuevo, sin migraciones, sin reabrir
`motor/`/`recalculo/`/`apu/`/`proyecto/`. `git diff --check` quedó
limpio y `graphify update .` finalizó correctamente (con warnings no
bloqueantes por `tree_sitter_sql` ausente).

### Implementación y evidencia

- **Ubicación:** toda la implementación vive bajo
  `ec.uce.propuestas.presupuesto.{dto,resource,service}` —
  `VersionadoService` (deep copy + marcar vigente + eliminar +
  comparación), `PresupuestoVersionResource` (crear),
  `PresupuestoVigenciaResource` (marcar vigente + eliminar),
  `ComparacionResource` (comparación), DTOs
  `PresupuestoVersionCrearRequest`, `ComparacionVersionesResponse`,
  `ComparacionItem`, `CapituloRaizComparacion`. Apoyo en
  `PresupuestoRepository.maxVersionDeProyecto(...)` y
  `PresupuestoRepository.lockProyectoRow(...)` (modificación
  mínima del repositorio: dos métodos data-access only, sin seam
  nuevo en `proyecto/`).
- **Deep copy bit-a-bit idéntico al origen (TC-P31-01):** una sola
  `@Transactional` recorre el árbol origen (DFS), reinserta con
  UUIDv7 públicos frescos y remapea FKs BIGINT internas
  (`presupuesto` → `capitulo` con `parent_id` → `rubro` →
  `apu` nuevo por rubro → `apu_seccion` (4) → `apu_detalle`);
  preserva referencias compartidas a `insumo` (no se copian) y la
  APU ET; cierra con
  `RecalculoService.recalcular(new Alcance.Version(nuevoId))` para
  que `presupuesto.total`, `capitulo.total` recursivos y
  `rubro.precio_total` coincidan con el origen (tolerancia 0.00).
- **Cronograma / actividad — copia estructural con SQL nativo:**
  `cronograma` y `actividad` aún no son entidades JPA en I-07
  (I-08/I-09 las introduce con CRUD), pero DM §3 / P-31 exige
  copiarlas. El deep copy las copia con SQL nativo dentro de
  `VersionadoService` cuando existen en el origen (FK remapeada al
  nuevo `presupuesto_id` / `rubro_id`), en la misma transacción,
  antes del recalculo final; no se introducen entidades JPA ni
  migraciones. Esto **resuelve** la contradicción previa del plan
  (la intro decía "copia estructuralmente"; STOP (D) original decía
  "manejar ausencia silenciosamente") y la deja cerrada.
- **Inert `apu.porcentaje_descuento`:** no se mapea ni se copia
  (compat seam inert de Plan 015); persiste con default 0 en BD.
- **Concurrencia:** lock pesimista de fila
  (`SELECT id FROM proyecto WHERE id = ? FOR UPDATE` en
  `PresupuestoRepository.lockProyectoRow`) que serializa la sección
  crítica «leer `max(version)` → insertar nueva versión» del deep
  copy y los toggles `es_vigente`; el índice único parcial
  `ux_presupuesto_vigente` (V001 §2.8) sigue garantizando
  «exactamente una vigente por proyecto».
- **owner-to-404 (RNF-05), D-09 (1:1 APU↔rubro) y UUIDv7 en
  frontera REST** preservados en todos los recursos; UUID
  malformado o no-v7 → 400 `validacion`.
- **REST expuesto:** `POST /proyectos/{proyectoId}/presupuestos`
  (crear versión, 201), `POST /presupuestos/{id}/vigente` (marcar
  vigente, 200), `DELETE /presupuestos/{id}` (eliminar no vigente,
  204; 409 `version-vigente-protegida` si vigente),
  `GET /presupuestos/{id}/comparar?con=<UUIDv7>` (comparación lado
  a lado con `porCapituloRaiz`, 400 si mismo id o distinto
  proyecto).
- **Verificación dirigida medida:**
  - `VersionadoResourceIT` **14/14 verde** (success, error,
    independencia origen↔copia, cascade, UUIDv7, owner scope);
    tests de deep-copy **0.785 s** y **0.850 s** sobre el fixture
    compacto representativo. El umbral STOP (A) no se activó en esas
    pruebas; no se reclama un benchmark del árbol IESS completo.
  - `./gradlew test --tests 'ec.uce.propuestas.presupuesto.*'
    -Dquarkus.http.test-port=0 --console=plain` →
    **85/85 verde**.
  - `./gradlew test --tests 'ec.uce.propuestas.apu.*' ...` →
    regresión **APU verde**.
  - `./gradlew test --tests 'ec.uce.propuestas.recalculo.*' ...` →
    regresión **`recalculo` verde**.
  - `./gradlew test --tests 'ec.uce.propuestas.motor.*' ...` →
    **45 = 42 verdes + 2 rojos aceptados (GM-19 `-$6.95`,
    GM-20 cap. 1 `-$0.84` — residuales aceptados por Plan 014,
    no se reabre el motor) + 1 skipped (GM-24 `@Disabled` por
    fixture EMELNORTE upstream)**.
  - `./gradlew spotlessCheck` → **verde**.
  - `./gradlew build -x test --console=plain` →
    **BUILD SUCCESSFUL**; 31 deprecation warnings (preexistentes;
    sin error de build).
  - `./gradlew test` (suite completa) → **425 totales =
    422 verdes + 2 rojos aceptados (GM-19 + GM-20) +
    1 skipped (GM-24) + 0 errors**.
- **No** se ejecutó commit unitario (instrucción explícita del
  usuario). `git diff --check` quedó limpio y `graphify update .`
  finalizó correctamente.
- **No** se avanzó Plan 025; **no** se modificó `motor/`,
  `recalculo/`, `apu/`, `proyecto/` fuente, ni migraciones
  (V001–V008 intactas; V009 no se pre-asigna).

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

1. **Auditar entidades cronograma/actividad — cerrado.** Confirmado
   durante la implementación: **NO existen como entidades JPA en I-07**
   (I-08 las introduce con CRUD e identidad pública UUIDv7 propia).
   P-31 (DM §3) exige copiar las filas `cronograma` y `actividad` del
   origen, así que el deep copy implementa la copia con **SQL nativo**
   dentro de `VersionadoService`
   (`EntityManager.createNativeQuery` con FK remapeada al nuevo
   `presupuesto_id` / `rubro_id`), ejecutada dentro de la misma
   transacción del deep copy, antes del recalculo final. **No** se
   introducen entidades JPA, **no** se añaden migraciones, **no** se
   introduce seam nuevo. Verificación: el test de integración de Plan
   024 cuenta `SELECT COUNT(*) FROM cronograma WHERE presupuesto_id =
   ?` y `SELECT COUNT(*) FROM actividad` antes/después del deep copy.
2. **RED — `VersionadoResourceIT.TC_P31_01_deep_copy_completo_e_independiente_bit_a_bit`**:
   - Sembrar un fixture compacto representativo con jerarquía de profundidad 3,
     rubro, APU con cuatro secciones/detalles, insumo compartido y
     cronograma/actividad. La regresión IESS completa permanece cubierta por
     los golden masters del motor; este test aísla el remapeo estructural.
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
       `porcentaje_descuento` (columna inert — Plan 015, 2026-09-01;
       el deep copy **no** la copia), `especificacion_tecnica`,
       `costo_*` (temporalmente 0).
     - INSERT 4 `apu_seccion` por APU.
     - INSERT `apu_detalle` filas por sección.
     - UPDATE `rubro.apu_id = apu_nuevo.id` (remap FK).
     - INSERT `cronograma` si existe (FK remapeada; **SQL nativo**
       dentro de `VersionadoService` — ver paso 1).
     - INSERT `actividad` si existe (FK `rubro_id` remapeada; **SQL
       nativo** — ver paso 1).
   - `RecalculoService.recalcular(Alcance.Version(presupuestoNuevoId))`
     al final → totales idénticos al origen.
4. **RED — `VersionadoServiceIT.marcarVigente_transaccional`**:
   - Sembrar v1 vigente + v2 no vigente.
   - `marcarVigente(v2.id, caller)`.
   - Verificar: v2 ahora vigente; v1 ahora no vigente; ambos cambios
     en una sola transacción.
   - Test cruzado: el índice único parcial rechaza 2 vigentes (la
     prueba directa está en Plan 021; este plan verifica el flujo).
5. **GREEN — `VersionadoService.marcarVigente`** con SQL transaccional
   (dos UPDATE consecutivos + commit), bajo lock pesimista del
   proyecto (`PresupuestoRepository.lockProyectoRow`).
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
    - `cronograma` y `actividad` se copian con SQL nativo cuando
      existen en el origen.
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
    - `./gradlew build -x test --console=plain` → BUILD SUCCESSFUL.
    - `git diff --check` → sin salida.
    - `graphify update .` → finalizado correctamente.
12. **Commit unitario — NO EJECUTADO** por instrucción explícita del
    usuario; el cierre conserva el working tree sin commit.

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

- [x] Deep copy crea árbol completo bit-a-bit idéntico al origen
  (TC-P31-01; recalculo final vía `Alcance.Version`).
- [x] Marcar vigente transaccional: 0 vigentes → 1 vigente; la
  anterior pasa a no vigente (lock pesimista de fila + 2 UPDATEs en
  la misma `@Transactional`).
- [x] Eliminar versión no vigente = 204; eliminar vigente = 409
  `version-vigente-protegida` (también 404 si ajeno/inexistente).
- [x] Comparación devuelve totales y `porCapituloRaiz` para ambas
  versiones (400 si mismo id o distinto proyecto).
- [x] El origen NO se modifica al crear/eliminar la copia (verificado
  por TC-P31-01).
- [x] Tests TC-P31-01/02/03/04 + comparación + UUIDv7 + owner-scope
  + cascade + independencia verdes (`VersionadoResourceIT` 14/14).
- [x] Regresión APU/capítulos/rubros/recalculo verde; motor con
  residuales aceptados (GM-19/GM-20) y GM-24 skipped (sin regresión
  respecto a línea base post-Plan 023).
- [x] `git diff --check` limpio y `graphify update .` finalizado.
- [x] Plan 025 (validación + cierre) puede operar sobre la versión
  vigente — **siguiente plan ejecutable**, DAG I-07 desbloqueado.

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
- **(D)** Copia de `cronograma` / `actividad` — **cerrado durante
  la implementación.** Las entidades JPA no existen en I-07 (viven
  en I-08/I-09), pero DM §3 / P-31 exige copiar las filas. La copia
  se implementa con **SQL nativo** dentro de `VersionadoService` (FK
  remapeada al nuevo `presupuesto_id` / `rubro_id`, en la misma
  transacción). No se introducen entidades ni migraciones; no es
  STOP.

---

## Siguiente plan ejecutable

[`07-validacion-y-cierre.md`](07-validacion-y-cierre.md) (Plan 025) —
validación de integridad (PU=0, cantidad=0, ítems sin actividad),
Bruno `api/bruno/10-presupuesto/`, Graphify refresh, cierre
documental. Depende de 019–024.
