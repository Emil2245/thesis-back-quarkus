# Backend implementation plans — `thesis-back-quarkus`

> This directory holds executable implementation plans for the Quarkus backend of
> the SERCOP propuestas técnico-económicas platform. Each plan is self-contained:
> a less capable executor with zero context from any planning conversation should
> be able to open one file, follow its steps, run its verification commands, and
> ship the change.
>
> **Source of truth for what to build:** `../../thesis-docs/plan/backend/01-quarkus-backend.md`
> (stack + bootstrap command), `../../thesis-docs/plan/architecture/08-codebase-design.md`
> (module map), `../../thesis-docs/plan/architecture/06-database-schema.md` (DDL),
> `../../thesis-docs/plan/architecture/07-api-contract.md` (REST contract),
> `../../thesis-docs/plan/roadmap/01-plan-iteraciones-xp.md` (iteration order).
>
> Plans in this directory implement those docs. They do not re-decide what
> `thesis-docs` has already decided; when a plan cites a domain rule, it cites
> the canonical file.

## Recommended execution order

Plans map onto the XP iteration plan (roadmap/01). The five plans below cover
iteration I-01 in full plus the I-02 hito (motor de cálculo puro).

| # | Plan | Iteration | Status |
|---|---|---|---|
| 001 | [Bootstrap Quarkus project via code.quarkus.io](./001-bootstrap-quarkus.md) | I-01 | **DONE** (2026-07-24, reviewer-verified) |
| 002 | [CI: GitHub Actions (JVM + native)](./002-ci-github-actions.md) | I-01 | **DONE** (2026-07-24, reviewer-verified; needs first push-to-GitHub to prove workflows actually run) |
| 003 | [Postgres schema baseline (V001–V003 migrations)](./003-schema-baseline.md) | I-01 | **DONE** (2026-07-24, reviewer-verified; see V003 data-quality note below) |
| 004 | [Auth module (registration, login, JWT, reset, invitation)](./004-auth-module.md) | I-01 | **DONE** (2026-07-24, 25/25 tests green, 0 token leaks; 4 plan bugs fixed inline — see below) |
| 005 | [Motor de cálculo APU (pure Java + GM tests)](./005-motor-calculo.md) | I-02 | **DONE** (scaffold + 21 per-APU GMs + 5 propiedades verdes; consolidación cerrada por Plan 02/006 con residual aceptado — ver abajo) |
| 006 | [Motor consolidación fix (GM-19/GM-20, GM-21 audit, GM-24 real)](./006-motor-consolidacion-fix.md) | I-02 | **IMPLEMENTACIÓN APLICADA workbook-consistent (2026-08-28) — PARTIAL — CLOSED WITH DOCUMENTED IESS RESIDUAL.** Regla workbook-consistent aplicada en `internal/Consolidador.java` (`precioUnitario DOWN 2dp`; `precioTotal = cantidad × PU_2dp` retenido a escala 6 `HALF_UP`; agregación de totales de capítulo y `totalGeneral` desde esos `precioTotal` a escala 6; display canónico a 2 dp `HALF_UP` solo en presentación/assertion). `ConsolidadorFronteraTest` focal verde; `MotorApuTest` focal verde; `MotorPropiedadesTest` focal verde. **Residual aceptado:** GM-19 actual `395108.37` vs esperado `395115.32` (delta `-$6.95`); GM-20 cap. 1 actual `158907.21` vs esperado `158908.05` (delta `-$0.84`). GM-21 verde con allowlist auditado (entradas ≤ 0.03) a nivel PU (atribuido a artefactos de redondeo manual del workbook IESS; **no** se realizan auditorías exhaustivas per-rubro — preferencia del usuario). **No** se reabre el motor: workbook, golden expected values, tolerancias y fórmulas del motor quedan cerrados. |
| 007 | [Migración Maven → Gradle](../docs/007-migracion-gradle.md) | build tooling | **DONE** (2026-08-01; build/tests/dev-mode verified; CI/CD deferred — plan 002 debt stays open; refinements modernos en §008 del mismo doc) |
| 009 | [Módulos `proyecto` + `insumo`](../docs/modulos/README.md) | I-03 | **DONE** (2026-08-02; ver §009 post-execution notes) |
| 010 | [Seed de escenarios reales (V004)](../docs/04-SEED-ESCENARIOS.md) | I-04 | **DONE** (2026-08-02; 3 proyectos uno por estado, FINALIZADO = workbook CMT; verificado en Postgres limpio + suite sin regresión) |
| 011 | [Módulo APU núcleo (P-19…P-22)](../docs/modulos/03-apu.md) | I-05 | **DONE** (2026-08-11; P-19…P-22, editor APU, filas M/N/O/P, fila HM protegida, override precio + `JsonNullable` write-through vía `Motor.calcularApu`; 10 tests verdes, colección Bruno `api/bruno/08-apu/`) |
| 012 | [Formatter + lint (Spotless/Palantir + -Xlint:all)](../docs/012-format-lint.md) | tooling | **DONE** (2026-08-11; 142 archivos formateados, 0 warnings lint, sin regresión; ver nota post-ejecución) |
| 013 | [Módulo APU avanzado (P-23…P-27, P-45, P-46 + decisiones N04)](../docs/modulos/04-apu-avanzado.md) | I-06 | **PARTIAL** (reconciliado 2026-08-28 tras N04 temporal; Plan 04 cerrado 2026-08-29). P-25 marcado **OBSOLETO/SUPERSEDED**; `ApuValidacionService`, `CAMBIO_AUXILIAR`, `es_auxiliar`, `apu_auxiliar_id`, `cdAuxiliar` y la creación del módulo `recalculo` **no se implementan`. **DONE:** P-24 descuento legacy, duplicar APU, ET (Apache POI), rangos parametrizables, bases PERSONALES, copia al usar, seam `ParametrosProyectoCambio`, **P-26 plantillas APU (Plan 04 DONE 2026-08-29 — `ec.uce.propuestas.plantilla.*` verificación focal verde; regresión dirigida adyacente focal verde; suite completa NO reportada)**. **PARTIAL:** P-23 %CI por APU (sin propagación global), P-27 desglose (sin redondeo intermedio del motor — ahora natural `BigDecimal` vía Plan 14). **DONE:** P-46 plantilla de proyecto (Plan 06, 2026-08-29). **MISSING:** UUIDv7 en módulos actuales (Plan 07). Display config global + frontera APU→Rubro 2 dp `DOWN` quedaron implementados por Plan 14. **DEFERRED:** write-through global (`recalculo`), descuento global FORMA 1, recálculo atómico FORMA 2. |
| 014 | [Motor de cálculo: precisión natural, no-links, display global](./014-motor-precision-no-links.md) | I-02 + I-06 | **PARTIAL — IMPLEMENTATION COMPLETE, GM-21 CLEANUP DEFERRED (2026-08-28).** T1 workbook-consistent + T2 (no-links estructural: `ApuSnapshot.porcentajeIndirecto` nullable; `ApuCalculado`/`FilaSnapshot`/`ParametrosCalculo` sin campos auxiliares) + T3 (`DisplayConfig` + `GET /api/v1/config/display`, defaults 2/4) + T4 (`@Digits(integer=8, fraction=2)` en los 3 campos del catálogo cerrado) + borrado de DIAG **IMPLEMENTADOS**. Regla workbook-consistent vigente: `precioUnitario DOWN 2dp`; `precioTotal = cantidad × PU_2dp` retenido a escala 6 `HALF_UP`; capítulo y `totalGeneral` agregan esos `precioTotal` a escala 6; display canónico a 2 dp `HALF_UP` solo en presentación/assertion. Línea base del motor: **línea base del motor con GM-19/GM-20 aceptados y GM-24 omitido** (GM-19/20 con residual aceptado — `-$6.95` / `-$0.84`). Estado post-T2–T4: **`SnapshotSinAuxiliaresTest` focal verde + `DisplayConfigResourceTest` focal verde + `DisplayConfigResourceOverrideTest` focal verde + `DigitsValidationCatalogTest` focal verde + `ConsolidadorFronteraTest` focal verde + `MotorApuTest` focal verde + `MotorPropiedadesTest` focal verde + GM-21 verde con allowlist auditado (entradas ≤ 0.03) + GM-19/GM-20 rojos con residual aceptado**. **GM-21 cleanup DEFERRED** por preferencia del usuario (no auditoría exhaustiva per-rubro del único workbook IESS). GM-24 sigue `@Disabled` (fixture upstream). **No** se reabre el motor.
| 015 | [Retirar `descuento` por APU (P-24 / S-24 withdrawn)](./015-retirar-descuento-apu.md) | I-06 → I-07 | **DONE (2026-09-01) — verificación dirigida completa; suite completa / Spotless / build siguen pendientes (no se reclaman).** Retira el *seam* activo de descuento por APU (campo `Apu.porcentajeDescuento`, endpoint `PATCH /api/v1/apus/{apuId}/porcentaje-descuento`, `ParametrosCalculo.porcentajeDescuento`, `ApuCalculado.costoDirectoAjustado`, campos JSON `ApuResponse.porcentajeDescuento`, `ApuCalculoParametros.descuento`, `ApuCalculoResumen.cdAjustado`/`operacionCdAjustado`). El descuento sobrevive únicamente como **FORMA 1** (mutación de las columnas base de los **insumos elegibles** copiados a la base PROYECTO del proyecto; **MO exenta**, reversible desde la base, regulada por `parametros_sistema.rango_descuento_min/max`) y **FORMA 2** (edición atómica de un insumo ya PROYECTO; sin seam nuevo). Columna BD `apu.porcentaje_descuento` queda como **compatibility seam inert** (sin V009; JPA la ignora). **Evidencia (medida):** contrato descuento focal verde verde (`DescuentoRetiradoMotorTest` 4 + `DescuentoRetiradoContratoTest` 4 + `DescuentoEndpointRetiradoTest` 4); motor 45 totales / línea base del motor con GM-19/GM-20 aceptados (GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84` — residuales aceptados) + 1 omitido (GM-24 `@Disabled`); APU regresión `apu.*` **focal verde**; regla workbook-consistent (Plan 014) no se reabre. STOP conditions cierran cualquier reintroducción de `porcentajeDescuento` salvo `plans/NNN-redescuento-apu.md` con justificación funcional + nota en ambos `CLAUDE.md`. *Nota: las entradas 015–018 de los planes I-06 viven en `docs/modulos/planes-para-estar-al-dia/0N-*.md`; este Plan 015 (descuento APU) del directorio raíz las precede y desbloquea Plan 020 (DONE) y el DAG I-07.* |
| 015bis | [Administración de bases (D-12 archivar/borrar central)](../docs/modulos/planes-para-estar-al-dia/05-administracion-bases.md) | I-06 | **DONE (2026-08-29).** `DELETE /bases-personales/{id}` owner-to-404; ciclo SUPER_ADMIN completo bajo `/admin/bases-centrales`; CRUD/import CSV central; archivado oculta del catálogo normal y borrado posterior preserva copias PROYECTO. Sin migración, tercer rol ni recálculo global. Verificación dirigida: `ec.uce.propuestas.insumo.*` **verificación dirigida **focal insumo verde** (suite focal Plan 015bis)**, build sin tests verde excluyendo el gate Spotless global y `git diff --check` limpio. Suite completa no ejecutada. `spotlessCheck` global permanece rojo solo por 31 archivos ajenos preexistentes; los archivos Java de Plan 05 fueron formateados de forma dirigida y no aparecen en el reporte. Siguiente plan ejecutable: Plan 06 (plantillas de proyecto). |
| 016 | [Plantillas de proyecto completas (P-46, N04 §A8)](../docs/modulos/planes-para-estar-al-dia/06-plantillas-proyecto.md) | I-06 | **DONE (2026-08-29).** `GET/DELETE /plantillas-proyecto` (owner-to-404), `POST /proyectos/{proyectoId}/guardar-plantilla` (snapshot backend-authored sin aceptar JSONB del cliente), `POST /proyectos/desde-plantilla/{plantillaId}` (crea proyecto NUEVO BORRADOR; lineage vía `plantilla_proyecto_origen_id` con `ON DELETE SET NULL`). Reuso de `ResolverInsumoPlantillaService` P-26 (PROYECTO → CENTRAL → PERSONAL → pendiente con override 0 + advertencia) sin duplicación. Snapshot con cabecera reutilizable, parámetros, árbol estructural y compatibilidad V004; excluye precios, IDs, owner, estado, logo, nombre, lineage y timestamps. V006 añade `descripcion`; V007 admite rubros pendientes con cantidad 0 sin debilitar la API. Verificación principal `plantilla.*` + `schema.*`: **verificación principal focal verde**; regresión adyacente APU focal, insumo focal, identifier focal; build sin tests verde y `git diff --check` limpio. Suite completa no ejecutada. Spotless global conserva 31 violaciones preexistentes ajenas; ningún Java del Plan 06 aparece en el reporte. |
| 017 | [UUIDv7 en fronteras REST actuales](../docs/modulos/planes-para-estar-al-dia/07-uuidv7-fronteras-rest.md) | I-06 | **DONE · VERIFICACIÓN DIRIGIDA COMPLETA (2026-08-30).** Recursos migrados en este pase: `proyecto/firmante/parametros_proyecto` (path UUIDv7; `ParametrosProyectoResponse.proyectoId` migrado de `Long` a `UUID`), `insumo/base_insumos` (admin central y bases personales; `InsumoUsoResponse.apuId` migrado a `UUID`; `CopiarBaseRequest` con `UUID baseId`/`proyectoId`), `PresupuestoApuResource` (`UuidV7.parse(presupuestoId)` + parse de `ApuCrearRequest.plantillaId` en frontera), `PlantillaProyectoGuardarResource` y `PlantillaProyectoAplicarResource` (`UuidV7.parse` en path; seam `POST /proyectos/{proyectoId}/guardar-plantilla`), `DocumentoResource` (ET; `UuidV7.parse(presupuestoId)` + owner-to-404 presupuesto/proyecto). Ya alineados antes: APU/detalle, `plantillas-apu` (P-26), `ApuDetalleResponse.insumoId` UUIDv7. PK/FK permanecen `BIGINT`; `public_id` UUIDv7 ya existía en V001 (sin migraciones nuevas — no se creó V008/V009 y no se reabre V001–V007). `UuidV7.parse` (regex v7 + `UUID.version() == 7` + variant `2`) como parser canónico de frontera; 400 `validacion` para UUID malformado/no-v7; 404 `no-encontrado` para ajeno/inexistente (RNF-05). **Verificación dirigida: 233/233 tests disponibles verdes** (`proyecto` 17, `insumo` 57, `apu` 41, `plantilla` 75, `documento` 7, `identifier` 36; `presupuesto.*` aún no contiene tests). `git diff --check` verde; build sin el gate Spotless verde. `spotlessCheck` y el build exacto siguen rojos únicamente por 23 archivos preexistentes ajenos a Plan 07. Suite completa y Bruno quedan para Plan 08. Matriz endpoint↔ID externo y seams alineados en el doc del plan. |
| 018 | [Cierre documental, Bruno y verificación](../docs/modulos/planes-para-estar-al-dia/08-cierre-documental-y-verificacion.md) | I-06 | **DONE (2026-08-30).** Documentación backend sincronizada; `api/bruno/09-i02-i06/` autocontenido con UUIDv7 y nueve temas; colecciones 06–08 reparadas. Spotless y `build -x test` verdes. Suites dirigidas sin regresiones; suite completa con únicamente GM-19/GM-20 residuales aceptados y GM-24 omitido upstream. Sin módulos, endpoints ni migraciones nuevas; Graphify actualizado. |

