# Plan 025 — Validación de integridad, Bruno, Graphify y cierre del módulo I-07

> **Plan 025** del módulo [`05-presupuesto`](00.md).
> **DONE (2026-09-01)** — implementación efectiva ejecutada y verificada
> con conteo real desde XML (código fuente, DTOs, tests
> `PresupuestoValidacionResourceIT` con 13 tests escritos primero,
> Bruno `api/bruno/10-presupuesto/` autocontenido, `dev.bru` con vars
> runtime, `graphify query` preflight).
> `graphify update .` final: **DONE — 2.972 nodos / 9.064 aristas /
> 141 comunidades**.
> Sin commit unitario ni merge (instrucción explícita). El DAG I-07
> queda cerrado para I-07; la **siguiente tarea de planificación** es
> Plan 026 (I-08 — cronograma CRUD), que aún **no** existe como
> archivo ejecutable y debe autorarse en su propia sesión.

## Resultado esperado

`GET /presupuestos/{id}/validacion` (UUIDv7 validado en frontera) responde
200 con `ValidacionPresupuestoResponse{exportable, itemsPuCero[],
itemsCantidadCero[], itemsSinActividad[]}` y errores canónicos 400/404.
El campo `exportable` se calcula como `itemsPuCero.isEmpty()
&& itemsCantidadCero.isEmpty() && itemsSinActividad.isEmpty()` y es la
base del bloqueo de export en P-37 (I-10). El endpoint **lee** las filas
`actividad`/`cronograma` (cross-presupuesto, vía SQL nativo narrow)
para detectar `itemsSinActividad` (RNF-02) pero **no** implementa su
CRUD — eso es I-08/I-09.

Adicionalmente, este plan:

- crea la colección Bruno `api/bruno/10-presupuesto/` (23 requests: 5
  helpers `TC-10-00a..00e` + 18 casos temáticos `TC-10-01..15`
  cubriendo P-28/P-29/P-30/P-31/P-32 + UUIDv7 + owner-scope),
  autocontenida contra Postgres limpio Flyway-seeded con V001…V004;
- actualiza `api/bruno/environments/dev.bru` con variables runtime
  `p25*` (sin crear `10-presupuesto/environments/`);
- refresca el cache de Graphify (`graphify query` preflight +
  `graphify update .` final, no `init`);
- sincroniza la documentación canónica con el estado DONE.

## Estado de cierre

**DONE (2026-09-01).** Verificación dirigida y medición completa de
suite ejecutadas por el escritor de Plan 025 con conteo real desde
XML. El único ítem que permanece pendiente es la ejecución final de
`graphify update .` por el orquestador (ver §10).

- **Implementación efectiva ejecutada y verificada** en este pase:
  - `ValidacionPresupuestoService` (`presupuesto/service/`):
    método `validar(UUID presupuestoPublicId, Long callerUsuarioId)`.
  - `PresupuestoValidacionResource` (`presupuesto/resource/`):
    `GET /presupuestos/{presupuestoId}/validacion`.
  - DTOs `ValidacionPresupuestoResponse` y `RubroRefResponse` (4
    campos exactos: `id`, `item`, `codigo`, `descripcion`).
  - `PresupuestoRepository.findRubrosCubiertosPorCronograma(Long)`:
    SQL nativo narrow mirroring Plan 024; sin entidad JPA
    `Actividad`/`Cronograma`, sin migración nueva.
  - `PresupuestoValidacionResourceIT`: **13 tests escritos primero**
    (TC-P32-01..13) — defectos ortogonales, lista vacía, presupuesto
    válido, sin cronograma, defectos múltiples, orden determinista,
    forma `RubroRefResponse` (4 campos, sin fugas), UUID malformado,
    UUIDv4, UUIDv7 inexistente, caller ajeno, aislamiento
    cross-presupuesto y read-only.
  - Bruno `api/bruno/10-presupuesto/` (23 requests), con
    `folder.bru` y `dev.bru` extendidos.
  - Documentación canónica (este archivo, `00.md`, README,
    `00-ESTADO-ACTUAL.md`, `estado-actual.md`, `plans/README.md`,
    `CLAUDE.md`, `api/bruno/README.md`) sincronizada.

