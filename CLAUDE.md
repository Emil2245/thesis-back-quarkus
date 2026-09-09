# CLAUDE.md — guidance for AI sessions in `thesis-back-quarkus`

Read this before touching anything. Terse on purpose; every line pays rent.

## What this repo is

Quarkus backend for a thesis project — a SERCOP propuestas técnico-económicas
platform. See [`README.md`](README.md) for the human overview.

## Cronograma reference project (I-08 planning only)

For the planning and clarification of I-08 and later cronograma work, use
`../ingepresupuestos/` as a complementary functional reference. The upstream
project is <https://github.com/ingelibre/ingepresupuestos>.

This reference is **only an example of cronograma behavior and UX**. It is not
a normative source and does not authorize copying its Python/PySide6/SQLite
architecture, code, schema, naming, or complete feature set. This repository
remains a Quarkus + PostgreSQL backend for the Ecuadorian SERCOP domain, with
public UUIDv7, internal BIGINT PK/FK, and the canonical decisions in
`../thesis-docs/`.

Read together with:

- `../thesis-docs/DOCUMENTOS/entrevistas/05/N05_entrevista-cronograma.md` —
  current business clarifications.
- `../ingepresupuestos/core/cronograma.py` — CPM, dependencies, lag,
  non-consecutive segments, and auto-programming examples.
- `../ingepresupuestos/views/cronograma_view.py` — Gantt, valued schedule,
  S-curve, editing UX, and MSPDI XML export examples.

Functional ideas to evaluate, not blindly replicate: non-consecutive activity
periods, editable Gantt bars, a valued spreadsheet-like view, a separate S-curve
view, incomplete distributions saved as drafts, automatic recalculation with
visible discrepancy warnings, budget chapter grouping, and Microsoft Project
interoperability through standard MSPDI XML rather than the proprietary `.mpp`
binary. Any conflict between the interview, `thesis-docs/`, and this reference
is a stop condition: document and resolve the decision before coding.

## The One Rule

**All design decisions live in `../thesis-docs/`.** This repo *implements*
those decisions; it does not re-decide them. Before proposing an approach,
find the relevant file in `../thesis-docs/plan/` and follow it. If the docs
are wrong, fix the docs *and* the code in the same change — never diverge
silently.

Canonical files (memorize these paths):
- `../thesis-docs/CLAUDE.md` — project-wide guidance for the docs repo
- `../thesis-docs/PROJECT_SPEC.md` — scope, out-of-scope, methodology
- `../thesis-docs/plan/architecture/06-database-schema.md` — DDL, canonical
- `../thesis-docs/plan/architecture/07-api-contract.md` — REST contract
- `../thesis-docs/plan/architecture/08-codebase-design.md` — module map + deep-module discipline
- `../thesis-docs/plan/domain/02-data-model.md` — **motor de cálculo formulas §16 are non-negotiable**
- `../thesis-docs/plan/roadmap/01-plan-iteraciones-xp.md` — iteration order + hitos + TC/GM/CHK ids
- `../thesis-docs/plan/quality/02-catalogo-pruebas.md` — test-case catalog and golden masters
- `../thesis-docs/plan/design/03-procesos-detalle.md` — process detail + `§J` decisions (D-01…D-13)

## Domain vocabulary — always Spanish

Do NOT invent English translations. The following terms are canonical:

| Term | Meaning |
|---|---|
| `insumo` | material / labor / equipment / transport input |
| `APU` | Análisis de Precios Unitarios — unit-price analysis |
| `presupuesto` | budget (versioned; one `es_vigente` per project) |
| `capitulo` | budget chapter (recursive tree, no depth limit) |
| `rubro` | budget line item (1:1 with an APU, per version) |
| `cronograma` | execution schedule (1:1 with presupuesto) |
| `firmante` | signer (CONSOLIDADO / APROBADO roles) |
| `HM` / `herramienta menor` | minor tools row in EQUIPO section |
| `base_insumos` | insumo catalog (CENTRAL or PROYECTO scope) |
| `plantilla_apu` | APU template (SISTEMA or PERSONAL) |
| `descuento` | **WITHDRAWN per APU** (Plan 015, 2026-09-01). Discount sobrevive únicamente como **FORMA 1** (mutación de las columnas base de los insumos elegibles copiados a la base PROYECTO del proyecto; **MO exenta**, reversible desde la base, regulada por `parametros_sistema.rango_descuento_min/max`) y **FORMA 2** (edición atómica de un insumo ya PROYECTO; sin seam nuevo). |
| `FORMA 1` / `FORMA 2` | mutually exclusive — FORMA 1 = mutación de la base PROYECTO (`InsumoCrudService.editar` sobre base PROYECTO) con MO exenta; FORMA 2 = edición atómica de un insumo ya PROYECTO. Nunca “monto absoluto”. |
| `%CI` / `porcentaje_indirecto` | indirect-cost percentage |
| `CD`, `CD_ajustado`, `CI`, `CT` | `CD` direct cost; `CI` indirect cost; `CT` total cost. `CD_ajustado` queda como sinónimo internal (`CD` cuando `descuento = 0`); **el campo `ApuCalculado.costoDirectoAjustado` se retira en Plan 015** (la aritmética del motor usa `CI = CD × %CI` y `CT = CD + CI`). Los JSON públicos de `ApuCalculoResumen` ya no exponen `cdAjustado` ni `operacionCdAjustado`. |

Two roles only: `USUARIO`, `SUPER_ADMIN`. No third role. No middleware roles.

## Numeric precision — load-bearing for the thesis

- `NUMERIC(14,6)` — monetary values (BigDecimal scale 6)
- `NUMERIC(5,4)` — percentages (0.1800 = 18%)
- `NUMERIC(12,6)` — quantities
- `NUMERIC(10,6)` — rendimiento (h/unit)
- `NUMERIC(7,4)` — peso_ponderado (%)
- **El motor opera con la precisión natural de `BigDecimal`.** No aplica
  redondeo intermedio a sus operaciones (la regla previa de
  `CALC_PRECISION=3 HALF_UP` queda **retirada** por Plan 014, 2026-08-28;
  ver [`plans/014-motor-precision-no-links.md`](plans/014-motor-precision-no-links.md)).
- **La única rounding dentro del motor** es la frontera APU→Rubro en
  `internal/Consolidador.java`, regla **workbook-consistent**
  (corrección 2026-08-28): `precioUnitario = costoTotal.setScale(2,
  RoundingMode.DOWN)`; `precioTotal = cantidad × precioUnitario`,
  retenido a la escala de persistencia 6 (`NUMERIC(14,6)`) con
  `setScale(6, RoundingMode.HALF_UP)`. **No** se trunca cada `precioTotal`
  a 2 dp — la versión previa con `setScale(2, DOWN)` simétrico en PU y
  PT producía deltas sistemáticos `GM19 = -$9.37` y `GM20 cap1 = -$3.09`
  vs workbook IESS (ver STOP conditions de `plans/014`). Cualquier otro
  `.setScale(...)` dentro de `motor/` es un bug — abrir STOP y reportar.