Plans for I-08 (cronograma CRUD) through I-10 (export SERCOP) are
**DONE**: las iteraciones I-08/I-09/I-10 están cubiertas por
`plans/026` a `plans/031` (cronograma + export + admin de bases) y
cerradas al 2026-09-07 (la única orientación histórica de la
suite completa vive exclusivamente en el panel README; este README
no predice conteos). El
bloque I-11 (panel Super-Admin y piloto SUS) está **planned /
TODO** bajo [`./panel-admin/`](./panel-admin/README.md) (9 planes
032–040 PLANNED / TODO, listos para ejecutar). I-12
(medición SUS `n ≥ 5` + baseline RNF-06 + cierre de
variables de tesis) **no** está planificada aún: se
autoriza en su propia sesión tras ejecutar 033–040.

### I-07 — Módulo `presupuesto` (P-28…P-32)

> **Estado (2026-09-01):** **Plan 015 + Plan 019 + Plan 020 + Plan 021 +
> Plan 022 + Plan 023 + Plan 024 + Plan 025 DONE**; módulo `presupuesto`
> I-07 cerrado. Plan 022 implementación aplicada (P-28 completo):
> `CapituloResource` dedicado con `presupuestoId` en cada ruta,
> numeración jerárquica autogenerada, renumeración atómica en dos
> pasadas con entidades gestionadas (item temporal `~<id>` para no
> violar `UNIQUE (presupuesto_id, item)` durante el flush), prevención
> de ciclos self/descendiente con
> `CapituloRepository.esDescendienteOigual` (STOP (A) activado —
> recorrido `findById` en memoria en lugar de `WITH RECURSIVE`),
> cascade Postgres en `capitulo.parent_id` + `rubro.capitulo_id` con
> APUs que sobreviven (D-09 + V001 §3), y write-through por mutación
> vía `RecalculoService.recalcular(new Alcance.Version(presupuestoId))`
> que devuelve el árbol completo (`PresupuestoResponse`). Plan 025
> **DONE (2026-09-01)** entrega P-32, Bruno `10-presupuesto/` 23/23
> verde dinámico y `graphify update .` cerrado (grafo actualizado; métricas reportadas por el orquestador). Al cierre de Plan 025 la
> **siguiente tarea de planificación** era Plan 026
> (I-08 — cronograma CRUD), que en ese momento aún **no** existía
> como archivo ejecutable y debía autorarse en su propia sesión.
> Hoy esa situación es **histórica**: Plan 026 → 031 están
> **DONE** (cronograma + export) y el cierre de I-11 está cubierto
> por los planes 032–040 en `./panel-admin/`.
> Índice + 7 planes ejecutables (019–025) en
> [`../docs/modulos/05-presupuesto/`](../docs/modulos/05-presupuesto/00.md).
> Cronología documentada en este README: Plan 015 (descuento APU) →
> Plan 020 (recalculo write-through) → Plan 021 (ciclo de vida del
> presupuesto). Decisiones locked: PK/FK BIGINT internas; `public_id
> UUID DEFAULT uuidv7()` en `capitulo` y `rubro` (V008 estructural);
> motor no se reabre; regla workbook-consistent vigente; `recalculo` es
> el único seam nuevo de I-07; cronograma/actividad P-33…P-36 y export
> SERCOP P-37 siguen fuera de I-07 (I-08/I-09/I-10); la identidad
> pública UUIDv7 para `cronograma`/`actividad` queda pendiente para una
> migración futura de I-08 (numeración por determinar; no se pre-asigna
> V009); `alertas` (`PU_CERO`, cantidad=0, sin actividad — P-32) se
> cerraron en Plan 025 (no forma parte del shape inicial de
> `RubroResponse` — la validación vive en listas top-level
> `itemsPuCero[]` / `itemsCantidadCero[]` / `itemsSinActividad[]`
> con `RubroRefResponse` reducido, no en el read model del rubro).

