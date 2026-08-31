# Plan 025 — Validación de integridad, Bruno, Graphify y cierre del módulo I-07

> **Plan 025** del módulo [`05-presupuesto`](00.md). PLANNED / READY —
> 2026-08-31. **No implementado todavía.** Cierra I-07 con la
> validación de integridad (P-32 — PU=0, cantidad=0, ítems sin
> actividad), la colección Bruno `api/bruno/10-presupuesto/`, la
> actualización de Graphify, el cierre documental y la verificación
> final del módulo.

## Resultado esperado

`GET /presupuestos/{id}/validacion` devuelve
`ValidacionPresupuestoResponse{exportable, itemsPuCero[],
itemsCantidadCero[], itemsSinActividad[]}`. El campo `exportable`
se calcula como `itemsPuCero.isEmpty() && itemsCantidadCero.isEmpty()
&& itemsSinActividad.isEmpty()` y es la base del bloqueo de
export en P-37 (I-10). El endpoint de validación **lee** la fila
`actividad` para detectar `itemsSinActividad` (D-09 + RNF-02) pero
**no** implementa el CRUD de `actividad`/`cronograma` — eso es I-08.

Adicionalmente, este plan crea la colección Bruno
`api/bruno/10-presupuesto/` con los temas P-28/29/30/31/32 bajo el
contrato UUIDv7 vigente; refresca el cache de Graphify; sincroniza
`docs/modulos/README.md`, `plans/README.md`,
`docs/00-ESTADO-ACTUAL.md` y `docs/modulos/estado-actual.md` con el
cierre del módulo; ejecuta la suite completa con conteo real desde
los XML.

## Dependencias

- [Plan 019 — Identidad y persistencia](01-identidad-y-persistencia.md).
- [Plan 020 — `recalculo` write-through](02-recalculo-write-through.md).
- [Plan 021 — Ciclo de presupuesto](03-ciclo-presupuesto.md).
- [Plan 022 — Capítulos](04-capitulos.md).
- [Plan 023 — Rubros, totales y resumen](05-rubros-totales-resumen.md).
- [Plan 024 — Versionado y comparación](06-versionado-comparacion.md).
- `Actividad` JPA disponible (al menos para lectura — verificar si
  existe como rastro del seed V004; si no, este plan sólo verifica
  `itemsSinActividad` cuando exista el cronograma).

## Estado de cierre

**PLANNED / READY — 2026-08-31.** El ejecutor actualiza esta sección
al cierre. Resultado esperado: «DONE (YYYY-MM-DD). TC-P32-01 verde;
colección Bruno `10-presupuesto/` autocontenida y verificada contra
Postgres limpio; Graphify refresh ejecutado; suite completa N/N verde
(sin cifras presupuestas — reportadas desde `build/test-results/`);
Spotless y build verde; `git diff --check` limpio; documentación
sincronizada.»

---

## Contexto / estado actual

### Lo que ya existe

1. **Toda la funcionalidad de presupuesto** (planes 019–024).
2. **`Actividad` JPA** — confirmar si existe como entidad JPA. Si
   no, este plan introduce `Actividad.java` mínimo (sólo lectura)
   con `publicId` aún NO migrado (será una migración futura de I-08 (numeración por determinar)) — la columna
   `public_id` no existe en V001; este plan NO crea la migración futura.
3. **`api/bruno/09-i02-i06/`** con 9 temas canónicos (Plan 08 I-06
  cierre). Patrón a replicar para `10-presupuesto/`.
4. **Graphify cache** —
   `graphify-out/` contiene el snapshot previo; el refresh
   reindexa el código del módulo `presupuesto` + `recalculo`.
5. **`docs/modulos/README.md` + `plans/README.md`** con los planes
   I-06 cerrados; este plan añade las entradas 019–025.

### Decisiones de diseño locked

- **P-32 sólo lee `actividad`** — el endpoint de validación NO crea
  actividades, NO las actualiza, NO las elimina. Sólo las consulta
  para detectar `itemsSinActividad`. La regla es: si un rubro no
  tiene una `actividad` con `rubro_id = rubro.id`, aparece en
  `itemsSinActividad[]`. El CRUD de actividad es I-08.