- **Display config** se rige por la config global `app.display.precision`
  (default 2, env `DISPLAY_PRECISION`) y `app.display.precision-porcentaje`
  (default 4, env `DISPLAY_PRECISION_PORCENTAJE`); endpoint público
  `GET /api/v1/config/display` → `{precisionDinero, precisionPorcentaje}`.
  Aplicado únicamente en la capa de presentación/export. La BD nunca
  redondea a 2 dp.

BigDecimal only. `double` and `float` are banned in `motor/`:
```bash
grep -r 'double\|float' src/main/java/ec/uce/propuestas/motor/    # must return zero hits
```

## Business rule reference (D-01 … D-13, from `design/03-procesos-detalle.md §J`)

Authoritative decisions. Do not restate. Look up when in doubt:

| ID | Rule |
|---|---|
| D-01 | Password ≥8 chars, ≥1 letter, ≥1 number. Email-verify token TTL **24h**. Reenvío cooldown **60s**. |
| D-02 | JWT access token **60 min**. Refresh token **30 days** if "recordar sesión" true, else browser-session only. |
| D-03 | Password change → revoke ALL refresh tokens. Email change → re-verify new email; account operable on old email until verified. |
| D-08 | Auxiliar APU cannot be modified/deleted if referenced (RESTRICT). |
| D-09 | Rubro ↔ APU is 1:1 per presupuesto version (UNIQUE constraint on `rubro.apu_id`). |
| D-11 | Super-Admin invitation token TTL **72h**. Never temporary passwords. |
| D-12 | Central `base_insumos` are ARCHIVED (`archivada=true`), never deleted. Only exception to the no-soft-delete rule. |
| D-13 | `log_actividad.evento` is a closed catalog. Never PII in `detalle`. |

## Motor de cálculo — the highest-stakes code

Lives in `src/main/java/ec/uce/propuestas/motor/`. Contract:

- **No framework.** No `@Inject`, no `@ApplicationScoped`, no Panache, no
  logging framework. Pure Java, `BigDecimal`, JDK only.
- **Two entry points on `Motor`**:
  - `Motor.calcularApu(ApuSnapshot, ParametrosCalculo) → ApuCalculado`
  - `Motor.consolidar(VersionSnapshot) → VersionCalculada`
- **Internal helpers under `motor/internal/`** are package-private. Never
  test them directly. Test through the public interface only.
- **Golden Masters** (`GM-01`…`GM-25`) are the acceptance test. Any GM
  failing → real bug. Do NOT add tolerance, do NOT adjust expected values.

**Current motor status** (cierre parcial USER-DECIDED 2026-08-28 — Plan 014;
[Plan 015 — DONE 2026-09-01](plans/015-retirar-descuento-apu.md)):
el motor opera con la **precisión natural de `BigDecimal`**; el workbook
IESS no aplica redondeo intermedio al APU (las 3 dp visibles son formato de
display). La **única rounding del motor aplicada** es la frontera
APU→Rubro en `internal/Consolidador.java` con la regla **workbook-consistent**:
`precioUnitario = costoTotal.setScale(2, RoundingMode.DOWN)`;
`precioTotal = cantidad × precioUnitario`, retenido a la escala de
persistencia 6 (`NUMERIC(14,6)`) con `setScale(6, RoundingMode.HALF_UP)`;
los totales de capítulo y `totalGeneral` se agregan desde esos `precioTotal`
a escala 6; el display canónico a 2 dp ocurre solo en la capa de
presentación/assertion. **No** se trunca cada `precioTotal` a 2 dp — la
versión previa con `setScale(2, DOWN)` simétrico en PU y PT quedaba
retirada por deltas sistemáticos `GM19 = -$9.37` y `GM20 cap1 = -$3.09` vs
workbook IESS (ver STOP conditions de `plans/014`). Display global
configurable vía `app.display.precision` (default 2) y
`app.display.precision-porcentaje` (default 4); endpoint público
`GET /api/v1/config/display` **IMPLEMENTADO** (Plan 014 T3, 2026-08-28 —
`DisplayConfig` + `DisplayConfigResponse` + `DisplayConfigResource`,
defaults 2/4 verificados por `DisplayConfigResourceTest` 1/1 +
`DisplayConfigResourceOverrideTest` 1/1). Validación de entrada `@Digits`
solo en los campos monetarios de DTO del catálogo cerrado del plan
(`ApuDetallePatchRequest.precioOverride`, `InsumoCrearRequest.precioUnitario`,
`InsumoEditarRequest.precioUnitario`; nunca en campos de entidad como
`tarifaJornal` / `precioUnitarioTarifa`, ni en cantidades, rendimientos
o porcentajes) — **IMPLEMENTADO** (Plan 014 T4, 2026-08-28, verificado
por `DigitsValidationCatalogTest` 3/3). Plan 015 retiró el *seam* activo de
descuento por APU: `motor.ParametrosCalculo.porcentajeDescuento` y
`motor.ApuCalculado.costoDirectoAjustado` eliminados; aritmética del motor
queda equivalente a `CI = CD × %CI`, `CT = CD + CI` (identidad con
`descuento = 0`).

**Baseline post-implementación (2026-09-01 — Plan 015 DONE + Plan 020
ACTIVATED; suite completa / Spotless / build siguen pendientes, no se
reclaman)**
`./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain` →
**45 tests** totales: **42 verdes** + **2 rojos** (GM-19 `-$6.95`, GM-20
cap. 1 `-$0.84` — residuales aceptados) + **1 omitido** (GM-24 `@Disabled`
por fixture upstream EMELNORTE). Desglose: `MotorApuTest` 21/21;
`MotorConsolidacionTest` 2/4 (GM-21 verde con allowlist 11 ≤ 0.03; GM-19,
GM-20 rojos); `MotorPropiedadesTest` 5/5
(`costoIndirecto_aplica_sobre_CD_y_depende_de_pct_ci`);
`ConsolidadorFronteraTest` 5/5; `SnapshotSinAuxiliaresTest` 6/6;
`DescuentoRetiradoMotorTest` 4/4. **No** se reabre el motor para cerrar
los residuales; workbook, golden expected values, tolerancias y fórmulas
del motor quedan cerradas. Preferencia del usuario: este es **un example
workbook único**; no se realizan auditorías exhaustivas per-rubro.