| # | Plan | Iteración | Estado |
|---|---|---|---|
| 019 | [Identidad pública UUIDv7 y persistencia base](../docs/modulos/05-presupuesto/01-identidad-y-persistencia.md) | I-07 | **DONE · VERIFICACIÓN DIRIGIDA COMPLETA (2026-08-31)** — entidades `Capitulo`/`Rubro` con WU-03 (PanacheEntityBase + IDENTITY + `@Generated(INSERT) publicId` insertable=false/updatable=false; sin timestamps/callbacks) + V008 estructural (`public_id UUID DEFAULT uuidv7()` + dos `trg_public_id_immutable` reusando `fn_assert_public_id_immutable()` de V001 §5); repos owner-scoped mínimos, sin listados/subárboles anticipados. `SchemaBaselineIT` permanece byte-for-byte V001-only; `V008SchemaIT` cubre cinco invariantes del esquema latest; `PublicIdPersistenceTest` registra ambas entidades/repositorios. **Evidencia real:** contratos focalizados **23/23 verdes** (6 + 5 + 12), regresión APU **regresión adyacente APU **focal verde****, `spotlessCheck` y `build -x test` verdes, `git diff --check` limpio y Graphify actualizado (SQL omitido por ausencia de `tree_sitter_sql`). Suite completa/Bruno reservados para Plan 025. Regla del usuario codificada en [`00.md` §0](../docs/modulos/05-presupuesto/00.md): cada plan es baseline revisable y se reevalúa antes de implementar. |
| 020 | [Activación del módulo profundo `recalculo` (write-through)](../docs/modulos/05-presupuesto/02-recalculo-write-through.md) | I-07 | **DONE (2026-09-01) — verificación dirigida completa; suite completa / Spotless / build siguen pendientes (no se reclaman).** Módulo `ec.uce.propuestas.recalculo` activo con `Alcance = Version | Apu | Insumo` (sealed interface). `Motor` se invoca estáticamente (CDI nunca), `VersionSnapshotBuilder` permanece en `recalculo/internal` (público por límite de subpaquete Java; no es seam adicional). **Evidencia (medida):** `RecalculoServiceIT` **focal verde**s (RED/GREEN TDD focal con `@QuarkusTest` + Dev Services; `Motor` real, sin mocks); motor no se reabre; regla workbook-consistent (Plan 014) preservada. Pre-requisito = Plan 015 (DONE; sin `%descuento` global ni por APU). V001–V008 intactas; V009 no se pre-asigna. |
| 021 | [Ciclo de vida del presupuesto: auto-create v1 vigente, listado y read model](../docs/modulos/05-presupuesto/03-ciclo-presupuesto.md) | I-07 | **DONE (2026-09-01) — verificación dirigida completa y recheck post-fixture-only finalizado.** Auto-create v1 vigente en `POST /proyectos` orquestado desde `ProyectoService.crear` (gap detectado por la auditoría del plan, cerrado en este mismo plan con una llamada a `PresupuestoService.crearVigenteInicial(...)` en la misma `@Transactional`; sin seam nuevo, PK/FK y campos de `presupuesto` inalterados); `GET /proyectos/{id}/presupuestos` (lista de versiones, owner-scoped) servido por `ProyectoResource`; `GET /presupuestos/{id}` (read model **recursivo** completo del árbol `Presupuesto → Capitulo → Rubro`) servido por `PresupuestoResource` (split por cohesión); DTOs `PresupuestoVersionResponse` / `PresupuestoResponse` / `CapituloResponse` / `RubroResponse` con mappers estáticos (mapper recursivo aplanando por `parentId`); `alertas` (`PU_CERO`, etc.) **diferido a Plan 025** — `RubroResponse` inicial **no** incluye el campo `alertas`; `ParametrosProyecto` y `BaseInsumos` permanecen lazy (no se tocan en este plan). **Evidencia (medida tras retoques fixture-only):** `PresupuestoResourceIT` **focal verde** (UUIDv7, owner-scope, 400 UUIDv4 / malformado, 404 ajeno, árbol vacío al inicio, árbol poblado transitivo), `ProyectoResourceIT` **focal verde** (TC-P06-01 verifica `POST /proyectos` deja exactamente una fila `Presupuesto(version=1, es_vigente=true)` y aparece en `/proyectos/{id}/presupuestos`), `RecalculoServiceIT` **focal verde** (regresión); `./gradlew spotlessCheck` **verde**, `./gradlew build -x test` **verde**, `git diff --check` **limpio**; suite completa con GM-19/GM-20 aceptados y GM-24 omitido (sin errores); suite dirigida `motor.*` no se re-ejecuta en este plan (motor cerrado por Plan 014, residuales aceptados). V001–V008 intactas; V009 no se pre-asigna; ningún seam nuevo. DAG I-07 sigue desbloqueado. |
| 022 | [CRUD de capítulos con renumeración atómica y resumen por componente](../docs/modulos/05-presupuesto/04-capitulos.md) | I-07 | **DONE (2026-09-01) — verificación dirigida completa.** P-28 implementado en `CapituloResource` (POST/PUT/PATCH …/mover/DELETE) con `presupuestoId` en cada ruta; numeración jerárquica autogenerada; renumeración atómica en dos pasadas (entidades gestionadas, item temporal `~<id>`) que respeta `UNIQUE (presupuesto_id, item)` durante el flush; detección de ciclos self/descendiente con `CapituloRepository.esDescendienteOigual` (STOP (A) activado — recorrido `findById` en memoria en lugar de `WITH RECURSIVE`); cascade Postgres en `capitulo.parent_id` + `rubro.capitulo_id`, APUs sobreviven (D-09 + V001 §3); cada mutación termina con `RecalculoService.recalcular(new Alcance.Version(presupuestoId))` y devuelve el árbol completo (`PresupuestoResponse`). DTOs con `@Size(max=255)` en `descripcion` y validación explícita de `orden` en `[1, hermanos+1]`. **Evidencia (medida):** `CapituloResourceIT` **focal verde**, `PresupuestoResourceIT` **focal verde**, `RecalculoServiceIT` **focal verde**, `apu.*` **focal verde**, motor **línea base del motor** (GM-19/GM-20 aceptados — residuales aceptados por Plan 014, no se reabre el motor) + GM-24 omitido (GM-24 `@Disabled` por fixture EMELNORTE upstream)**; suite completa con GM-19/GM-20 aceptados y GM-24 omitido (sin errores); `./gradlew spotlessCheck` verde, `./gradlew build -x test` verde, `git diff --check` limpio. DAG I-07 sigue desbloqueado. |
| 023 | [Rubros, totales write-through y resumen por componente](../docs/modulos/05-presupuesto/05-rubros-totales-resumen.md) | I-07 | **DONE (2026-09-01).** P-29/P-30 implementados: CRUD de rubros con D-09, cantidad > 0, items compactos y write-through por `Alcance.Version`; resumen read-only con M/N/O/P directos, `totalGeneral` persistido e IVA. Evidencia: rubros 19/19, resumen focal verde, capítulos 31/31, read model focal verde, cálculo read-only focal verde; suite 411 = 408 verdes + GM-19/GM-20 aceptados + GM-24 omitido; Spotless/build/diff verdes. |
| 024 | [Versionado de presupuesto: deep copy, vigente, comparación](../docs/modulos/05-presupuesto/06-versionado-comparacion.md) | I-07 | **DONE (2026-09-01).** P-31 implementado: deep copy bit-a-bit idéntico al origen (TC-P31-01 tolerancia 0.00), marcar vigente transaccional, eliminar con protección de la vigente (409 `version-vigente-protegida`), comparación lado a lado. Implementación bajo `ec.uce.propuestas.presupuesto.{dto,resource,service}` (`VersionadoService`, `PresupuestoVersionResource`, `PresupuestoVigenciaResource`, `ComparacionResource`); `PresupuestoRepository` gana `maxVersionDeProyecto` y `lockProyectoRow` (data-access only, sin seam en `proyecto/`). `cronograma` y `actividad` se copian con **SQL nativo** dentro de `VersionadoService` (entidades JPA viven en I-08/I-09; sin entidades nuevas, sin migraciones); columna inert `apu.porcentaje_descuento` (Plan 015) no se mapea/copia. Lock pesimista de fila serializa `max(version)+1` y toggles `es_vigente`; índice único parcial `ux_presupuesto_vigente` (V001 §2.8) preservado. `owner-to-404` (RNF-05), D-09 y UUIDv7 en frontera REST preservados. **Evidencia (medida):** `VersionadoResourceIT` **focal verde**; tests de deep-copy **cronometrados** sobre fixture compacto representativo (sin benchmark específico del árbol IESS completo); `./gradlew test --tests 'ec.uce.propuestas.presupuesto.*' ...` → **suite dirigida **focal verde****; regresiones `apu.*` y `recalculo.*` verdes; motor **línea base del motor** (GM-19/GM-20 aceptados y GM-24 omitido); `./gradlew spotlessCheck` verde; `./gradlew build -x test` **BUILD SUCCESSFUL** (deprecation warnings preexistentes sin error); `./gradlew test` (suite completa) → **focal verde con GM-19/GM-20 aceptados y GM-24 omitido**. `git diff --check` limpio y `graphify update .` finalizado correctamente. **No** se ejecutó commit unitario (instrucción explícita). **No** se modificó `motor/`, `recalculo/`, `apu/`, `proyecto/` fuente ni migraciones. DAG I-07 sigue desbloqueado; módulo `presupuesto` I-07 **módulo cerrado pendiente de Plan 025** en este pase. (Plan 025 cierra I-07 por separado — ver fila siguiente.) |
| 025 | [Validación de integridad, Bruno, Graphify y cierre del módulo I-07](../docs/modulos/05-presupuesto/07-validacion-y-cierre.md) | I-07 | **DONE (2026-09-01).** P-32 implementado: `ValidacionPresupuestoService.validar(UUID, Long) → ValidacionPresupuestoResponse{exportable, itemsPuCero[], itemsCantidadCero[], itemsSinActividad[]}` (PU desde `rubro.precio_unitario`, cantidad desde `rubro.cantidad`, sin actividad desde `PresupuestoRepository.findRubrosCubiertosPorCronograma` con SQL nativo narrow, mirror del deep copy Plan 024 — sin entidad JPA `Actividad`/`Cronograma`, sin V009, sin seam); `PresupuestoValidacionResource` con `GET /presupuestos/{presupuestoId}/validacion` (UUIDv7 validado en frontera con `UuidV7.parse`, owner-scope, errores 400 `validacion`/`no-encontrado` 404, roles `USUARIO`/`SUPER_ADMIN`, sin `@Transactional` read-only); DTOs `ValidacionPresupuestoResponse` y `RubroRefResponse` (4 campos exactos: `id` UUIDv7, `item`, `codigo`, `descripcion` — sin fugas de `BIGINT` ni del `RubroResponse`); `PresupuestoValidacionResourceIT` con **tests escritos primero** (TC-P32-01..13: defectos ortogonales, presupuesto vacío, versión válida, sin cronograma ⇒ todos los rubros sin actividad, defectos múltiples en un rubro, orden determinista, forma `RubroRefResponse` 4 campos sin fugas, UUID malformado, UUIDv4 → 400, UUIDv7 inexistente → 404, caller ajeno → 404, aislamiento cross-presupuesto, read-only tras N invocaciones); Bruno `api/bruno/10-presupuesto/` autocontenido (23 requests con helpers `TC-10-00a..00e` + 18 casos temáticos `TC-10-01..15` con P-28 desglosado en 4 sub-casos `TC-10-02a..02d`, P-29/P-30/P-31/P-32 + 3 negativos `TC-10-13..15` cubriendo UUIDv7/owner-scope) con `folder.bru` propio y `api/bruno/environments/dev.bru` extendido con vars runtime `p25*` (no se crea `10-presupuesto/environments/`, Bruno carga el environment compartido); Bruno dinámico contra PostgreSQL 18 limpio + fast-jar: **Bruno dinámico verde contra PostgreSQL 18 limpio + fast-jar** (medición CLI Bruno 4.1.0; `requests/asserts/failures/errors/skips/CLI time` reportados en el log del orquestador); el archivo raíz inválido `api/bruno/collection.bru` (no-op rechazado por el parser 4.1) fue removido antes de la corrida dinámica, el environment compartido `dev.bru` se mantiene libre de comentarios para el parser 4.1, y los tests `assert` de los requests usan `bru.getVar(...)` mientras la interpolación de cuerpo conserva `{{...}}`. `graphify query` preflight ejecutado; `graphify update .` final **DONE — grafo actualizado (nodos/aristas/comunidades reportados por el orquestador)** (lo ejecuta el orquestador al cierre). `api/bruno/README.md` actualizado con tabla de todas las colecciones y guía de ejecución de `10-presupuesto/`; `docs/modulos/05-presupuesto/00-analisis-reevaluacion.md` con `NEEDS ADJUSTMENT` cerrado antes de implementar (sin `Actividad.java` JPA, regla `itemsSinActividad` poblada por defecto cuando no hay cronograma, 23 requests Bruno en lugar de ~6–8). Documentación canónica sincronizada: `00.md` §0/§8/§10, `README.md` (fila `05-presupuesto`), `00-ESTADO-ACTUAL.md` §2/§3, `estado-actual.md` bloque I-07, `CLAUDE.md`. **NO** se ejecuta commit unitario ni merge (instrucción explícita). **NO** se reabre motor / V001–V008 / `recalculo` / `apu` / `proyecto/`. **Evidencia medida (2026-09-01):** `PresupuestoValidacionResourceIT` **focal verde**; `./gradlew test --tests 'ec.uce.propuestas.presupuesto.*'` focales verde; `./gradlew test --tests 'ec.uce.propuestas.apu.*'` focales verde; `./gradlew test --tests 'ec.uce.propuestas.recalculo.*'` focal verde; línea base del motor (GM-19/GM-20 aceptados y GM-24 omitido, sin errores); suite completa con GM-19/GM-20 aceptados y GM-24 omitido (sin errores); `./gradlew spotlessCheck` PASS; `./gradlew build -x test` PASS; `git diff --check` limpio. Módulo `presupuesto` I-07 **módulo cerrado**. Sin bloqueos correctivos identificados en Plans 019–024; las dos observaciones de auditoría no son defectos: `TRUNCATE ... CASCADE` cubre el reset de `cronograma`/`actividad` (no entidades JPA en este módulo) y D-09 (1:1 APU↔rubro por versión) implica que los códigos APU son únicos por presupuesto. Tras cierre Plan 025, la **siguiente tarea de planificación** (en ese momento) era **Plan 026 (I-08 — cronograma CRUD)**, que aún **no** existía como archivo ejecutable. Esa expectativa ya está cumplida: Plan 026 → 031 están **DONE** (cronograma + export), e I-11 se cubre con los planes 032–040 en `./panel-admin/`. |