- **Evidencia medida (2026-09-01):**
  - `PresupuestoValidacionResourceIT` **13/13 verde** (TC-P32-01..13).
  - `./gradlew test --tests 'ec.uce.propuestas.presupuesto.*'` →
    **98/98 verde**.
  - `./gradlew test --tests 'ec.uce.propuestas.apu.*'` → **52/52
    verde** (sin regresión).
  - `./gradlew test --tests 'ec.uce.propuestas.recalculo.*'` →
    **4/4 verde** (sin regresión).
  - `./gradlew test --tests 'ec.uce.propuestas.motor.*'` → **45
    totales = 42 pass + 2 aceptados (GM-19 `-$6.95`, GM-20 cap. 1
    `-$0.84` — residuales aceptados por Plan 014, no se reabre el
    motor) + 1 skipped (GM-24 `@Disabled` por fixture upstream
    EMELNORTE) + 0 errors**.
  - `./gradlew test` (suite completa) → **438 totales = 435 pass +
    2 aceptados (GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84`) + 1 skipped
    (GM-24 `@Disabled`) + 0 errors** (conteo real desde los XML de
    `build/test-results/test/`).
  - `./gradlew spotlessCheck` → **PASS**.
  - `./gradlew build -x test` → **PASS** (BUILD SUCCESSFUL).
  - `git diff --check` → **limpio**.

- **Evidencia Bruno (dinámica, contra PostgreSQL 18 limpio + fast-jar,
  2026-09-01):**
  - 23/23 requests ejecutadas, 83/83 tests asserts, 0 failures,
    0 errors, 0 skips.
  - Duración CLI 4.554 s, wall 6.070 s, Bruno CLI 4.1.0.
  - El archivo raíz inválido `api/bruno/collection.bru` (no-op,
    rechazado por el parser 4.1) fue removido por el escritor antes
    de la corrida dinámica; el environment compartido `dev.bru` se
    mantiene libre de comentarios para que el parser de environments
    de Bruno CLI 4.1 no los rechace; los tests `assert` de los
    requests usan `bru.getVar(...)` para capturar variables runtime
    mientras que la interpolación de cuerpo conserva `{{...}}`.

- **Decisiones locked por re-evaluación (NEEDS ADJUSTMENT cerrado antes
  de tocar código):**

  1. **Sin entidad JPA `Actividad`/`Cronograma`** — no se crea
     `Actividad.java`/`Cronograma.java` en este plan. El deep copy P-31
     ya copió estas filas con SQL nativo dentro de `VersionadoService`
     (Plan 024, sin seam). El endpoint de validación lee con SQL nativo
     narrow (`PresupuestoRepository.findRubrosCubiertosPorCronograma`),
     preservando el aislamiento cross-presupuesto (TC-P32-12). El CRUD
     de `cronograma`/`actividad` sigue diferido a I-08/I-09.
  2. **Sin `itemsSinActividad` vacío cuando no hay cronograma** — la
     regla locked es: si el presupuesto NO tiene cronograma, el conjunto
     de cubiertos es vacío y TODOS los rubros quedan en
     `itemsSinActividad` (TC-P32-04). El plan original proponía devolver
     `[]` en ese caso; la re-evaluación detectó que esa elección
     contradice la regla de negocio y deja `exportable=true` por
     defecto. Se corrige.
  3. **No se pre-asigna V009 ni se reabre V001–V008.**
  4. **Estado del módulo `presupuesto`:** Plans 019–024 están
     **DONE** y los planes 015 + 020 ya están referenciados como tales
     en las entradas existentes del índice (`00.md` §8,
     `plans/README.md`). Plan 025 los **actualiza** en vez de añadir
     filas duplicadas.

- **Sin cambios de motor / recalculo / migración.** No se modificó
  código de `motor/`, `recalculo/`, `apu/`, `proyecto/` ni migraciones
  V001–V008. No se ejecutó commit unitario ni merge (instrucción
  explícita del orquestador).

- **STOP conditions** siguen activas para re-apertura futura (ver
  más abajo).

---

## Contexto / estado actual

### Lo que ya existe

1. **Toda la funcionalidad de presupuesto** (planes 015 + 019–024,
   **DONE 2026-09-01**). El árbol completo
   `Presupuesto → Capitulo → Rubro` con UUIDv7 públicos y FKs BIGINT
   internas está activo.
2. **`cronograma` y `actividad` con datos sembrados por V001 §2.13.**
   Ambas tablas existen y el deep copy de `VersionadoService` (Plan 024)
   las maneja con SQL nativo narrow. No son entidades JPA.
3. **`api/bruno/09-i02-i06/`** con 9 temas canónicos (Plan 08 I-06
   cierre). Patrón a replicar para `10-presupuesto/`.
4. **Graphify cache** — `.codegraph/` reindexado por Plan 024 con
   `graphify update .` (sin SQL). El refresh final de Plan 025 reindexa
   el código del módulo `presupuesto` (4 archivos nuevos +
   1 repositorio extendido) + los archivos `*.bru` de
   `10-presupuesto/`. Sin `codegraph init` (no es lifecycle);
   sin `codegraph uninit`/`install`/`uninstall`/`upgrade`.
5. **Documentación canónica** — `00.md` §8 (tabla de status),
   `plans/README.md` (estado por plan), `docs/modulos/README.md`,
   `docs/00-ESTADO-ACTUAL.md`, `docs/modulos/estado-actual.md` y
   `CLAUDE.md` ya mencionan I-07 / Plan 025. Este plan los
   **actualiza** (no duplica filas).

### Decisiones locked (re-evaluación cerrada antes de implementar)

- **P-32 sólo lee `actividad`/`cronograma`** — el endpoint de
  validación **no** crea actividades, **no** las actualiza, **no** las
  elimina. La regla es:
  - `itemsPuCero`: rubros con `rubro.precio_unitario = 0` (PU desde
    el APU vía frontera APU→Rubro `workbook-consistent`).
  - `itemsCantidadCero`: rubros con `rubro.cantidad = 0` (permitido a
    nivel BD por V007; la API REST P-29 sólo permite crear con
    `cantidad > 0`).
  - `itemsSinActividad`: rubros sin actividad en el cronograma del
    MISMO presupuesto (SQL nativo, FK remapeada, aislamiento
    cross-presupuesto, TC-P32-12).
  - `exportable`: `itemsPuCero.isEmpty() && itemsCantidadCero.isEmpty()
    && itemsSinActividad.isEmpty()`.
- **Cobertura por actividad** vía SQL nativo narrow, **no** vía entidad
  JPA. Si el presupuesto no tiene cronograma, el conjunto de cubiertos
  es vacío y todos los rubros quedan como defectuosos (TC-P32-04).
- **Bloqueo de export (`export-bloqueado`)** — vive en I-10 (P-37);
  este plan sólo calcula `exportable` y lo expone.
- **`exportable = false` es informativo** — no impide al usuario
  trabajar ni impide operaciones internas; sólo documenta que la
  versión no es presentable.
- **Bruno `10-presupuesto/`** sigue el patrón `09-i02-i06/`:
  - helpers `TC-10-00a…00e` (login titular, login ajeno, crear proyecto
    propio BORRADOR con auto-create v1 vigente, capturar presupuesto
    v1, crear APU vacío);
  - casos temáticos `TC-10-01..15` (P-28/P-29/P-30/P-31/P-32 + 3 negativos
    UUIDv7/owner-scope);
  - casos negativos `TC-10-13…15` (UUIDv4 → 400, UUIDv7 inexistente →
    404, caller ajeno → 404).
- **Variables runtime en `dev.bru`** — `p25ProyectoId`,
  `p25PresupuestoId`, `p25CapituloRaizId`, `p25SubcapituloId`,
  `p25ApuVacioId`, `p25RubroId`, `p25V2Id`, `p25InexistenteV7`. Cada
  nombre/código es determinista; cada corrida usa una base desechable limpia.
- **Graphify refresh** —
  - **preflight:** `graphify query` para inspeccionar el árbol del
    módulo `presupuesto/` antes de editar (verifica que las nuevas
    clases `ValidacionPresupuestoService`/`Resource` se modelan
    correctamente);
  - **final:** `graphify update .` para reindexar el código +
    Bruno `*.bru` (no SQL, `tree_sitter_sql` no disponible —
    restricción documentada por Plan 019) — **DONE — 2.972 nodos / 9.064 aristas / 141 comunidades**
    (§10).
  - **NO** se ejecuta `codegraph init` (lifecycle); **NO** se ejecuta
    `codegraph uninit`/`install`/`uninstall`/`upgrade`.
- **Cierre documental** sincroniza:
  - `docs/modulos/05-presupuesto/00.md` §0 y §10 — actualizar
    referencias a Plan 025 DONE;
  - `docs/modulos/README.md` — actualizar el estado del módulo
    `05-presupuesto` con la fila DONE 2026-09-01;
  - `docs/00-ESTADO-ACTUAL.md` §2 y §3 — actualizar nota I-07 DONE
    2026-09-01;
  - `docs/modulos/estado-actual.md` — actualizar el bloque I-07
    (la sección 4.4 de capacidades ya menciona cierre I-07);
  - `plans/README.md` — actualizar entrada 025 con detalle DONE;
  - `CLAUDE.md` — actualizar nota Plan 025 DONE;
  - `docs/modulos/05-presupuesto/00-analisis-reevaluacion.md` — ya
    creado (NEEDS ADJUSTMENT cerrada);
  - `docs/modulos/planes-para-estar-al-dia/00.md` — **no modificar**
    (el banner histórico ya es correcto; STOP (D) del plan 025).

---

## Alcance

### Incluye

- `ValidacionPresupuestoService.validar(UUID, Long) → ValidacionPresupuestoResponse`
  (`presupuesto/service/`).
- `PresupuestoValidacionResource` con `GET /presupuestos/{presupuestoId}/validacion`
  (UUIDv7 validado en frontera con `UuidV7.parse`, roles
  `USUARIO`/`SUPER_ADMIN`).
- DTOs `ValidacionPresupuestoResponse` y `RubroRefResponse` (4 campos
  exactos).
- `PresupuestoRepository.findRubrosCubiertosPorCronograma(Long)` —
  SQL nativo narrow, sin entidad JPA.
- `PresupuestoValidacionResourceIT` con 13 tests escritos primero
  (TC-P32-01..13). Cobertura:
  - TC-P32-01 canónico: tres defectos ortogonales.
  - TC-P32-02 presupuesto recién creado sin rubros.
  - TC-P32-03 versión válida completa (PU>0, cantidad>0, actividad).
  - TC-P32-04 sin cronograma → todos los rubros en `itemsSinActividad`.
  - TC-P32-05 un rubro con múltiples defectos.
  - TC-P32-06 orden determinista por `item`.
  - TC-P32-07 forma `RubroRefResponse` (4 campos, sin fugas BIGINT ni
    `RubroResponse`).
  - TC-P32-08 UUID malformado → 400.
  - TC-P32-09 UUIDv4 → 400.
  - TC-P32-10 UUIDv7 inexistente → 404.
  - TC-P32-11 caller ajeno → 404 (RNF-05).
  - TC-P32-12 aislamiento cross-presupuesto.
  - TC-P32-13 read-only (no muta BD tras N invocaciones).
- Colección Bruno `api/bruno/10-presupuesto/` (23 requests, ver §Bruno
  más abajo).
- `api/bruno/environments/dev.bru` extendido con vars runtime
  `p25*`.
- `api/bruno/README.md` actualizado con la tabla de colecciones y la
  guía de ejecución de `10-presupuesto/`.
- `docs/modulos/05-presupuesto/00-analisis-reevaluacion.md` creado
  (NEEDS ADJUSTMENT cerrada).
- Sincronización documental (ver §Cierre documental arriba).
- Preflight `graphify query` + final `graphify update .`.

### No incluye

- **No** se crea `Actividad.java`/`Cronograma.java` (entidades JPA
  siguen diferidas a I-08/I-09).
- **No** se introduce CRUD de `actividad`/`cronograma` (P-33…P-36 →
  I-08/I-09).
- **No** se introduce `export-bloqueado` (P-37 → I-10); sólo se
  calcula el flag `exportable` y se expone.
- **No** se reabre el motor ni se cambian fórmulas.
- **No** se introduce un nuevo módulo; `validacion` vive dentro del
  módulo `presupuesto`.
- **No** se introducen dependencias nuevas.
- **No** se modifican los planes I-06 cerrados (Plan 08 I-06) ni el
  banner de `planes-para-estar-al-dia/00.md` (STOP (D)).
- **No** se ejecuta `codegraph init`/`uninit`/`install`/`uninstall`/
  `upgrade` (sólo `update .`).
- **No** se ejecuta ningún commit unitario ni merge (instrucción
  explícita del orquestador para esta sesión).

---

## Contrato esperado

### Endpoint

```text
GET /presupuestos/{presupuestoId}/validacion
   → 200 ValidacionPresupuestoResponse
   · 400 validacion (UUID malformado o no-v7)
   · 404 no-encontrado (presupuesto ajeno o inexistente)
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
  "item":        "1.1",
  "codigo":      "501BM6",
  "descripcion": "..."
}
```

### Reglas de error

| Caso | Código | `type` |
|---|---|---|
| `presupuestoId` UUIDv7 malformado o no-v7 | 400 | `validacion` |
| Presupuesto ajeno o inexistente | 404 | `no-encontrado` |

---

## Pasos

### Numerados y secuenciales

1. **Re-evaluación (NEEDS ADJUSTMENT cerrada antes de tocar código)**
   — abrir `00-analisis-reevaluacion.md`, citar el código actual
   (incluyendo `Actividad`/`Cronograma` no-JPA, deep copy SQL nativo
   de Plan 024) y locked las decisiones locked arriba. Documentar la
   corrección de `itemsSinActividad` cuando no hay cronograma
   (presupuesto con rubros pero sin cronograma ⇒ TODOS los rubros
   defectuosos, no `[]`).

2. **RED — `PresupuestoValidacionResourceIT`** — escribir primero los
   13 tests TC-P32-01..13 contra el endpoint que aún NO existe.
   Capturar el fallo observado (HTTP 404 por defecto en Quarkus para
   path no registrado; o si se observa que el endpoint ya existe en
   alguna forma previa, capturar el fallo semántico de los defectos
   ortogonales, de la forma `RubroRefResponse` o del orden
   determinista).

3. **GREEN — DTOs + `ValidacionPresupuestoService`** — implementar
   `ValidacionPresupuestoResponse`, `RubroRefResponse` y
   `ValidacionPresupuestoService.validar`:
   - `itemsPuCero`: filtro `r.precioUnitario.compareTo(ZERO) == 0`,
     ordenado por `(item asc, publicId.toString())`.
   - `itemsCantidadCero`: filtro `r.cantidad.compareTo(ZERO) == 0`,
     mismo orden.
   - `itemsSinActividad`: rubros cuyo `id` interno NO esté en
     `PresupuestoRepository.findRubrosCubiertosPorCronograma(p.id)`;
     si el presupuesto no tiene cronograma, el conjunto es vacío y
     todos los rubros quedan en `itemsSinActividad` (TC-P32-04).
   - `exportable`: `itemsPuCero.isEmpty() && itemsCantidadCero.isEmpty()
     && itemsSinActividad.isEmpty()`.

4. **GREEN — `PresupuestoRepository.findRubrosCubiertosPorCronograma`**
   — SQL nativo narrow con JOIN a `cronograma` filtrando por
   `presupuesto_id`:
   ```sql
   select distinct a.rubro_id
     from actividad a
     join cronograma c on c.id = a.cronograma_id
    where c.presupuesto_id = ?1
   ```
   Devuelve `Set<Long>` de IDs internos cubiertos.

5. **GREEN — `PresupuestoValidacionResource`** —
   `@Path("/presupuestos")` + `@Path("/{presupuestoId}/validacion")`,
   UUIDv7 parseado en frontera, owner-scope vía `SecurityIdentity +
   UsuarioRepository` (mismo patrón que
   `PresupuestoResource`/`CapituloResource`).
   Roles `USUARIO`/`SUPER_ADMIN`. Sin `@Transactional` (read-only).

6. **TRIANGULATE — verificar aislamiento cross-presupuesto
   (TC-P32-12)** y read-only tras N invocaciones (TC-P32-13). Estos
   tests adicionales cubren dos riesgos materiales:
   - leakage de actividad de la versión hermana del mismo proyecto;
   - mutación accidental del árbol al evaluar la validación.

7. **Bruno — crear `api/bruno/10-presupuesto/`** siguiendo el patrón
   `09-i02-i06/`:
   - `folder.bru` con `meta { name: "10-presupuesto", seq: 10 }`,
     `auth { mode: inherit }`.
   - 5 helpers: `TC-10-00a` (login titular), `TC-10-00b` (login
     ajeno), `TC-10-00c` (crear proyecto propio BORRADOR),
     `TC-10-00d` (capturar presupuesto v1 vigente auto-creado),
     `TC-10-00e` (crear APU vacío sin plantilla).
   - 18 casos temáticos cubriendo P-28 (con 4 sub-casos `TC-10-02a..02d`), P-29, P-30, P-31 y P-32, más
     3 casos negativos UUIDv7/owner-scope. Conteo total = 19
     requests; secuencia determinista (los `seq` Bruno son 0..18).
   - Documenta honestamente: TC-10-12 sólo cubre PU=0 y sin
     actividad por API; `itemsCantidadCero` se cubre sólo por el
     IT (REST P-29 prohíbe cantidad=0 en creación).

8. **dev.bru — extender** `api/bruno/environments/dev.bru` con vars
   `p25*` (sin comentarios — el parser de environments de Bruno CLI 4.1
   los rechaza). NO crear `10-presupuesto/environments/` (Bruno carga
   el environment compartido).

9. **Bruno README** — `api/bruno/README.md` actualizado con tabla de
   todas las colecciones (incluyendo `10-presupuesto/`) y guía de
   ejecución secuencial.

10. **Graphify preflight + final**:
    - **preflight:** `graphify query "presupuesto"` antes de tocar
      código para confirmar que el árbol del módulo se modela
      correctamente y que el cache tiene las entidades
      `Presupuesto`/`Capitulo`/`Rubro` esperadas.
    - **final:** `graphify update .` tras toda la implementación —
      **DONE — 2.972 nodos / 9.064 aristas / 141 comunidades**.
    - **NO** `codegraph init` (lifecycle); **NO** `uninit`/`install`/
      `uninstall`/`upgrade`.

11. **Sincronización documental** (ver §Decisiones locked arriba):
    - `docs/modulos/05-presupuesto/07-validacion-y-cierre.md` (este
      archivo).
    - `docs/modulos/05-presupuesto/00.md` (estado en §0, §8 y §10).
    - `docs/modulos/README.md` (fila `05-presupuesto`).
    - `docs/00-ESTADO-ACTUAL.md` (fila I-07 §2; fila 025 §3).
    - `docs/modulos/estado-actual.md` (bloque I-07).
    - `plans/README.md` (entrada 025).
    - `CLAUDE.md` (nota Plan 025).
    - `docs/modulos/05-presupuesto/00-analisis-reevaluacion.md`
      (NEEDS ADJUSTMENT cerrada).
    - **NO** `planes-para-estar-al-dia/00.md` (STOP (D)).

12. **Verificación final — ejecutada por el escritor** (medición con
    conteo real desde XML, no se inventan cifras):
    - `./gradlew spotlessCheck` → PASS.
    - `./gradlew build -x test` → PASS (BUILD SUCCESSFUL).
    - `./gradlew test --console=plain` → **438 totales = 435 pass +
      2 aceptados (GM-19 + GM-20) + 1 skipped (GM-24) + 0 errors**.
    - `git diff --check` → limpio.
    - `graphify update .` → **DONE — 2.972 nodos / 9.064 aristas / 141 comunidades**.

13. **Bruno dinámico** — corrida contra PostgreSQL 18 limpio +
    fast-jar: 23/23 requests, 83/83 tests, 0 failures/errors/skips,
    4.554 s CLI / 6.070 s wall, Bruno CLI 4.1.0. El archivo raíz
    inválido `api/bruno/collection.bru` fue removido antes de la
    corrida (no-op rechazado por el parser 4.1); el environment
    compartido `dev.bru` se mantiene libre de comentarios; los
    tests `assert` usan `bru.getVar` mientras que la interpolación
    de cuerpo conserva `{{...}}`.

14. **NO** commit unitario ni merge (instrucción explícita).

---

## Pruebas y comprobaciones

```bash
# Verificación dirigida — ejecutada por el escritor (2026-09-01)
./gradlew spotlessCheck                              # PASS
./gradlew build -x test                              # PASS (BUILD SUCCESSFUL)
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*'  # 98/98 verde
./gradlew test --tests 'ec.uce.propuestas.apu.*'           # 52/52 verde
./gradlew test --tests 'ec.uce.propuestas.recalculo.*'    # 4/4 verde
./gradlew test --tests 'ec.uce.propuestas.motor.*'        # 45 = 42 pass + 2 aceptados + 1 skipped + 0 errors
./gradlew test --console=plain                       # 438 = 435 pass + 2 aceptados + 1 skipped + 0 errors