- **Bloqueo de export (`export-bloqueado`)** — vive en I-10 (P-37);
  este plan sólo calcula el flag `exportable` y lo expone. El
  recurso de export (futuro) lo lee.
- **`exportable = false` es informativo** — no impide al usuario
  trabajar ni impide operaciones internas; sólo documenta que la
  versión no es presentable. Esto evita bloquear al usuario con
  advertencias durante la edición.
- **Bruno `10-presupuesto/`** sigue el patrón `09-i02-i06/`:
  helpers de login, crear-proyecto, crear-presupuesto, crear-APU;
  temas numerados `TC-10-NN-…` con nombre descriptivo; entornos
  `dev.bru` con `proyectoId`/`presupuestoId` UUIDv7.
- **Graphify refresh** —
  `codegraph index --cwd <repo>` (NO `codegraph init` que es
  lifecycle; sólo reindex). El cache actualizado vive en
  `<repo>/.codegraph/`.
- **Cierre documental** sincroniza:
  - `docs/modulos/README.md` — añadir fila para `05-presupuesto/`
    con estado `PLANNED / READY (I-07 — 7 planes ejecutables 019–025)`.
  - `plans/README.md` — añadir entradas 019–025 con resumen del
    alcance y enlace al plan detallado.
  - `docs/modulos/estado-actual.md` y `docs/00-ESTADO-ACTUAL.md` —
    añadir fila «I-07 PLANNED — 7 planes listos, implementación
    pendiente» con referencia al índice.

---

## Alcance

### Incluye

- `ValidacionPresupuestoService` con método:
  - `obtener(Long presupuestoId, Long callerUsuarioId)` que devuelve
    `ValidacionPresupuestoResponse`.
- `PresupuestoValidacionResource` con 1 endpoint:
  - `GET /presupuestos/{presupuestoId}/validacion` (UUIDv7 validado).
- DTOs `ValidacionPresupuestoResponse`, `RubroRefResponse`.
- Si `Actividad` JPA no existe, crear `Actividad.java` mínimo
  (read-only, sin relaciones JPA a `Rubro`/`Cronograma` por ahora —
  sólo los campos de la tabla V001 §2.13).
- Colección Bruno `api/bruno/10-presupuesto/` con ~6–8 requests que
  ejerciten P-28/29/30/31/32 bajo UUIDv7.
- Ejecutar Graphify refresh.
- Sincronizar `docs/modulos/README.md`, `plans/README.md`,
  `docs/00-ESTADO-ACTUAL.md`, `docs/modulos/estado-actual.md`.
- Suite completa del proyecto y conteo real desde XML.

### No incluye

- **No** se introduce la migración futura (UUIDv7 en `cronograma`/`actividad`).
- **No** se introduce CRUD de `actividad`/`cronograma` (P-33…P-36 →
  I-08/I-09).
- **No** se introduce `export-bloqueado` (P-37 → I-10); sólo se
  calcula el flag `exportable` y se expone.
- **No** se reabre el motor ni se cambian fórmulas.
- **No** se introduce un nuevo módulo; `validacion` vive dentro del
  módulo `presupuesto`.
- **No** se introducen dependencias nuevas.
- **No** se modifican los planes I-06 cerrados (Plan 08).

---

## Contrato esperado

### Endpoint

```text
GET /presupuestos/{presupuestoId}/validacion
   → 200 ValidacionPresupuestoResponse
   · 404 no-encontrado
```

### Shape JSON

```jsonc
ValidacionPresupuestoResponse {
  "exportable":          false,
  "itemsPuCero":         [ RubroRefResponse, ... ],
  "itemsCantidadCero":   [ RubroRefResponse, ... ],
  "itemsSinActividad":   [ RubroRefResponse, ... ]
}

RubroRefResponse {
  "id":          "<UUIDv7>",
  "item":        "1.1.1",
  "codigo":      "501BM6",
  "descripcion": "..."
}
```

### Reglas de error