**Módulo `recalculo` (Plan 020, DONE 2026-09-01):** módulo profundo
`ec.uce.propuestas.recalculo` activo con interfaz
`recalcular(Alcance)`; `Alcance = Version | Apu | Insumo` (sealed
interface). `Motor` invocado estáticamente (CDI nunca);
`VersionSnapshotBuilder` permanece en `recalculo/internal` (`public` por
límite de subpaquete Java, no es seam). `RecalculoServiceIT` 4/4 verdes
(TDD focal con `@QuarkusTest` + Dev Services; `Motor` real, sin mocks).
Pre-requisito = Plan 015 DONE.

**Plan 021 (DONE 2026-09-01) — Ciclo de vida del presupuesto (auto-create v1 vigente, listado, read model):** módulo `presupuesto` activo con auto-create de la fila `presupuesto(version=1, es_vigente=true)` orquestado desde `ProyectoService.crear` en la misma `@Transactional` (sin seam nuevo — gap detectado por la auditoría del plan, ejecutado en este mismo plan); `GET /proyectos/{id}/presupuestos` (lista de versiones, owner-scoped) servido por `ProyectoResource`; `GET /presupuestos/{id}` (read model **recursivo** del árbol `Presupuesto → Capitulo → Rubro`) servido por `PresupuestoResource` (split por cohesión); DTOs `PresupuestoVersionResponse` / `PresupuestoResponse` / `CapituloResponse` / `RubroResponse` con mappers estáticos; `alertas` (`PU_CERO`, etc.) **diferido a Plan 025** y por tanto **no** forma parte del shape inicial de `RubroResponse`; `ParametrosProyecto` y `BaseInsumos` permanecen lazy (fuera de scope del read model). **Evidencia (medida tras retoques fixture-only):** `PresupuestoResourceIT` **9/9 verde**, `ProyectoResourceIT` **8/8 verde** (TC-P06-01 verifica el auto-create), `RecalculoServiceIT` **4/4 verde** (sin regresión); `./gradlew spotlessCheck` **verde**, `./gradlew build -x test` **verde**, `git diff --check` **limpio**; suite completa re-ejecutada **346 totales = 343 pass + 2 aceptados (GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84`) + 1 skipped (GM-24 `@Disabled`) + 0 errors**. DAG I-07 sigue desbloqueado.

**Plan 022 (DONE 2026-09-01) — CRUD del árbol de capítulos (P-28):** recurso dedicado `ec.uce.propuestas.presupuesto.resource.CapituloResource` con `@Path("/presupuestos/{presupuestoId}/capitulos")` y los 4 verbos `POST / PUT /{capituloId} / PATCH /{capituloId}/mover / DELETE /{capituloId}` (todas las rutas llevan `presupuestoId` por contrato — split por cohesión frente a `PresupuestoResource`, que sólo sirve el read model de Plan 021). DTOs `CapituloCrearRequest` / `CapituloEditarRequest` con `@NotBlank @Size(max=255)` en `descripcion`; `CapituloMoverRequest` con `@NotNull @Min(1) orden` y rechazo explícito de `orden` fuera de `[1, hermanos+1]` vía `CapituloService.validarPosicion` → 400 `validacion`. Numeración jerárquica autogenerada en backend (`item = orden` raíz; `item = padre.item + "." + orden` sub); renumeración atómica en dos pasadas sobre **entidades gestionadas** (`CapituloService.renumerarArbol`): pasada 1 aparca cada item que cambia bajo el prefijo `~<id>` (espacio de nombres disjunto, ≤ 19 chars en `VARCHAR(20)`) + `flush()`; pasada 2 asigna los items definitivos — sin ese aparcado, permutar hermanos o cruzar padre violaría `UNIQUE (presupuesto_id, item)` a mitad del flush. Detección de ciclos self/descendiente con `CapituloRepository.esDescendienteOigual` (recorrido `findById` por la cadena de padres en memoria, STOP (A) del plan activado — `WITH RECURSIVE` descartado por profundidad ≤ 4 IESS). `DELETE` cascadea por FKs Postgres (`capitulo.parent_id` + `rubro.capitulo_id` `ON DELETE CASCADE`); los APUs sobreviven porque `rubro.apu_id → apu.id` es `ON DELETE RESTRICT` y al cascadear rubros el catálogo `apu` queda intacto (D-09 + V001 §3). Cada mutación termina con `RecalculoService.recalcular(new Alcance.Version(presupuestoId))` (siempre tras `flush()`) y devuelve el `PresupuestoResponse` completo y fresco. **Evidencia (medida):** `CapituloResourceIT` **30/30 verde** (TC_P22_01…30 — incluye 03/15/29/30 para permutación de hermanos, 16/26 para renumeración de subárbol de profundidad 3, 17/18 para ciclos self/descendiente, 21 para cascade + `apuSigueExistiendo(apuId) == 1L`, 27 para owner-scope 404); `PresupuestoResourceIT` **9/9 verde** (sin regresión); `RecalculoServiceIT` **4/4 verde** (sin regresión); `apu.*` **49/49 verde** (sin regresión); motor **45 = 42 verdes + 2 rojos aceptados (GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84` — residuales aceptados por Plan 014, no se reabre el motor) + 1 skipped (GM-24 `@Disabled`)**; suite completa **376 totales = 373 pass + 2 aceptados (GM-19 + GM-20) + 1 skipped (GM-24) + 0 errors**; `./gradlew spotlessCheck` verde, `./gradlew build -x test` verde, `git diff --check` limpio. DAG I-07 sigue desbloqueado.

**Plan 023 (DONE 2026-09-01) — Rubros, totales y resumen P-30:** `RubroResource` implementa crear, editar cantidad y eliminar con UUIDv7/owner-scope, D-09, cantidad > 0, items compactos y write-through por `Alcance.Version`; `ResumenComponentesResource` es read-only y usa `ApuCalculoService.calcular`: M/N/O/P son contribuciones directas, mientras `totalGeneral` proviene de `presupuesto.total` porque incluye CI y rounding de frontera; IVA referencial se aplica sobre ese total. Evidencia: rubros 19/19, resumen 12/12, capítulos 31/31, presupuesto 9/9, cálculo read-only 3/3; suite **411 = 408 verdes + GM-19/GM-20 aceptados + GM-24 omitido**, Spotless/build/diff verdes.