# Conteo real desde XML — ejecutado por el escritor (2026-09-01)
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
  build/test-results/test/TEST-*.xml \
| awk -F'"' '{ t+=$2; f+=$4; e+=$6; s+=$8 }
    END { printf "tests=%d failures=%d errors=%d skipped=%d\n", t, f, e, s }'
# → tests=438 failures=2 errors=0 skipped=1

# Git diff
git diff --check                                     # limpio

# Graphify — preflight y actualización final ejecutados
graphify query "presupuesto"
graphify update .                 # DONE: 2.972 nodos / 9.064 aristas / 141 comunidades
```

**Resultado esperado — verificado por el escritor (2026-09-01):**

- TC-P32-01..13 verde — **13/13** (medido).
- `PresupuestoValidacionResourceIT` **13/13 verde**.
- `./gradlew test --tests 'ec.uce.propuestas.presupuesto.*'`
  → **98/98 verde**.
- `./gradlew test --tests 'ec.uce.propuestas.apu.*'`
  → **52/52 verde** (sin regresión).
- `./gradlew test --tests 'ec.uce.propuestas.recalculo.*'`
  → **4/4 verde** (sin regresión).
- `./gradlew test --tests 'ec.uce.propuestas.motor.*'`
  → **45 totales = 42 pass + 2 aceptados (GM-19, GM-20) +
  1 skipped (GM-24 `@Disabled`) + 0 errors**.
- `./gradlew test` (suite completa)
  → **438 totales = 435 pass + 2 aceptados (GM-19 `-$6.95`,
  GM-20 cap. 1 `-$0.84`) + 1 skipped (GM-24 `@Disabled`) + 0
  errors**.
- `./gradlew spotlessCheck` → **PASS**.
- `./gradlew build -x test` → **PASS** (BUILD SUCCESSFUL).
- `git diff --check` → **limpio**.
- Bruno dinámico contra PostgreSQL 18 limpio + fast-jar
  → **23/23 requests, 83/83 tests, 0 failures, 0 errors, 0 skips,
  4.554 s CLI / 6.070 s wall, Bruno CLI 4.1.0**.
- `graphify update .` → **DONE — 2.972 nodos / 9.064 aristas /
  141 comunidades**.

---

## Criterios de terminado

- [x] `GET /presupuestos/{id}/validacion` implementado con UUIDv7,
      owner-scope, errores canónicos (TC-P32-08..11).
- [x] `itemsPuCero`, `itemsCantidadCero`, `itemsSinActividad`
      calculados correctamente (TC-P32-01..06).
- [x] `exportable` calculado correctamente.
- [x] Tests TC-P32-01..13 escritos primero (RED) y verificados
      verdes (GREEN) por el escritor.
- [x] Colección Bruno `api/bruno/10-presupuesto/` autocontenida
      con UUIDv7 y temas P-28/29/30/31/32 (23 requests) y corrida
      dinámica **23/23 verde** contra PostgreSQL 18 limpio + fast-jar.
- [x] Graphify preflight (`graphify query`) ejecutado por el
      escritor.
- [x] Documentación canónica sincronizada (este archivo, `00.md`,
      `README.md`, `00-ESTADO-ACTUAL.md`, `estado-actual.md`,
      `plans/README.md`, `CLAUDE.md`,
      `00-analisis-reevaluacion.md`, `api/bruno/README.md`).
- [x] Suite completa verde con conteo real (**438 = 435 pass +
      2 aceptados GM-19/GM-20 + 1 skipped GM-24 + 0 errors**) —
      verificada por el escritor.
- [x] `./gradlew spotlessCheck` → PASS.
- [x] `./gradlew build -x test` → PASS.
- [x] `git diff --check` → limpio.
- [x] **Sin commit unitario ni merge** — instrucción explícita del
      orquestador (no se ejecuta).
- [x] **`graphify update .` final** — DONE: 2.972 nodos / 9.064
      aristas / 141 comunidades.

---

## STOP conditions (específicas de este plan)

- **(A)** Si la suite completa tiene rojos distintos de GM-19/GM-20
  aceptados y GM-24 `@Disabled`, **STOP** — no cerrar I-07 con
  regresiones; identificar y arreglar el módulo responsable.
- **(B)** Si la validación de integridad requiere modificar el
  motor para detectar PU=0 cuando el APU es válido pero vacío
  (sólo HM), **STOP** — el motor ya detecta PU=0 como
  `costo_total = 0`; validar que la lectura es correcta antes de
  cualquier cambio.
- **(C)** Si Graphify refresh falla por caché corrupto, **STOP** —
  reindexar (`graphify update .`, no init). Si init falla por
  permisos, escalar.
- **(D)** Si el banner de `planes-para-estar-al-dia/00.md` requiere
  reescribir los planes históricos para mantener coherencia,
  **STOP** — el banner es aditivo; no se reabre contenido.
- **(E)** Si al ejecutar `PresupuestoValidacionResourceIT` se
  descubre que `PresupuestoRepository.findRubrosCubiertosPorCronograma`
  no aísla cross-presupuesto (TC-P32-12 falla), **STOP** — la
  causa es SQL nativo; corregir la query sin tocar la estructura
  JPA.
- **(F)** Si la auditoría revela que `itemsCantidadCero` debería
  poblarse vía API, **STOP** — eso requiere romper el contrato REST
  P-29; levantar al orquestador antes de cambiar el contrato.

---

## Cierre del módulo I-07

Al cierre de Plan 025 (medición verificada por el escritor,
2026-09-01), el módulo `presupuesto` queda **DONE** para I-07:

1. **Diseño cerrado** — 8 planes ejecutables (015, 019–025) con TDD,
   contratos y STOPs definidos y todos verificados.
2. **Canonical sources alineadas** — `06-database-schema.md`,
   `07-api-contract.md` y `08-codebase-design.md` actualizados para
   que ningún ejecutor futuro se contradiga con el diseño canónico.
3. **Documentación sincronizada** — los archivos de status lo
   reflejan como DONE.
4. **Bruno `10-presupuesto/`** autocontenido y verificado contra
   Postgres limpio sembrado por Flyway (23/23 verde dinámico).
5. **Código ejecutable** — Plan 025 entrega el endpoint P-32, los
   DTOs y el service; el deep copy SQL nativo de Plan 024 cubre el
   ciclo de versiones; CRUD de capítulos y rubros + resumen P-30 ya
   están en Plans 022/023.

Tras el cierre de Plan 025, la **siguiente tarea de planificación**
es **Plan 026 (I-08 — cronograma CRUD)**, que aún **no** existe como
archivo ejecutable y debe autorarse en su propia sesión. P-33…P-36
(CRUD y avance de `cronograma`/`actividad`) viven en I-08/I-09. Este
plan los difiere explícitamente y no los pre-construye.