| Caso | Código | `type` |
|---|---|---|
| `presupuestoId` UUIDv7 malformado o no-v7 | 400 | `validacion` |
| Proyecto ajeno o inexistente | 404 | `no-encontrado` |

---

## Pasos

### Numerados y secuenciales

1. **Auditar existencia de `Actividad` JPA** abriendo
   `src/main/java/ec/uce/propuestas/` y buscando
   `actividad/entity/Actividad.java`. Si existe, este plan sólo
   añade el método de consulta. Si NO existe (probable), crear
   la entidad mínima de lectura.
2. **RED — `ValidacionPresupuestoServiceIT.detecta_incompletos`**:
   - Caso 1 — versión vacía: `itemsPuCero: []`, `itemsCantidadCero:
     []`, `itemsSinActividad: []`, `exportable: true`.
   - Caso 2 — versión con 1 rubro: PU conocido > 0, cantidad 100 →
     no aparece en ningún listado; `exportable: true` (si hay
     cronograma+actividad) o `false` (si no hay).
   - Caso 3 — versión con 1 rubro con PU = 0 (APU vacío): aparece
     en `itemsPuCero`; `exportable: false`.
   - Caso 4 — versión con 1 rubro con cantidad = 0: aparece en
     `itemsCantidadCero`; `exportable: false`.
   - Caso 5 — versión con 1 rubro sin actividad asociada: aparece en
     `itemsSinActividad`; `exportable: false`.
3. **GREEN — `ValidacionPresupuestoService`**:
   - `itemsPuCero`: `SELECT r FROM Rubro r WHERE r.precioUnitario = 0`.
   - `itemsCantidadCero`: `SELECT r WHERE r.cantidad = 0`.
   - `itemsSinActividad`: si existe `Actividad` JPA, `SELECT r
     WHERE NOT EXISTS (SELECT 1 FROM Actividad a WHERE a.rubroId =
     r.id)`. Si no existe, devolver `[]` (sin itemsSinActividad).
   - `exportable = itemsPuCero.isEmpty() && itemsCantidadCero.isEmpty()
     && itemsSinActividad.isEmpty()`.
4. **GREEN — `PresupuestoValidacionResource`** con `@GET
   /presupuestos/{presupuestoId}/validacion` (UUIDv7 validado).
5. **GREEN — DTOs + mappers**.
6. **Bruno — crear `api/bruno/10-presupuesto/`** siguiendo el patrón
   `09-i02-i06/`:
   - `environments/dev.bru` con `proyectoId` y `presupuestoId`
     UUIDv7.
   - Helpers `TC-10-00a-…` (login titular), `TC-10-00b-…` (crear
     proyecto), `TC-10-00c-…` (crear APU vacío), `TC-10-00d-…`
     (crear rubro con cantidad > 0 y APU válido).
   - Tema 1 — P-28 capítulos: crear raíz + subcapítulo + mover +
     eliminar.
   - Tema 2 — P-29 rubros: crear ítem, intentar duplicar APU
     vinculado (409), editar cantidad.
   - Tema 3 — P-30 resumen: `GET …/resumen`.
   - Tema 4 — P-31 versionado: crear versión 2, marcar vigente,
     eliminar no vigente.
   - Tema 5 — P-32 validación: `GET …/validacion` con todos los
     listados vacíos / poblados.
   - Tema 6 — UUIDv7 inválido (400) + recurso ajeno (404).
7. **Graphify refresh**:
   - `codegraph index --cwd
     /home/kaandradec/Documents/workspace/uce/proyecto-grado/thesis-back-quarkus`
     (siguiendo el patrón Plan 08 I-06). El cache actualizado vive
     en `.codegraph/`.