**Plan 024 (DONE 2026-09-01) — Versionado de presupuesto (P-31):** módulo `presupuesto` completo con deep copy, vigente única, comparación y eliminación protegida. Toda la implementación vive bajo `ec.uce.propuestas.presupuesto.{dto,resource,service}`: `VersionadoService` (deep copy + marcar vigente + eliminar + comparación), `PresupuestoVersionResource` (crear), `PresupuestoVigenciaResource` (marcar vigente + eliminar), `ComparacionResource` (comparación), DTOs `PresupuestoVersionCrearRequest`, `ComparacionVersionesResponse`, `ComparacionItem`, `CapituloRaizComparacion`. Deep copy con una sola `@Transactional` que recorre el origen (BFS), reinserta con UUIDv7 públicos frescos y remapea FKs BIGINT internas (`presupuesto` → `capitulo` con `parent_id` → `rubro` → `apu` nuevo por rubro → `apu_seccion` (4) → `apu_detalle`); preserva referencias compartidas a `insumo` (no se copian) y la APU ET; cierra con `RecalculoService.recalcular(new Alcance.Version(nuevoId))` para que `presupuesto.total` / `capitulo.total` / `rubro.precio_total` coincidan bit-a-bit con el origen (TC-P31-01 tolerancia 0.00). Las filas `cronograma` / `actividad` aún no son entidades JPA (I-08/I-09) pero DM §3 / P-31 exige copiarlas, así que el deep copy las copia con **SQL nativo** dentro de `VersionadoService` (FK remapeada al nuevo `presupuesto_id` / `rubro_id`, misma transacción); sin entidades nuevas, sin migraciones, sin seam. La columna inert `apu.porcentaje_descuento` (Plan 015) no se mapea ni se copia; queda con default 0. Lock pesimista de fila `proyecto` (`PresupuestoRepository.lockProyectoRow`: `SELECT id FROM proyecto WHERE id = ? FOR UPDATE`) serializa `max(version)+1` y los toggles `es_vigente`; el índice único parcial `ux_presupuesto_vigente` (V001 §2.8) sigue garantizando «exactamente una vigente por proyecto». `owner-to-404` (RNF-05) y D-09 preservados en todos los recursos; UUIDv7 malformado / no-v7 → 400 `validacion`. Recursos REST: `POST /proyectos/{proyectoId}/presupuestos` (201), `POST /presupuestos/{id}/vigente` (200), `DELETE /presupuestos/{id}` (204 / 409 `version-vigente-protegida`), `GET /presupuestos/{id}/comparar?con=<UUIDv7>` (lado a lado con `porCapituloRaiz`, 400 si mismo id o distinto proyecto). **Evidencia (medida):** `VersionadoResourceIT` **14/14 verde** (success, error, independencia origen↔copia, cascade, UUIDv7, owner scope); tests de deep-copy **0.785 s** y **0.850 s** sobre fixture compacto representativo (sin benchmark específico del árbol IESS completo); `./gradlew test --tests 'ec.uce.propuestas.presupuesto.*' ...` → **85/85 verde**; regresiones APU y `recalculo` verdes; motor **45 = 42 verdes + 2 rojos aceptados (GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84` — Plan 014, no se reabre el motor) + 1 skipped (GM-24 `@Disabled`)**; `./gradlew spotlessCheck` verde; `./gradlew build -x test` verde (31 deprecation warnings preexistentes, sin error de build); `./gradlew test` (suite completa) → **425 totales = 422 verdes + 2 rojos aceptados (GM-19 + GM-20) + 1 skipped (GM-24) + 0 errors**. `git diff --check` limpio y `graphify update .` finalizado correctamente. **No** se ejecutó commit unitario (instrucción explícita del orquestador). DAG I-07 sigue desbloqueado.

**Plan 025 (DONE, 2026-09-01) — Validación de integridad P-32, Bruno y cierre del módulo I-07:** implementación efectiva del endpoint `GET /presupuestos/{presupuestoId}/validacion` (UUIDv7 validado en frontera con `UuidV7.parse`, owner-scope, errores canónicos 400 `validacion` / 404 `no-encontrado`, roles `USUARIO`/`SUPER_ADMIN`, sin `@Transactional` read-only). DTOs `ValidacionPresupuestoResponse` y `RubroRefResponse` (4 campos exactos: `id` UUIDv7, `item`, `codigo`, `descripcion` — sin fugas de `BIGINT` ni de `RubroResponse`). `ValidacionPresupuestoService.validar(UUID, Long)` computa tres listas independientes: `itemsPuCero` (filtro `precioUnitario = 0`), `itemsCantidadCero` (filtro `cantidad = 0`) y `itemsSinActividad` (rubros cuyo `id` NO esté en `PresupuestoRepository.findRubrosCubiertosPorCronograma(p.id)`, SQL nativo narrow con JOIN `cronograma`, mirror del deep copy Plan 024 — **sin entidad JPA `Actividad`/`Cronograma`, sin V009, sin seam nuevo**). Cada lista se ordena por `(item asc, publicId.toString())` y `exportable = itemsPuCero.isEmpty() && itemsCantidadCero.isEmpty() && itemsSinActividad.isEmpty()`. Regla locked: si el presupuesto no tiene cronograma, el conjunto de cubiertos es vacío y TODOS los rubros quedan en `itemsSinActividad` (TC-P32-04). `PresupuestoValidacionResourceIT` con **13 tests escritos primero** (TC-P32-01..13: defectos ortogonales, presupuesto vacío, versión válida, sin cronograma, defectos múltiples, orden determinista, forma `RubroRefResponse`, UUID malformado, UUIDv4 → 400, UUIDv7 inexistente → 404, caller ajeno → 404, aislamiento cross-presupuesto, read-only tras N invocaciones). Bruno `api/bruno/10-presupuesto/` autocontenido (23 requests: 5 helpers `TC-10-00a..00e` + 18 casos temáticos `TC-10-01..15` con P-28 desglosado en 4 sub-casos `TC-10-02a..02d`, P-29/P-30/P-31/P-32 + 3 negativos `TC-10-13..15` cubriendo UUIDv7/owner-scope) con `folder.bru` propio y `api/bruno/environments/dev.bru` extendido con vars runtime `p25*` (NO se crea `10-presupuesto/environments/`, Bruno carga el environment compartido). Bruno dinámico contra PostgreSQL 18 limpio + fast-jar: **23/23 requests, 83/83 tests asserts, 0 failures, 0 errors, 0 skips, 4.554 s CLI / 6.070 s wall, Bruno CLI 4.1.0**. El archivo raíz inválido `api/bruno/collection.bru` (no-op rechazado por el parser 4.1) fue removido antes de la corrida dinámica; el environment compartido `dev.bru` se mantiene libre de comentarios; los tests `assert` de los requests usan `bru.getVar(...)` mientras la interpolación de cuerpo conserva `{{...}}`. `api/bruno/README.md` actualizado con tabla de todas las colecciones y guía de ejecución de `10-presupuesto/`. `graphify query` preflight ejecutado; `graphify update .` final **DONE — 2.972 nodos / 9.064 aristas / 141 comunidades** (lo ejecuta el orquestador al cierre; sin `codegraph init` lifecycle, sin `uninit`/`install`/`uninstall`/`upgrade`). `docs/modulos/05-presupuesto/00-analisis-reevaluacion.md` con `NEEDS ADJUSTMENT` cerrado antes de implementar (sin `Actividad.java` JPA, regla `itemsSinActividad` poblada por defecto cuando no hay cronograma, 23 requests Bruno en lugar de ~6–8). **NO** se ejecuta commit unitario ni merge (instrucción explícita). **NO** se reabre motor / V001–V008 / `recalculo` / `apu` / `proyecto/`. **Evidencia medida (2026-09-01):** `PresupuestoValidacionResourceIT` 13/13; `./gradlew test --tests 'ec.uce.propuestas.presupuesto.*'` 98/98; APU 52/52; `recalculo` 4/4; motor 45 = 42 pass + 2 aceptados (GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84`) + 1 skipped (GM-24 `@Disabled`) + 0 errors; suite completa **438 = 435 pass + 2 aceptados + 1 skipped + 0 errors**; `./gradlew spotlessCheck` PASS; `./gradlew build -x test` PASS; `git diff --check` limpio. Sin bloqueos correctivos identificados en Plans 019–024; las dos observaciones de auditoría no son defectos: `TRUNCATE ... CASCADE` cubre el reset de `cronograma`/`actividad` (no entidades JPA en este módulo) y D-09 (1:1 APU↔rubro por versión) implica que los códigos APU son únicos por presupuesto. Tras cierre Plan 025, la **siguiente tarea de planificación** es **Plan 026 (I-08 — cronograma CRUD)**, que aún **no** existe como archivo ejecutable y debe autorarse en su propia sesión.