### I-11 — Panel Super-Admin y piloto SUS (P-38…P-42, US-35…US-39)

> **Estado (2026-09-07 — corte de la planificación I-11):** **PLANNED / TODO**.
> Secuencia completa de 9 planes ejecutables en
> [`./panel-admin/`](./panel-admin/README.md): 032 (gate documental +
> inventario + reconciliación canónica con corrección de drift en
> `thesis-docs/plan/architecture/07-api-contract.md §1/§9`), 033
> (P-42 foundation + `LogActividadService` con `MANDATORY`; enum
> `EventoLogActividad` con **26 eventos verbatim**; **una** migración
> aditiva con el siguiente número disponible para
> `log_actividad.public_id UUID DEFAULT uuidv7()` + UNIQUE +
> inmutabilidad — V001–V009 intactas; sin `emitirFailure`,
> `REQUIRES_NEW` ni `codigoError`; **solo operaciones exitosas
> emiten**), 034 (P-38 gestión de usuarios e invitaciones; matriz
> self-delete/last-admin/cambio-email/primer SUPER_ADMIN **gated por
> acta 032** o diferida a I-12; `DELETE` con proyectos propios →
> 409 `usuario-con-proyectos-impedido` con test focal de mapeo
> FK), 035 (P-39 bases centrales — cierre del Plan 015bis;
> divergencias DELETE base/insumo **resueltas por acta 032**; sin
> marcas de paridad fabricadas), 036 (P-40 plantillas APU de
> sistema; longitud `descripcionRubro` confirmada contra columna
> real — sin tope arbitrario), 037 (P-41 parámetros + valores de
> referencia; `/proyectos/parametros-sistema` canónico; cualquier
> `clave` única con `fuente` no blank se acepta — sin CAMICON
> sembrado), 038 (D-13 instrumentación identidad y catálogos;
> `auth.password_cambiada` cubre perfil y reset; `usuario.activado`
> con origen `invitacion`; **16 nombres únicos del catálogo D-13 más un camino emisor adicional para `usuario.activado` con origen `invitacion`; 4 legacy V004
> excluidos), 039 (D-13 instrumentación presupuesto, cronograma,
> documento — preservando transacción única y TOCTOU de Plan 031;
> **6 operaciones canónicas** cerradas para `cronograma.editado`:
> `configurar`, `programar.reemplazar_avances`,
> `programar.distribuir_uniforme`, `programar.mover_segmento`,
> `programar.redimensionar_segmento`, `revisar`), 040 (integración
> + Bruno `12-admin/` autocontenido con **16 requests exactos** +
> piloto SUS 1–2 con cita Brooke (1996); cierre dual — backend
> puede cerrar, evidencia humana del piloto queda pendiente si el
> frontend o los participantes no están disponibles, nunca se
> fabrica). Cero código nuevo
> fuera del subdirectorio `plans/panel-admin/`; Plan 031 preservado
> intacto; motor, `recalculo/`, V001–V009 y los planes ya ejecutados (015,
> 019–031) intactos. La siguiente tarea de planificación tras ejecutar
> 033…040 es **I-12** (semanas 23–24: medición SUS `n ≥ 5` + baseline
> RNF-06 + cierre de variables de tesis), que se autoriza en su propia
> sesión.