8. **Sincronización documental**:
   - `docs/modulos/README.md` — añadir fila para `05-presupuesto/`
     con descripción y estado `PLANNED / READY (I-07)`.
   - `plans/README.md` — añadir entradas 019–025 con resumen y
     enlace. Reemplazar la frase «Plans for I-07 through I-12 (…,
     presupuesto, cronograma, export, admin, validación final) are
     not yet written — they will be authored in later planning
     sessions once each preceding iteration's plans are DONE and
     CI-green.» por una que reconozca los planes 019–025 como
     escritos y referencie el nuevo índice.
   - `docs/00-ESTADO-ACTUAL.md` — añadir fila I-07 PLANNED en la
     tabla §2 (Posición en el cronograma), apuntando al índice
     `docs/modulos/05-presupuesto/00.md`. No cambiar estados de
     features existentes.
   - `docs/modulos/estado-actual.md` — añadir línea breve al
     resumen ejecutivo: «I-07 — 7 planes listos, implementación
     pendiente (`docs/modulos/05-presupuesto/`)».
   - **No** modificar los planes I-06 cerrados ni el `00.md` del
     `planes-para-estar-al-dia/` salvo el banner que ya planeamos
     en §3 de la tarea original (ver §Sincronización adicional
     abajo).
9. **Sincronización adicional (autorizada por el orquestador)**:
   - `docs/modulos/planes-para-estar-al-dia/00.md` — añadir un
     banner al inicio: «Conjunto histórico cerrado de planes I-06.
     Los planes I-07 viven en [`../05-presupuesto/00.md`](../05-presupuesto/00.md).
     No se reabre este índice; sus enlaces históricos se conservan.»
   - **No** reescribir los planes individuales del set I-06.
10. **Verificación dirigida final**:
    - `./gradlew test --tests 'ec.uce.propuestas.presupuesto.*'
      -Dquarkus.http.test-port=0 --console=plain` → verde.
    - `./gradlew test --tests 'ec.uce.propuestas.recalculo.*'
      -Dquarkus.http.test-port=0 --console=plain` → verde.
    - `./gradlew spotlessCheck` — verde. Plan 08 cerró el Spotless
      global; no se asume ni se permite deuda preexistente. Este plan
      sólo añade archivos del módulo `presupuesto` y `recalculo`, que
      deben estar formateados.
    - `./gradlew build -x test` → BUILD SUCCESSFUL.
    - `git diff --check` → sin salida.
11. **Verificación completa del proyecto**:
    - `./gradlew test --console=plain` — ejecutar la suite
      completa del proyecto.
    - **Conteo real desde XML** (no presuponer cifras):
      ```bash
      grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
        build/test-results/test/TEST-*.xml \
        | awk -F'"' '{
            t+=$2; f+=$4; e+=$6; s+=$8
          } END { printf "tests=%d failures=%d errors=%d skipped=%d\n", t, f, e, s }'
      ```
    - Reportar el conteo en el cierre. Los únicos rojos aceptados
      son GM-19 y GM-20 (residual aceptado 2026-08-28). GM-24 sigue
      `@Disabled`. Cualquier otro rojo **STOP** — no cerrar I-07
      con regresiones.
12. **Commit unitario final** con mensaje
    `feat(presupuesto): Plan 025 validación P-32, Bruno, Graphify y
    cierre I-07`.

---

## Pruebas y comprobaciones

```bash
# Verificación dirigida final
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*' \
    -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.recalculo.*' \
    -Dquarkus.http.test-port=0 --console=plain

# Spotless (Plan 08 cerró el global; sin exclusión por deuda)
./gradlew spotlessCheck

# Build sin tests
./gradlew build -x test

# Suite completa (cierre)
./gradlew test --console=plain

# Conteo real desde XML
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
    build/test-results/test/TEST-*.xml \
  | awk -F'"' '{ t+=$2; f+=$4; e+=$6; s+=$8 }
    END { printf "tests=%d failures=%d errors=%d skipped=%d\n", t, f, e, s }'

# Documentación y Graphify
git diff --check
git status --short
codegraph index --cwd $(pwd)
```

**Resultado esperado** (cifras al cierre — reportadas, no
presupuestas):

- TC-P32-01 verde.
- Suite completa: N tests, sólo rojos GM-19/GM-20 aceptados; GM-24
  `@Disabled`; sin otras regresiones.
- `./gradlew spotlessCheck` verde para los archivos del módulo
  `presupuesto` y `recalculo` (el cierre Plan 08 ya saldó el Spotless
  global; no se asume deuda preexistente ni se excluye la tarea).