**Plan 032 (DONE, 2026-09-07) — Gate documental I-11 (acta e inventario admin):** sin código; firma del acta de reconciliación canónica del panel Super-Admin (`docs/modulos/panel-admin/00-acta-reconciliacion.md`) y publicación del inventario operativo (`docs/modulos/panel-admin/00-inventario-trabajo.md`). 21 decisiones locked (20 originales + adenda firmada D-21); 15 STOP con disposición (10 CLOSED + 4 DEFERRED a I-12 + 1 CLOSED con gate RED-first en 035); 26 eventos D-13 verbatim; matriz canónica `evento → detalle`; paridad P-38…P-42; 19 filas log V004 (4 legacy); 4 seeds `valor_referencia`. Drift de `07-api-contract.md §1/§9` corregido (registrado; aplicación pendiente del padre). Verificación: `git diff --name-only -- 'src/**'` y `'src/main/resources/db/migration/**'` ambos vacíos; acta firmada; 26 grep matches uno por evento literal; sin totales hardcodeados.

**Plan 033 (DONE, 2026-09-08) — Fundación P-42 (Log de actividad — base):** paquete `ec.uce.propuestas.usuario.audit` (subpaquete del módulo `usuario`, sin seam nuevo de primer nivel); enum `EventoLogActividad` con **26 entradas verbatim** (sin los 4 legacy V004) y `detallesEsperados()` materializando la matriz del acta 032; `LogActividadDetalleValidator` (rechaza evento desconocido con `IllegalArgumentException`; clave de detalle no permitida con `IllegalStateException`; valor acotado fuera del conjunto cerrado con `IllegalStateException`); `LogActividadService.emitir(...)` con `@Transactional(TxType.MANDATORY)` (fuera de tx exterior lanza `jakarta.transaction.TransactionalException` estándar sin wrapper propio); entidad JPA `LogActividad` con `publicId` UUIDv7 (V010) + `entidadPublicId` UUIDv7 nullable (V010; sin FK, sin DEFAULT, sin UNIQUE — D-21; los logs sobreviven al borrado de la entidad afectada) + `entidadIdLegacy` BIGINT nullable (mapea la columna V001 `entidad_id`, **sin** exponerla en el DTO); `detalle` JSONB persistido como `String` vía `ObjectMapper`; `LogActividadRepository` con filtros `usuarioId`/`evento`/`desde`/`hasta` + paginación `count(*)` separado; `LogActividadResource` con `GET /admin/logs` (`@Path("/admin/logs")` + `@RolesAllowed("SUPER_ADMIN")`) con validación Bean de paginación y filtro `evento` regex `[a-z0-9._-]{1,60}`; `LogActividadResponse` (`id` UUIDv7, `usuarioId` UUIDv7 nullable, `usuarioNombre`, `evento`, `entidad` nullable, `entidadId` UUIDv7 nullable mapeado **solo** desde `entidadPublicId`, `detalle` Map, `fecha` Instant). **Migración aditiva única** `V010__log_actividad_identidad_publica.sql` añade **ambas** columnas (`public_id` con UNIQUE + trigger `trg_log_actividad_public_id_immutable` reusando `fn_assert_public_id_immutable()` V001 §5; `entidad_public_id` sin FK). **NO** se edita V001–V009. **Evidencia (medida, 2026-09-08):** `LogActividadServiceTest` + `LogActividadSinPiiTest` + `LogActividadIndicesTest` + `EventoLogActividadTest` + `LogActividadDetalleValidatorTest` focal **26/26 verde**; `LogActividadResourceIT` (TC-P42-01..02 con filtro `evento` charset/longitud, USUARIO 403, legacy V004 legible pero no admitido al enum) verde; regresión `ec.uce.propuestas.usuario.*` completa **51/51 verde** (incluye auth, perfil, registro, login, logout, reset, invitación); suite completa con `test-port=0` autoritativa: **673 tests** = **670 verdes** + **2 rojos aceptados** (GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84` — Plan 014, no se reabre el motor) + **1 skipped** (GM-24 `@Disabled` por fixture EMELNORTE upstream) + **0 errors**. **`spotlessCheck` PASS**, **`build -x test` PASS**, **`git diff --check` limpio**. V010 sin FK sobre `entidad_public_id`. Sin commit unitario. DAG I-11 desbloqueado para 034–040.

**Plan 034 (DONE, 2026-09-08) — Gestión de usuarios e invitaciones (P-38 / US-35 / TC-P38-01..03):** paquete `ec.uce.propuestas.usuario.admin` con `UsuarioAdminService` + `UsuarioAdminResource` (`@Path("/admin/usuarios")` + `@RolesAllowed("SUPER_ADMIN")` a nivel de clase). Implementa el contrato exacto del acta 032 D-04 (decisión 31 del plan): **32 bytes aleatorios** del `SecureRandom` canónico de la JVM → **Base64URL sin padding** (`Base64.getUrlEncoder().withoutPadding()`) como portador efímero → **hash bcrypt único** a través del `PasswordService` existente → **descarte inmediato** del `byte[]` (sobreescrito a `0`) y del `String` portador (fuera de alcance al retornar del método, GC). El algoritmo se ejecuta **únicamente** al crear usuarios invitados; **no** se regenera en login, logout, cambio de contraseña, ni en ningún otro flujo posterior. El token de invitación 72 h viaja por el `TokenService` existente (`SHA-256`, TTL `app.auth.invitacion-ttl: PT72H`, `TipoToken.INVITACION`); token y contraseña temporal son **dos secretos independientes**. **No** se introduce helper `RandomUtil`; el `SecureRandom` de la JVM es la única fuente aleatoria. Endpoints: `GET /admin/usuarios` (listado paginado con `q` ILIKE escapado y `activo` Boolean, orden `createdAt desc, id asc` estable), `GET /admin/usuarios/{id}`, `POST /admin/usuarios` (invitación 72 h sin contraseña temporal), `PUT /admin/usuarios/{id}` (edición sin `email` — decisión 6 del acta 032), `POST /admin/usuarios/{id}/desactivar` y `POST /admin/usuarios/{id}/reactivar` (con `@Consumes(MediaType.WILDCARD)` para aceptar body vacío), `DELETE /admin/usuarios/{id}`. **`DELETE` con proyectos propios** (FK RESTRICT V001 §3 `proyecto_usuario_id_fkey`) captura `org.hibernate.exception.ConstraintViolationException` / `PersistenceException` / `SQLException` con SQLSTATE `23503` y/o mensaje `violates foreign key constraint` / `fk_proyecto_usuario` y la mapea a **409 `usuario-con-proyectos-impedido`** (`esViolacionForeignKey(...)` recorre la cadena de causas). DTOs `UsuarioAdminResponse` (record canónico con `id` UUIDv7, `nombre`, `email`, `rol`, `activo`, `emailVerificado`, `fechaCreacion` Instant — sin `passwordHash`, sin `tokenHash`, sin `invitacionExpiraEn`), `UsuarioInvitarRequest` (`@NotBlank @Size(max=200) nombre`, `@NotBlank @Email @Size(max=320) email`, `@NotNull Rol rol`), `UsuarioAdminEditarRequest` (sin `email`). Errores canónicos: 400 `validacion` (email-invalido, rol-invalido, nombre-requerido, uuid-invalido, tamano-pagina-invalido, evento-largo, evento-formato-invalido), 404 `no-encontrado` (UUIDv7 sin fila), 409 `conflicto` (`email-ya-registrado`, `usuario-con-proyectos-impedido`), 403 `forbidden` (caller `USUARIO`). Emisión D-13 (decisión 32) a través de 033 dentro de la misma `@Transactional` exterior: `usuario.invitado` con `detalle.tokenExpiraEn` ISO-8601 (entidadId top-level = UUIDv7 del `Usuario` invitado); `usuario.desactivado`/`usuario.activado` con `detalle.origen=admin` (entidadId = UUIDv7 del usuario afectado). `UsuarioRepository` extendido con `findByPublicId(UUID)` (admin sin owner-scope), `listar(String, Boolean, int, int)` (ILIKE escapado con `!` como carácter de escape para evitar ambigüedad en el parser HQL/JPQL — HQL rechaza `escape '\\'` como escape literal de dos caracteres; orden estable secundario `id asc`), `contar(...)`. **Decisiones deferidas a I-12** (no se codifican): self-delete, last-active-SUPER_ADMIN, cambio de email admin, primer SUPER_ADMIN bootstrap — `STOP-034-SUPERFICIE-ABIERTA` cerrado. **Evidencia (medida, 2026-09-08):** `UsuarioAdminResourceIT` **18/18 verde** (TC-P38-01 invitación/aceptación/duplicado/validación; TC-P38-02 desactivar/login-403/reactivar/login-200 + uuid-inválido/inexistente; PUT edición sin email; TC-P38-03 DELETE con proyectos → 409 `usuario-con-proyectos-impedido` con test focal FK + DELETE sin proyectos → 204; listado paginado con `q` y `activo`; orden estable; `USUARIO` 403); `LogActividadEmisionUsuarioTest` **6/6 verde** (emisión `usuario.invitado` con `detalle.tokenExpiraEn` ISO-8601; emisión `usuario.desactivado`/`usuario.activado` con `detalle.origen=admin`; invariante de idempotencia; email duplicado NO emite evento; claves canónicas `detallesEsperados()`); `UsuarioAdminSinContrasenaTemporalTest` **1/1 verde** (`STOP-034-CONTRASENA-TEMPORAL` cerrado: 10 invitaciones, 0 matches regex `(?i).*(password|contrase|clave|temporal|randombytes|temp_pwd|tempassword).*` en DTOs ni en `RecordingEnviadorCorreo`, token Base64URL exacto de 43 chars). Regresión `ec.uce.propuestas.usuario.*` completa **verde, 0 failures/errors/skips** (incluye 033 audit, auth, perfil, registro, login, logout, reset, invitación). Suite completa autoritativa con `test-port=0`: **698 tests** = **695 verdes** + **2 rojos aceptados** (GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84` — Plan 014, no se reabre el motor) + **1 skipped** (GM-24 `@Disabled`) + **0 errors**. **`spotlessCheck` PASS**, **`build -x test` PASS**, **`git diff --check` limpio**. Ninguna migración nueva; `motor/`, `recalculo/`, `auth/`, V001–V010 intactos. `grep -RInE 'temporalPassword|contrasenaTemporal|passwordTemporal|tempPassword' src/main/java/ec/uce/propuestas/`: **0 matches**. `grep -RInE 'RandomUtil' src/main/java/ec/uce/propuestas/usuario/admin/`: **0 matches**. `graphify update .` ejecutado. Sin commit unitario. DAG I-11 sigue desbloqueado; Plan 035 quedó ejecutado después de este cierre.