| # | Plan | Iteración | Estado |
|---|---|---|---|
| 032 | [Sincronizar contrato e inventario admin](./panel-admin/032-sincronizar-contrato-inventario-admin.md) | I-11 | **PLANNED / TODO** (gate documental; corrige drift canónico en `07-api-contract.md §1/§9`; 20 decisiones locked; **033 STOPPED hasta que acta esté firmada**) |
| 033 | [Log de actividad — base](./panel-admin/033-log-actividad-base.md) | I-11 | **PLANNED / TODO** (P-42 / US-39 foundation; enum 26 eventos verbatim; MANDATORY; migración aditiva con siguiente número disponible; V001–V009 intactas) |
| 034 | [Gestión de usuarios e invitaciones](./panel-admin/034-gestion-usuarios-invitaciones.md) | I-11 | **PLANNED / TODO** (P-38 / US-35 / TC-P38-01..03; DELETE con proyectos → 409 con test focal FK; matriz self/last/email/first SUPER_ADMIN **gated por acta 032** o I-12) |
| 035 | [Bases centrales — cierre](./panel-admin/035-bases-centrales-cierre.md) | I-11 | **PLANNED / TODO** (P-39 / US-36 / TC-P39-01..03; divergencias DELETE base/insumo resueltas por acta 032; sin marcas de paridad fabricadas) |
| 036 | [Plantillas APU de sistema](./panel-admin/036-plantillas-apu-sistema.md) | I-11 | **PLANNED / TODO** (P-40 / US-37 / TC-P40-01; longitud `descripcionRubro` confirmada contra columna real — sin tope arbitrario) |
| 037 | [Parámetros del sistema y valores de referencia](./panel-admin/037-parametros-valores-referencia.md) | I-11 | **PLANNED / TODO** (P-41 / US-38 / TC-P41-01..02; DTO de `GET` decide acta 032; `valor_referencia` con `fuente` no blank — sin allowlist vacío que bloquee) |
| 038 | [Instrumentación D-13: identidad y catálogos](./panel-admin/038-instrumentacion-d13-identidad-catalogos.md) | I-11 | **PLANNED / TODO** (16 nombres únicos + 1 camino emisor adicional `usuario.activado` origen `invitacion`; `auth.password_cambiada` perfil+reset; 4 legacy V004 excluidos) |
| 039 | [Instrumentación D-13: presupuesto, cronograma, documento](./panel-admin/039-instrumentacion-d13-presupuesto-cronograma-documento.md) | I-11 | **PLANNED / TODO** (4 eventos; **6 operaciones canónicas** de `cronograma.editado`; preserva transacción única y TOCTOU de Plan 031) |
| 040 | [Integración del panel y piloto SUS](./panel-admin/040-integracion-panel-piloto-sus.md) | I-11 | **PLANNED / TODO** (Bruno `12-admin/` 16 requests exactos; `graphify update .`; piloto SUS 1–2 con cita Brooke (1996); cierre dual — backend puede cerrar, evidencia humana pendiente sin fabricación) |

### 013 — Módulo APU avanzado (P-23…P-27, P-45, P-46 + decisiones N04) (raised 2026-08-19, reconciled 2026-08-28)

Plan: [`../docs/modulos/04-apu-avanzado.md`](../docs/modulos/04-apu-avanzado.md).
**State: PARTIAL** (reconciliado tras N04 temporal, 2026-08-28). El
documento se conserva como rastro histórico; las instrucciones activas
están en [`../docs/modulos/planes-para-estar-al-dia/`](../docs/modulos/planes-para-estar-al-dia/).

**Decisión de mayor autoridad:** la entrevista N04 **temporal**
(`../thesis-docs/DOCUMENTOS/entrevistas/04/temporal/Respuesta_Entrevista_N04_TERMPORAL.md`
§2) elimina los enlaces entre APUs. Por tanto:

- **P-25 (auxiliares) — OBSOLETO/SUPERSEDED.** `es_auxiliar`,
  `apu_auxiliar_id`, `apuAuxiliarId`, `cdAuxiliar`, `CAMBIO_AUXILIAR` y
  `ApuValidacionService` **no se implementan**. Un supuesto "auxiliar"
  se modela como otro APU/rubro independiente.
- **Módulo `recalculo` — DEFERRED.** No se crea ningún módulo nuevo de
  primer nivel en esta etapa.
- **Librería documental — Apache POI** (`poi-ooxml`), ya presente en
  dependencias. **No** se usa docx4j.

**Estado por capacidad** (cruzado con
[`../docs/modulos/estado-actual.md`](../docs/modulos/estado-actual.md) §4):

| Capacidad | Estado |
|---|---|
| P-23 `%CI` por APU (PATCH + recalcular local) | **DONE** |
| P-23 propagación al cambiar default del proyecto | **DEFERRED** (requería `recalculo`) |
| P-24 descuento legacy por APU (`PATCH /apus/{id}/porcentaje-descuento`) | **WITHDRAWN — Plan 015 (2026-09-01).** El seam activo se retira; sólo sobrevive FORMA 1 (mutación de insumos PROYECTO, MO exenta, regulada por `rango_descuento_*`) y FORMA 2 (edición atómica de insumo). |
| P-25 enlaces auxiliares | **OBSOLETO/SUPERSEDED** |
| P-26 plantillas personales + carga con fallback | **DONE 2026-08-29 (Plan 04)** — `ec.uce.propuestas.plantilla.*` focal verde (SnapshotApuMapperTest focal verde + PlantillaApuResourceIT focal verde + ApuCalculoServiceNullableInsumoTest focal verde + PlantillaApuServiceTest focal verde). Regresión dirigida adyacente focal verde (ApuResourceIT focal verde + ApuCalculoServiceIT focal verde + ResolverInsumoProyectoTest focal verde). **No** se reporta suite completa. Snapshot totalmente price-free (writer nunca emite precios/IDs/links; reader tolera campos V004); fila pendiente con `insumo_id = NULL` + override `0` en columna de la sección (V005 estructural, no reseed); fila pendiente vs insumo real con `precio_unitario = 0` se distinguen por `insumo_id IS NULL` + `advertencias[]` (HTTP 200) vs `insumo_id` poblado + sin advertencias (HTTP 201). Ver [`docs/modulos/planes-para-estar-al-dia/04-plantillas-apu.md`](../docs/modulos/planes-para-estar-al-dia/04-plantillas-apu.md). |
| P-27 desglose de cálculo | **PARTIAL** — Plan 03; aplicar `CALC_PRECISION` queda en Plan 02 |
| P-45 ET (PUT/GET + DOCX con Apache POI) | **DONE** |
| P-46 plantilla de proyecto | **DONE 2026-08-29 (Plan 06)** — snapshot estructural price-free con cabecera reutilizable y compatibilidad V004; aplicar crea proyecto NUEVO BORRADOR con fallback P-26 y lineage opcional. Verificación principal **focal verde**; ver [Plan 06](../docs/modulos/planes-para-estar-al-dia/06-plantillas-proyecto.md). |
| `POST /apus/{id}/duplicar` | **DONE** |
| Rangos parametrizables (A6) | **DONE** |
| Base `PERSONAL` (A9) | **DONE** |
| A3 reordenamiento filas | **PARTIAL** |
| A9 copia al usar | **DONE** |
| D-12 archivar/borrar central | **DONE 2026-08-29** — Plan 05 ([planes-para-estar-al-dia/05](../docs/modulos/planes-para-estar-al-dia/05-administracion-bases.md)). Borrado PERSONAL owner-to-404 y administración CENTRAL completa con copia PROYECTO preservada. Regresión dirigida `ec.uce.propuestas.insumo.*` **verificación dirigida **focal insumo verde** (suite focal Plan 015bis)**; suite completa no ejecutada. Spotless global conserva deuda preexistente ajena al plan. |
| `CALC_PRECISION`/`DISPLAY_PRECISION` | **WITHDRAWN / SUPERSEDED** — Plan 014 retira `CALC_PRECISION` del motor (precisión natural `BigDecimal`). Display global `precisionDinero=2` / `precisionPorcentaje=4` vía `app.display.*` (`application.yml`/env `DISPLAY_PRECISION`, `DISPLAY_PRECISION_PORCENTAJE`) + `GET /api/v1/config/display`. Plan 02 activo lo cubre ahora ([planes-para-estar-al-dia/02](../docs/modulos/planes-para-estar-al-dia/02-motor-precision-y-consolidacion.md)). |
| Consolidación APU→Rubro `DOWN` 2 dp | **PARTIAL — CIERRE CON RESIDUO ACEPTADO** — Plan 02 ([planes-para-estar-al-dia/02](../docs/modulos/planes-para-estar-al-dia/02-motor-precision-y-consolidacion.md)). Regla workbook-consistent aplicada; GM-19 / GM-20 cap. 1 con residual sub-céntimo aceptado por el autor (no se reabre el motor). |
| UUIDv7 en módulos actuales | **DONE 2026-08-30 · VERIFICACIÓN DIRIGIDA COMPLETA** — Plan 07 ([planes-para-estar-al-dia/07](../docs/modulos/planes-para-estar-al-dia/07-uuidv7-fronteras-rest.md)). Verificación dirigida completada en Plan 07; suite completa y Bruno cerrados en Plan 08. |
| Write-through global (`recalculo`) | **DEFERRED** |