- Graphify refresh ejecutado; el cache actualizado vive en
  `.codegraph/`.
- Documentación sincronizada (4 archivos actualizados).
- `git diff --check` limpio.

---

## Criterios de terminado

- [ ] `GET /presupuestos/{id}/validacion` funciona con UUIDv7,
  owner-scope, errores canónicos.
- [ ] `itemsPuCero`, `itemsCantidadCero`, `itemsSinActividad`
  calculados correctamente.
- [ ] `exportable` calculado correctamente.
- [ ] Test TC-P32-01 verde.
- [ ] Colección Bruno `api/bruno/10-presupuesto/` autocontenida
  con UUIDv7 y temas P-28/29/30/31/32.
- [ ] Graphify refresh ejecutado (cache en `.codegraph/`).
- [ ] `docs/modulos/README.md`, `plans/README.md`,
  `docs/00-ESTADO-ACTUAL.md`, `docs/modulos/estado-actual.md`
  actualizados con I-07 PLANNED.
- [ ] `docs/modulos/planes-para-estar-al-dia/00.md` con banner
  «I-06 cerrado» + enlace al nuevo índice.
- [ ] Suite completa verde con conteo real (sólo GM-19/GM-20
  rojos aceptados, GM-24 `@Disabled`).
- [ ] Spotless verde para los archivos del módulo.
- [ ] Build sin tests verde.
- [ ] `git diff --check` limpio.
- [ ] Commit unitario final + merge.

---

## STOP conditions (específicas de este plan)

- **(A)** Si la suite completa tiene rojos distintos de GM-19/GM-20
  aceptados y GM-24 `@Disabled`, **STOP** — no cerrar I-07 con
  regresiones; identificar y arreglar el módulo responsable.
- **(B)** Si la validación de integridad requiere modificar el
  motor para detectar PU=0 cuando el APU es válido pero vacío
  (sólo HM), **STOP** — el motor ya detecta PU=0 como `costo_total
  = 0`; validar que la lectura es correcta antes de cualquier
  cambio.
- **(C)** Si Graphify refresh falla por caché corrupto, **STOP** —
  reindexar (no init). Si init falla por permisos, escalar.
- **(D)** Si el banner de `planes-para-estar-al-dia/00.md` requiere
  reescribir los planes históricos para mantener coherencia,
  **STOP** — el banner es aditivo; no se reabre contenido.

---

## Cierre del módulo I-07

Al cierre de Plan 025, el módulo `presupuesto` queda PLANNED /
READY — la implementación efectiva la ejecutará un futuro ejecutor
plan a plan. El estado «PLANNED / READY» significa:

1. **Diseño cerrado** — 7 planes ejecutables (019–025) con TDD, contratos y STOPs
   definidos.
2. **Canonical sources alineados** — `06-database-schema.md`,
   `07-api-contract.md` y `08-codebase-design.md` actualizados
   para que el ejecutor futuro no se contradiga con el diseño
   canónico.
3. **Documentación sincronizada** — los 4 archivos de status lo
   reflejan como PLANNED; el conjunto histórico I-06 sigue
   cerrado.
4. **Bruno template presente** — `api/bruno/10-presupuesto/`
   existe con los requests esperados pero sin verificación contra
   el backend (la verificación ocurrirá cuando se implemente).
5. **Sin código ejecutable** — los archivos del módulo
   `presupuesto`/`recalculo` siguen vacíos; sólo existen los
   entities/repositories del rastro V001/V004.

El ejecutor futuro comienza por Plan 019, aplica los pasos, ejecuta
las pruebas específicas, hace commit unitario, repite con 020…025 en
orden, y al cierre ejecuta las verificaciones dirigidas + la suite
completa + Graphify.

---

## Siguiente paso del proyecto (fuera de I-07)

Cuando I-07 cierre «IMPLEMENTED» (no sólo «PLANNED»), el siguiente
paso es planificar I-08 (cronograma base — `cronograma` y `actividad`
con migración futura de I-08 (numeración por determinar). Ese plan se redactará cuando el módulo
`presupuesto` esté en verde.