**Plan 035 (DONE, 2026-09-08) — Bases centrales (P-39 / US-36 / TC-P39-01..03):** cierre estrecho de Plan 015bis con listado admin paginado en forma canónica `Page<T>`, validación `page`/`size`, conteo real de referencias `apu_detalle` para responder 409 `insumo-en-uso`, y emisión D-13 `admin.base_editada` en las ocho mutaciones exitosas; GET, `soloValidar=true` y rechazos no emiten. El fixture con referencia directa de `apu_detalle` a CENTRAL es cobertura defensiva de corrupción/datos heredados; el flujo normal A9 copia a PROYECTO. **Evidencia medida:** RED focal 41 tests = 18 failures + 0 errors/skips (9 auditoría y 9 recurso); GREEN focal fresco con `--rerun-tasks` **41/41** (`AdminBaseCentralResourceIT` 29 + `AdminBaseCentralLogAuditoriaIT` 12); regresión dirigida `insumo.*` fresca **74/74**, 0 failures/errors/skips; el primer `spotlessCheck` encontró únicamente cuatro archivos Java de Plan 035 sin normalizar, `spotlessApply` los corrigió y el check posterior pasó dentro de `build -x test`; `build -x test` **PASS**; suite completa fresca **715 = 712 pass + 2 failures aceptados (GM-19/GM-20) + 1 skipped (GM-24), 0 errors**; `git diff --check` limpio; sin cambios en migraciones, `motor/` ni `recalculo/`. `graphify update .` finalizado: **4.453 nodos / 13.555 aristas / 182 comunidades**. Sin commit. Plan 036 fue ejecutado posteriormente.