> **No aplicar** las instrucciones activas del documento
> `../docs/modulos/04-apu-avanzado.md` sin cruzar con esta matriz. Su
> contenido histórico se preserva solo para auditoría.

## Dependency graph

```
001 bootstrap ──┬── 002 CI ────────────────────┐
                │                               │
                ├── 003 schema baseline ────────┤
                │        └── 004 auth ──────────┤   ← end of I-01
                │                               │
                └── 005 motor de cálculo ───────┘   ← I-02 hito (semana 4)
                                                     (parallelizable with 003/004:
                                                      pure Java, no DB, no auth)
```

**Key ordering notes:**
- **002 does not block anything** but is the earliest ROI: it turns
  every push into a regression check. Recommended second.
- **003 blocks 004** (auth needs `usuario` / `refresh_token` /
  `token_usuario` tables) — but only technically. If you want to build
  auth entities first for offline development, that's fine as long as
  the schema plan lands before merging auth.
- **005 has no runtime deps** and can be built anytime after 001. In
  practice, roadmap I-02 slots it in weeks 3–4 (after auth). It is
  written to be executable standalone.

## Conventions for every plan in this directory

- **Every plan begins with a "Context" section** explaining the *why*.
- **Every plan lists in-scope and out-of-scope files/paths explicitly.**
- **Every plan ends with a "Done criteria" section** whose items are commands
  with expected outputs (not prose like "works correctly").
- **Escape hatches:** if the executor hits an ambiguity not covered by the plan
  or the linked docs, STOP and report back — do not improvise. Domain rules in
  `thesis-docs` are frozen by interviews with stakeholders; guessing them wrong
  wastes downstream work.
- **Never invent domain terms.** Use the Spanish domain vocabulary from
  `thesis-docs/plan/domain/03-glosario.md` verbatim.

## Post-execution notes

### 009 — Módulos `proyecto` + `insumo` (raised 2026-08-02)

Build second-vertical-slice milestones per `docs/modulos/01-proyecto.md` and
`docs/modulos/02-insumo.md`. **State: DONE**, full suite green minus the two
known GM-19/GM-20 reds.

**Proyecto** (`ec.uce.propuestas.proyecto`): entidades `Proyecto`, `Firmante`,
`ParametrosProyecto`, `ParametrosSistema` (LECTURA), enums `EstadoProyecto`,
`PlazoUnidad`, `RolFirmante`, `ModoCodigoRubro`; DTOs/mappers; servicios
`ProyectoService`, `FirmanteService`, `ParametrosProyectoService`; resources
`/proyectos`, `/proyectos/{id}/firmantes`, `/proyectos/{id}/parametros`,
`/proyectos/parametros-sistema`. Propiedad resuelta por email del claim JWT →
404 si ajeno (RNF-05).

**Insumo** (`ec.uce.propuestas.insumo`): `BaseInsumos`, `Insumo`,
`UnidadCatalogo` + enums `TipoInsumo`, `TipoBase`; `InsumoCrudService`,
`BaseInsumosService`, `ImportacionInsumoService`, `CopiaBaseService`,
`UnidadCatalogoService`, `InsumoCatalogoService` (selector multi-fuente
P-16/P-21); parsing CSV puro (`CsvInsumoParser`, commons-csv) con validación
por tipo. Recursos `InsumoResource` (`/proyectos/{proyectoId}/insumos`) y
`BaseInsumosResource` (`/bases-centrales`).

**Deviations (approved, documented in `docs/modulos/`):** (1) `commons-csv`
added a catalog + build; (2) errores vía `ProblemaException` sobre
`GlobalExceptionMapper` (no enum nuevo); (3) resources JAX-RS `Response`
(en vez de `RestResponse<T>`) para coincidir con `PerfilResource`;
(4) D-08 `eliminar insumo` = `stub → 0` hasta el módulo APU (TODO en código);
(5) `parametros_sistema.id` es `Short`; (6) **repositorios obligatorios por
entidad** (`repository/` en ambos módulos) — decisión del autor para separar
SQL de reglas y evitar refactor al añadir consultas; `docs/01-ARQUITECTURA.md`
§5 actualizado; `usuario/` queda como caso previo a alinear; (7) **colecciones Bruno**
`api/bruno/06-proyecto/` y `api/bruno/07-insumo/` con requests autenticados
(login helper + token en variables) y CSV de ejemplo para importación.

**Suite state:** 63 tests total (antes 56), 2 red (GM-19/GM-20 preexistents,
sin tocar el motor), 2 skipped (GM-24, DIAG — como antes).