**Plan 036 (DONE, 2026-09-08) — Plantillas APU de sistema (P-40 / US-37 / TC-P40-01):** paquete `ec.uce.propuestas.plantilla.admin` con `PlantillaApuAdminService` + `PlantillaApuAdminResource` (`@Path("/admin/plantillas-apu")`, `@RolesAllowed("SUPER_ADMIN")`) entrega listado paginado y CRUD SISTEMA. `POST` crea desde cualquier APU UUIDv7 existente mediante el lookup administrativo estrecho `ApuRepository.findByPublicId`, usado después del control de rol; el flujo público conserva owner-scope. PERSONAL y SISTEMA comparten `PlantillaApuService.serializarSnapshotDesdeApu(Apu)` → `SnapshotApuMapper` price-free, sin modificar el mapper; snapshots sin precios efectivos, IDs internos ni enlaces al APU origen. DTOs rechazan campos desconocidos/server-authored con 400; `descripcion_rubro TEXT` queda sin tope artificial. `admin.plantilla_editada` se emite solo en crear/editar/borrar exitosos con `operacion` y `tipo=SISTEMA`. **Evidencia:** RED focal 7/7 failures; GREEN admin **10/10**; regresión `plantilla.*` **85/85**; `spotlessCheck` y `build -x test` PASS; suite completa **725 = 722 pass + GM-19/GM-20 aceptados + GM-24 skipped, 0 errors**; diff limpio; sin migraciones ni cambios en `motor/`, `recalculo/` o `SnapshotApuMapper`; `graphify update .` finalizó con **4.529 nodos / 13.836 aristas / 185 comunidades**; sin commit. **Siguiente plan autorizado: 037**.

**Plan 037 (DONE, 2026-09-08) — Parámetros del sistema y valores de referencia (P-41 / US-38 / TC-P41-01..02):** `GET/PUT /proyectos/parametros-sistema` conserva su ruta canónica y usa `ParametrosSistemaResponse` completo; la actualización bloquea pesimistamente el singleton y emite D-13 `admin.parametros_editados` solo ante cambios efectivos, con diff numérico estable. `ProyectoService.crear` materializa transaccionalmente los 12 defaults en `parametros_proyecto`, por lo que cambios globales solo alcanzan proyectos nuevos. `ec.uce.propuestas.proyecto.admin` entrega CRUD/upsert paginado `SUPER_ADMIN` de `/admin/valores-referencia`; acepta cualquier clave única con `fuente` no blank, conserva las 4 filas V004 y expone `actualizado`. Los valores son informativos y una prueba arquitectónica impide dependencias desde `motor/`. **Evidencia:** RED focal 7 tests con 6 fallos esperados; GREEN focal **7/7**; regresión `proyecto.*` **25/25**; suite completa **732 = 729 pass + 2 failures aceptados (GM-19/GM-20) + 1 skipped (GM-24), 0 errors**; Spotless/build/diff PASS; sin cambios en migraciones, `motor/` ni `recalculo/`. Sin commit. **Siguiente plan autorizado: 038**.

**Plan 038 (DONE, 2026-09-08) — Instrumentación D-13 de identidad y catálogos:** los servicios transaccionales existentes de `auth`, `proyecto`, `insumo`, importación/copia de bases y `apu` emiten a través de `LogActividadService` (Plan 033) únicamente tras operaciones exitosas. Se cubren los 16 nombres únicos previstos más el camino adicional `usuario.activado` con `origen=invitacion`; `proyecto.duplicado` permanece explícitamente `no producer yet` porque el seam P-09 no existe. Importación CSV usa un seam interno sin auditoría por fila para producir solo `insumos.import_csv`; eventos de insumos se restringen a bases PROYECTO para no duplicar `admin.base_editada`; `apu.editado` cubre cabecera, `%CI` y ET, no filas de detalle. UUIDv7 se registran como identidad pública, sin BIGINT, PII ni secretos. **Evidencia medida:** focal `Plan038LogActividadIT` 6/6; regresión conjunta `usuario.*` + `proyecto.*` + `insumo.*` + `apu.*` 234/234; suite completa **738 = 735 verdes + 2 rojos aceptados** (GM-19/GM-20) + **1 omitido** (GM-24) + 0 errors; Spotless/build/diff PASS. Sin migraciones ni cambios en `motor/` o `recalculo/`; sin commit. Siguiente plan autorizado: 039.

**Regla de modificación del Motor (Plan 014, 2026-08-28):**
- **`Motor.calcularApu()` y `internal/CalculadorFila.java`:** el guard "do
  not touch" se levanta **de forma acotada y solo para Plan 014**, para
  **ediciones estructurales mínimas**: borrar las ramas obsoletas
  `esAuxiliar`/`cdAuxiliar` y leer el `%CI` por APU desde
  `ApuSnapshot.porcentajeIndirecto`. **Aritmética intacta:** fórmulas,
  `MathContext`, orden de operaciones y precisión natural de `BigDecimal`
  quedan semánticamente idénticos; **ningún redondeo intermedio** (la regla
  anterior de `CALC_PRECISION=3 HALF_UP` por operación queda **retirada**).
  Fuera de ese scope acotado el guard sigue vigente.
- **`internal/Consolidador.java`:** se permite modificar **solo** para
  aplicar la regla **workbook-consistent** de la frontera APU→Rubro
  (corrección 2026-08-28): `RoundingMode.DOWN` 2 dp **solo** en
  `precioUnitario`; `precioTotal = cantidad × PU_2dp` retenido a
  escala 6 (`NUMERIC(14,6)`) con `HALF_UP`; agregar los totales de
  capítulo y `totalGeneral` desde esos `precioTotal` a escala 6.
  **No** reintroducir `setScale(2, DOWN)` por rubro total (causaba
  deltas sistemáticos `GM19 = -$9.37` y `GM20 cap1 = -$3.09`).
  Cada nueva excepción debe documentarse con su plan
  (`plans/0NN`) y referenciarse aquí.
- **Snapshots/resultados del motor:** `ApuSnapshot` y `ApuCalculado` sin
  `esAuxiliar`; `FilaSnapshot` sin `cdAuxiliar`; `ParametrosCalculo` sin
  `porcentajeIndirectoApu` (no-links N04 §2 confirmado por Plan 014).
  `ApuSnapshot.porcentajeIndirecto` (nullable) es el único override
  semántico por APU; `ParametrosCalculo` retiene solo el default del
  proyecto.
- **`Fixture.java`:** no tocar las assertions de los GMs; solo reparaciones
  justificadas (con STOP conditions documentadas).

**Para cambios futuros al motor:** abrir `plans/0NN-motor-fix.md`
siguiendo el patrón de `plans/006`. Cada cambio debe:
1. Justificar el cambio por un cambio de requerimientos funcionales.
2. Actualizar `CLAUDE.md` y `thesis-docs/CLAUDE.md` con la nota
   correspondiente.
3. NO tocar tolerances de GMs existentes.
4. Re-correr `./gradlew test` completo y reportar baseline + delta.

## Verification commands (memorize)

```bash
./gradlew build -x test                                    # sanity build
./gradlew test                                             # full suite (~2 min cold)
./gradlew test --tests 'ec.uce.propuestas.motor.*'         # motor only
./gradlew test --tests 'ec.uce.propuestas.usuario.*'       # auth only
./gradlew --console=plain quarkusDev                       # live reload
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true   # native (~10 min)
```

Expected motor test count (post-Plan 015, 2026-09-01): **45 tests totales**
(21 `MotorApuTest` + 4 `MotorConsolidacionTest` + 5 `MotorPropiedadesTest` +
5 `ConsolidadorFronteraTest` + 6 `SnapshotSinAuxiliaresTest` + 4 `DescuentoRetiradoMotorTest`);
**42 verdes** + **2 rojos** (GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84` — **residual
aceptado**, no se reabre el motor) + **1 omitido** (GM-24 `@Disabled` por
fixture EMELNORTE upstream; DIAG borrado por Plan 014). Conteo total de la
suite se **reporta** desde los XML de `build/test-results/`, no se presupone.

Do **not** hardcode a post-change total. After any run, report the real
counts from the XML results instead of predicting them:

```bash
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
  build/test-results/test/TEST-*.xml
```

## Repo conventions

- **Package structure**: vertical slices per module (`usuario/`, `motor/`,
  `common/`, and future `insumo/`, `apu/`, `presupuesto/`, `cronograma/`,
  `documento/`). See `08-codebase-design.md §1`.
- **Deep modules only get `Service` + `Repository`**: motor, recalculo,
  versionado, documento, importacion, invitacion-tokens, correo. Plain
  CRUD is `Resource → Panache` direct. Don't invent `UsuarioService` for
  profile CRUD.
- **DTOs are Java `record`s**, camelCase JSON, `@Valid` at the resource
  boundary. Bean validation for format; business rules in the service.
- **REST base path**: `/api/v1` via `@ApplicationPath` on
  `common/RestApplication.java`. Never hardcode `/api/v1` in per-resource
  `@Path`.
- **Role checks**: `@RolesAllowed({"USUARIO","SUPER_ADMIN"})` on the
  resource class or method. `@PermitAll` explicitly on public endpoints.
- **Panache entities**: `PanacheEntityBase` + explicit `@Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)` to map to Postgres
  `BIGINT GENERATED ALWAYS AS IDENTITY`. **Do not use `PanacheEntity`** —
  its default sequence-based ID does not match the schema and startup
  fails under `hibernate-orm.database.generation: validate`.
- **Migrations**: `V{NNN}__{snake}.sql`. Never edit an applied migration;
  add a new one.
- **Secrets**: dev keys ship in `src/main/resources/META-INF/resources/`
  as `publicKey.pem` and `privateKey.pem`. Never commit prod keys.
  `.env.example` documents all env vars.

## Working with plans

`plans/` is the authoritative workflow log. When starting a task:

1. Read `plans/README.md` first — it tells you what's DONE, TODO, BLOCKED.
2. If a plan applies, read that plan file in full.
3. **Never edit source in a way the current plans don't authorize** —
   either follow an existing plan or write a new one (`plans/007-*.md`,
   `plans/008-*.md`, keep the numbering monotonic).
4. When work finishes, update `plans/README.md` with the new status.

Plans in this repo are executable by less-capable models. Keep them
self-contained: paths, code excerpts, verification commands with expected
outputs, escape hatches ("if X, STOP and report").

## Traps this project has hit before (real war stories)

- **Datasource URL at global scope kills Dev Services in tests.** Put
  `quarkus.datasource.jdbc.url` under `%dev:` and `%prod:` only. Leaving
  it at global level, even as an env-var default, deactivates Dev
  Services for `%test`.
- **JWT key location must be `META-INF/resources/privateKey.pem`** in
  YAML, not bare `privateKey.pem`. The bare form fails with
  `MalformedURLException` at runtime.
- **`PanacheEntity` extends fails schema `validate`.** Always
  `PanacheEntityBase` + `@GeneratedValue(IDENTITY)`.
- **The IESS insumos CSV has no `codigo` column** and empty `unidad` on
  MO/EQ rows. Current V003 seed generates synthetic codes and
  substitutes `'h'` per schema CHECK. If real vendor codes matter,
  source upstream and write `V004__reseed_insumos.sql`.
- **The EMELNORTE APU fixture has empty `secciones` and null `codigo`**
  — GM-24 is `@Disabled` for this reason. Not a code fix.
- **Workbook `precioUnitario` is 2dp rounded, `precioTotal` is
  `cantidad × precioUnitario_2dp`.** Motor computes at 6dp. This is the
  GM-19/20 open question. Do not "fix" by adding tolerance.
- **A local Windows Postgres on :5432 shadows Dev Services** in dev mode
  (not in tests — tests use container URL directly). If `quarkus:dev`
  fails with auth errors, either stop the local service or set the DB
  env vars to match it.
- **`gradlew` sometimes ships without the executable bit** — `chmod +x gradlew`
  if `./gradlew` says "permission denied" on WSL/macOS.
- **Gradle outputs to `build/`, not `target/`** — the fast-jar lives at
  `build/quarkus-app/quarkus-run.jar` and the native binary at `build/*-runner`.
  Dockerfiles and `.dockerignore` already point at `build/`.

## What NOT to do

- Do not add framework code to `motor/`. Not even a `@Slf4j`.
- Do not add English translations of domain terms.
- Do not lower a golden-master tolerance from 0.00.
- Do not modify an applied Flyway migration; add a new one.
- Do not use `double` or `float` in any file that participates in cost
  arithmetic.
- Do not log tokens, passwords (raw or hashed), or PII (RNF-08).
- Do not add dependencies to `build.gradle.kts` without a plan authorizing it.
- Do not run destructive git commands (`push --force`, `reset --hard`,
  `clean -fd`) without explicit user request.
- Do not silently work around fixture bugs in `thesis-docs`. Report them
  and stop.

## Contact points

- **Emil** (etverkade@deltamontero.com) — this repo's primary author.
- **Kevin** (kaandradec@uce.edu.ec) — coauthor; owns some upstream
  `thesis-docs` decisions.
- **Director:** Ing. Zoila de Lourdes Ruiz Chavez, PhD — final call on
  domain-semantic decisions like GM-19/20's rounding question.