**Scope decision (2026-08-02): P-09 "duplicar proyecto" EXCLUDED.** Interview
N02 §3 advises against cloning whole projects ("propenso a errores al arrastrar
cronogramas o cantidades pasadas") and approves only copying **insumo bases**
(P-17, implemented). Therefore `POST /proyectos/{id}/duplicar` will NOT be
built; only insumo duplication between bases remains. D-04 is moot. Docs updated
in `docs/modulos/01-proyecto.md`, `docs/modulos/README.md`,
`docs/00-ESTADO-ACTUAL.md` and `docs/03-BASE-DATOS.md` (§6 matiz 2).

### 010 — Seed de escenarios reales (V004) (raised 2026-08-02)

`V004__seed_escenarios.sql` (723 KB, ~2600 líneas) añade 3 proyectos completos,
uno por estado, más soporte (`valor_referencia` Anexo A, `plantilla_apu`
1 SISTEMA + 1 PERSONAL, `log_actividad` catálogo D-13 sin PII) y 2 usuarios
logueables (John Doe / Ana de Armas, `Clave1234`, ids 1 y 2). Escenario C
(FINALIZADO) reutiliza el workbook real **Cetro Médico Tulcán** desde los
fixtures del motor (298 rubros, 395115.32). **State: DONE.**

**Generación:** script desechable `/tmp/opencode/gen_seed_cmt.py` (fragmento C)
+ `gen_esc_b.py` (escenario B sintético con write-through coherente) +
`gen_act_cmt.py`/`gen_act_b.py` (actividades; pesos Σ=100.0000). Los 6 códigos
de APU reutilizados en 2 rubros del workbook se desambiguan con copia `-B`
(D-09 `rubro.apu_id` UNIQUE).

**Verificación (Postgres 18 limpio + suite):** todas las consultas de Done
criteria en §7 del plan pasan; `./gradlew test` = 63 tests, 2 red (GM-19/20
preexistentes), 2 skipped — sin regresión. Notas: la query del plan para el
capítulo 1 CMT se verificó sobre el **subárbol** (los rubros cuelgan de
subcapítulos 1.x); el APU real `501772` no tiene MO → no lleva HM (19 HM totales).

### 011 — Módulo APU núcleo (P-19…P-22) (raised 2026-08-11)

Plan: [`docs/modulos/03-apu.md`](../docs/modulos/03-apu.md). **State: DONE.**

Implementa lista/crea APUs por presupuesto (P-19/P-20), editor de cabecera (P-20),
filas M/N/O/P (P-21) con fila HM automática protegida, y override de precio vía
`JsonNullable` (P-22: presente = setea, `null` = vuelve a heredar, omitido = no
toca). Write-through de derivados (totales, subtotales de sección, costo/costo_hora
por fila) vía `Motor.calcularApu` — RNF-02 a nivel APU, sin invocar internos de
`motor/`.

**Desvíos aprobados (documentados en `docs/modulos/03-apu.md` §6):**
(1) `JsonNullable` vive en `org.openapitools:jackson-databind-nullable` (no viene
con `quarkus-rest-jackson`); se añadió `jackson-databind-nullable = "0.2.10"` al
version catalog + `JacksonConfig` (registra `JsonNullableModule`).
(2) TRUNCATE de `InsumoResourceIT`/`ProyectoResourceIT` ampliado con
`apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto` (cascada FK).

**Verificación:** `./gradlew test --tests 'ec.uce.propuestas.apu.*'` → BUILD
SUCCESSFUL (10 tests). `./gradlew test` completo → 73 tests, 2 red (GM-19/20
preexistentes), 2 skipped — sin regresión. Colección Bruno `api/bruno/08-apu/`
apunta al presupuesto v1 del seed de John Doe ("Rehabilitación de consultorios
UCE"); `presupuestoId` se fija en `environments/dev.bru` (id 1 en BD seed limpia).

### 003 V003 — insumos seed data quality (raised 2026-07-24)

The IESS CSV at `../../thesis-docs/plan/domain/_artifacts/insumos-seed-apus-cetro-medico-tulcan.csv`
turned out to be thinner than plan 003 assumed:

- **No `codigo` column** — executor generated synthetic codes `EQ-001…EQ-011`,
  `MO-001…MO-016`, `MA-001…MA-066` (11+16+66 = 93 rows total, no TRANSPORTE).
  If the thesis needs real vendor codes, they must be sourced upstream and
  a new migration (`V004__reseed_insumos.sql`) will replace this seed.
- **Empty `unidad`** on EQUIPO/MANO_OBRA rows — executor substituted `'h'`
  as required by the `insumo` CHECK constraint. Defensible; the schema forces
  it. Same conclusion: fix upstream if a value other than `'h'` was intended.
- **Unit case/encoding mismatch**: the CSV uses `'m2'`, `'m3'` (ASCII) and
  `'Kg'` on some MATERIAL rows, while `unidad_catalogo` seeds `'m²'`, `'m³'`,
  `'kg'` (Unicode/lowercase). Insumo has no FK to unidad_catalogo, so the
  DB accepts them, but the frontend will flag them as unknown-unit warnings
  once P-13 lands.
- **Flyway `quarkus:dev` verification blocked** by a local Windows Postgres
  service occupying port 5432. Executor validated migrations via direct
  `docker exec psql < V00X.sql` — DDL and DML both applied cleanly.

None of these blocks V001/V002 (both are pristine). None blocks feature
plans that read `insumo` (they'll work; unit warnings are cosmetic).

### 004 — plan bugs fixed by the executor (raised 2026-07-24)

The executor made four defensible deviations from plan 004; all approved:

1. **`PanacheEntityBase` + `@GeneratedValue(IDENTITY)`** instead of plan's
   `PanacheEntity`. Reason: `PanacheEntity` defaults to sequence-based ID
   generation, which does NOT map to Postgres `BIGINT GENERATED ALWAYS AS
   IDENTITY`. The executor's choice is the only one that validates against
   the schema with Hibernate `generation: validate`.
2. **JWT key paths** in `application.yml` need `META-INF/resources/` prefix
   (classpath-relative). Plan said bare `privateKey.pem` / `publicKey.pem`
   which failed with `MalformedURLException` at runtime.
3. **`datasource.jdbc.url` moved to `%dev`/`%prod` profiles only**. Setting
   it at global scope deactivates Dev Services for `%test`, breaking IT
   tests. Executor's restructure is the correct Quarkus idiom.
4. **Two extra `pom.xml` edits** beyond the authorized elytron dep:
   `io.rest-assured:rest-assured` (test scope) and a surefire
   `<includes>` pattern to route `*IT.java` through Surefire so
   `mvn test` picks them up. Both required to make the plan's own test
   plan runnable; plan should have listed them.
5. **TC-P01-03 assertion adjusted**: the plan expected first reenvio to
   return 202 and second to return 429, but the registration itself
   creates a fresh VERIFICATION_EMAIL token — so **any** immediate
   reenvio hits the 60 s cooldown. Executor asserts 429 on the first
   call, which correctly encodes the D-01 business rule.

**Non-issue also flagged:** `quarkus-security-jpa` extension from plan 001
is present but unused (this module chose JWT-only auth). Not conflicting,
but dead weight — remove in a cleanup plan if desired.

### 005 — motor de cálculo consolidación fails GM-19 & GM-20 (raised 2026-07-24)

Plan 005's scaffolding (facade `Motor`, snapshot/result records, `internal/`
calculators, `Fixtures.java`, all 4 test classes, 8 JSON + 2 CSV fixtures)
was already committed to main in `2fe6c83` before this session. Executor
verified the tree, ran the suite, and STOPPED per the plan's escape hatch
("Any test in MotorApuTest or MotorConsolidacionTest requires tolerance
> 0.00 to pass → STOP. That's a real bug."). Verified independently after
the dispatch — failures reproduce.

Suite state on `2fe6c83`:

| Suite | Result |
|---|---|
| `MotorApuTest` (21 tests, GM-01…GM-18, GM-22, GM-23, GM-25) | **focal verde** |
| `MotorConsolidacionTest` (4 tests, GM-19…GM-21, GM-24) | **2 fail, 2 pass** |
| `MotorPropiedadesTest` (5 property tests) | **focal verde green** |

Failing assertions (surefire, verified locally with `./mvnw -q test -Dtest='ec.uce.propuestas.motor.MotorConsolidacionTest'`):

- **GM-19** `MotorConsolidacionTest:53` — `totalGeneral` (2dp) expected
  `395115.32`, actual `395112.82`. Delta **−$2.50** on the Cetro Médico
  Tulcán presupuesto.
- **GM-20** `MotorConsolidacionTest:90` — Root chapter "1" total
  expected `158908.05`, actual `158909.20`. Delta **+$1.15**.

The thesis's `exactitud_calculo` dependent variable requires **0.00**
deviation, so these failures are load-bearing. The plan explicitly
forbids "adjusting the expected value" — the fixtures are authoritative.
Bug lives in `Motor.consolidar` (`internal/Consolidador.java`) or in
`Fixtures.versionFromJson`'s snapshot construction; per-APU math is
correct (all 21 per-APU GMs green + all 5 properties green).

Two additional plan-scope deviations found in the committed code (not
caused by any executor this session — pre-existing on main):

1. **GM-21 allowlist has 11 entries, plan specifies 6.** The plan says
   the 6 sub-cent mismatches are "logged in the domain doc"; no such
   reference is present in the code or docs. Either the workbook has
   more rounding artifacts than the plan captured, or 5 of those 11
   entries are covering up genuine motor bugs. Cannot tell without
   cross-checking each rubro against the source workbook.
2. **GM-24 is a no-op** — the test body is `assertTrue(true, ...)`
   with a comment claiming the STOP was documented, rather than
   raising the STOP. So GM-24's "pass" is not evidence of anything.
   Once GM-19/GM-20 are fixed, GM-24 must be reimplemented against
   the EMELNORTE fixture per plan step 9.

**Next step for the maintainer:** ~~a follow-up plan (`006-motor-consolidacion-fix.md`)~~
Plan 006 written and partially executed — see the 006 section below for
the actual root cause (spoiler: none of the hypotheses in this paragraph
were right; the delta is intermediate-workbook-rounding in the source
data, not a bug in the Motor).

**Environment quirks logged during verification** (not blockers, but
worth writing down): the machine's default `java` on PATH
(`/usr/lib/jvm/java-25-openjdk`) is a JRE-only install with no `javac`,
so `./mvnw` fails until `JAVA_HOME=/home/etverkade/.jdks/temurin-25.0.3`
is exported. And `mvnw` was committed without the executable bit, so
`./mvnw` fails without `chmod +x` first. Neither is worth a plan on its
own; note them in the repo's dev-setup doc when one exists.

### 006 — executed, cierre parcial USER-DECIDED (raised 2026-07-24, decision closed 2026-08-19 N04-bis, workbook-consistent 2026-08-28, partial closure 2026-08-28)

**Status actual (2026-08-28):** **IMPLEMENTACIÓN APLICADA workbook-consistent — PARTIAL — CLOSED WITH DOCUMENTED IESS RESIDUAL.** La regla workbook-consistent quedó aplicada en `internal/Consolidador.java`: `RoundingMode.DOWN` 2 dp **solo** a `Rubro.precioUnitario = APU.costoTotal.setScale(2, DOWN)`; `Rubro.precioTotal = cantidad × PU_2dp` se retiene a la escala de persistencia 6 (`NUMERIC(14,6)`) con `HALF_UP` aplicado **únicamente** en esa frontera de resultado (sin truncar cada PT a 2 dp); capítulo y `totalGeneral` agregan esos `precioTotal` a escala 6; display/assertion canónico a 2 dp `HALF_UP` ocurre solo en la capa de presentación. `ConsolidadorFronteraTest` focal verde (T1 workbook-consistent); `MotorApuTest` focal verde; `MotorPropiedadesTest` focal verde; GM-21 verde con allowlist auditado (entradas ≤ 0.03) a nivel PU.

**Residual aceptado por el autor (cierre parcial user-decided 2026-08-28):** GM-19 actual `395108.37` vs workbook esperado `395115.32` (delta `-$6.95`); GM-20 cap. 1 actual `158907.21` vs esperado `158908.05` (delta `-$0.84`). Atribuido a artefactos de redondeo manual dispersos en el example workbook IESS (one example workbook, no exhaustive per-rubro audit — preferencia del usuario). **No** se reabre el motor: workbook, golden expected values, tolerancias ni fórmulas del motor se modifican para cerrar este residual.

El plan
[`02-motor-precision-y-consolidacion.md`](../docs/modulos/planes-para-estar-al-dia/02-motor-precision-y-consolidacion.md)
(Plan 02) refleja el cierre parcial (mismo residual; §6 explícita).
[`plans/014-motor-precision-no-links.md`](./014-motor-precision-no-links.md)
sigue **OPEN / READY FOR REMAINING IMPLEMENTATION** con T1 marcado
EXECUTED y T2 (no-links estructural), T3 (display config global),
T4 (`@Digits`) y borrado de DIAG abiertos. Línea base del motor
post-implementación: `MotorApuTest` focal verde, `MotorPropiedadesTest` focal verde,
`ConsolidadorFronteraTest` focal verde, GM-21 verde (allowlist auditado; entradas ≤ 0.03),
GM-19/GM-20 rojos con residual aceptado, GM-24 y DIAG `@Disabled`.
El bloque siguiente conserva la historia de investigación para auditoría.

**What landed cleanly** (safe to keep, not merged yet — uncommitted in the
working tree):
- `Fixtures.java` §1 stub-precision fix: 279 stubbed rubros now reproduce
  the workbook's `precioTotal` exactly. This is a strict improvement in
  test-data fidelity even though it didn't fix GM-19/20.
- `MotorConsolidacionTest.java` §3 GM-21 audit: all 11 allowlist entries
  survived the fix (zero removable). Each now has a source comment showing
  motor vs fixture 2dp values. Plan 005's estimate of "6 entries" was
  approximate; the workbook has 11 rounding artifacts.
- `MotorConsolidacionTest.java` §4 GM-24: replaced `assertTrue(true)`
  no-op with a real `@Disabled` annotation citing the upstream
  fixture bug (empty `secciones`, null `codigo`). Confirmed independently
  by reading the first 30 lines of the EMELNORTE fixture.

**What did NOT land** — plan 006's §2 STOP condition triggered:
- GM-19 and GM-20 still fail with the same deltas after §1 (verified with
  fresh `./mvnw -q test`). Executor added a temporary `@Disabled`
  `DIAG_rubro_expected_vs_actual` diagnostic method that dumps per-rubro
  expected-vs-actual; running it (with `@Disabled` removed) produced:
  ```
  DIAG SUMMARY: fixtureSum=395115.32 motorSum=395112.82 totalDelta=-2.499876
  divergentCount=16 stubCount=279
  first divergent: item=1.1.1 codigo=501BM6 fixturePT=1568.308000 motorPT=1569.791937 delta=+1.483937
  ```
  All 16 divergent rubros are real-APU rubros (not stubs). Motor is
  arithmetically correct; the workbook is computing
  `precioTotal = cantidad × precioUnitario_2dp` rather than
  `cantidad × CT_full_precision`.

**Verified reviewer arithmetic** (independent of executor):
  `501BM6: 283.6 × 5.5352325 = 1569.79` (Motor) vs
  `283.6 × 5.53 = 1568.308` (workbook). Delta +$1.48 for one rubro.
  Sum of 16 similar rows nets to −$2.50.

**Escalation decision** (2026-07-24, per Emil): pause the fix. Do not
edit `Motor.java` or `Consolidador.java` yet. Raise with the director
before deciding whether:
  (a) Motor rounds `apu.costoTotal` to 2dp when constructing
      `RubroConPrecio.precioUnitario` (matches workbook exactly; motor
      internals stay 6dp),
  (b) Motor stays arithmetically pure and GM-19/GM-20 expected values
      are updated to $395,112.82 (thesis defense point becomes "motor
      is more accurate than the reference workbook"),
  (c) Upstream fixture is regenerated so `precioTotal =
      cantidad × CT_full_precision` — Motor matches automatically,
      but the audit trail against the real published SERCOP workbook
      is lost.

**Diagnostic left in place** for the follow-up session:
`MotorConsolidacionTest.DIAG_rubro_expected_vs_actual` is
`@Disabled`; remove `@Disabled` and re-run to reproduce the dump.

### 007 — Migración Maven → Gradle (raised 2026-08-01)

Build system migrated from Maven to Gradle 9.5.1 (Kotlin DSL). The plan lives
in [`../docs/007-migracion-gradle.md`](../docs/007-migracion-gradle.md) (per
author decision it was placed in `docs/`, not `plans/`). Outcome:

- `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`, `target/` deleted; added
  `settings.gradle.kts`, `gradle.properties`, `build.gradle.kts`, wrapper
  (`./gradlew`, Gradle 9.5.1).
- `./gradlew build -x test` → BUILD SUCCESSFUL, fast-jar at
  `build/quarkus-app/quarkus-run.jar`.
- `./gradlew test` → full suite **63 tests, 2 red (GM-19, GM-20 — known domain
  issue, not a regression), 2 skipped (GM-24 @Disabled, DIAG)**.
- `./gradlew --console=plain quarkusDev` smoke-tested against the compose
  Postgres: health UP, Flyway validated 3 migrations, OpenAPI 200.
- **CI/CD deferred** (author decision 2026-08-01): the plan 002 workflow
  debt stays open; when written it must use `./gradlew` and Gradle's
  `build/test-results/` + `build/*-runner` paths.
- Gotcha captured: Gradle `Test` task `include` patterns match compiled
  `.class` names, not `.java` files — `**/*Test.java` leaves the task at
  `NO-SOURCE`. Use `**/*Test.class` / `**/*IT.class`.
- Pre-existing (not migration) warnings: `quarkus.health.extensions.enabled`
  unrecognized and `quarkus.hibernate-orm.database.generation` deprecated,
  both from `application.yml`.

### 008 — Refinamientos modernos del build (raised 2026-08-01)

Detalle completo en [`../docs/007-migracion-gradle.md`](../docs/007-migracion-gradle.md) §008.
Resumen (decisión del autor: los planes son base, no muro; se aplica lo moderno
que aporta valor real):

- **Version catalog** `gradle/libs.versions.toml` — única fuente de versiones
  (Quarkus 3.37.4, poi, openpdf, jqwik). Plugin vía `alias(libs.plugins.quarkus)`.
- **Toolchain JDK 25** — el build corre sobre JDK 25 (auto-detectado o
  auto-descargado vía foojay). Target Java: **21 → 25** (decisión del autor).
- `gradle.properties` limpio: fuera `quarkusPlatform*` (ahora en el catalog);
  dentro `org.gradle.caching` + `org.gradle.parallel`.
- **Lombok/@Builder RECHAZADO** (verificado: DTOs de entrada no se construyen a
  mano; solo 14 sitios `new <Record>()` en main; motor es Java puro sin framework).
- **Consul/Stork/Micrometer/OTel/K8s/Jib RECHAZADO** — monólito, no microservicios;
  `thesis-docs` no lo contempla.
- Verificado: `./gradlew build` OK, `./gradlew test` 56 tests (2 red GM-19/20,
  2 skipped — sin regresión), `quarkusDev` arranca en `:8080` (credenciales
  compose: `DB_USER=postgres DB_PASSWORD=postgres`).

### 012 — Formatter + lint (raised 2026-08-11)

Plan: [`docs/012-format-lint.md`](../docs/012-format-lint.md). **State: DONE.**

Spotless 8.9.0 (`com.diffplug.spotless`, palantir-java-format sin versión fija)
+ `-Xlint:all` en `JavaCompile`. `spotlessApply` formateó 142 archivos Java
(main + tests, +1465/−1196, whitespace-only — verificado vía `git diff -w` y
suite). `spotlessCheck` acoplado a `check` via `tasks.named("check")`.

**Decisiones del autor:** (1) motor formateado *sin* exclusión — los cambios
en `Motor.java`/`internal/Consolidador.java` son whitespace-only (GM-19/20
siguen red por el tema de rounding del workbook, no por formato); (2) Error
Prone NO se añade (requiere su propio plan); (3) `-Werror` NO — solo ver.

**Verificación:** `./gradlew spotlessCheck` OK · `./gradlew build -x test` OK ·
`./gradlew test` → 77 tests, 2 red (GM-19/20 preexistentes), 2 skipped.

## Considered and rejected

*(empty — no findings have been rejected yet; this section grows as future
planning sessions triage findings)*
