# Estado actual y plan directo para cerrar `04-apu-avanzado`

> **Corte auditado:** `main` en `9ccb25c`.
> **Modo de ejecución acordado:** directo sobre `main`, sin SDD, sin worktrees y
> sin crear nuevos módulos de primer nivel. Cada bloque termina en un commit.
> Las comprobaciones acumuladas quedan para el cierre y serán ejecutadas por el
> usuario.

Este documento responde una sola pregunta: **qué falta exactamente para dejar
los módulos backend actuales alineados con `docs/modulos/04-apu-avanzado.md` y
con los planes globales vigentes de `../thesis-docs`**.

## 1. Resumen ejecutivo

El backend ya tiene implementada una parte importante de I-06:

- IDs internos `BIGINT` + `public_id` UUIDv7.
- Seeds deterministas sin compatibilidad temporal.
- `%CI` por APU; **descuento legacy por APU WITHDRAWN — Plan 015 (2026-09-01); sólo sobreviven FORMA 1 (mutación de insumos PROYECTO, MO exenta, regulada por `rango_descuento_*`) y FORMA 2 (edición atómica)**.
- Duplicación profunda de APU.
- Desglose de cálculo.
- Especificaciones técnicas y exportación DOCX con Apache POI.
- Rangos configurables de parámetros.
- Bases PERSONALES y copia al usar hacia una base PROYECTO.

Lo que todavía falta, limitado a los módulos existentes, se concentra en:

1. el seam de `ParametrosProyectoCambio` está **commiteado/completo** en `main` como `feat(proyecto): expose parameter change seam`; el write-through global de parámetros hacia el frontend queda **diferido** (la costura neutral ya está expuesta para futura propagación);
2. corregir documentación contradictoria sobre APUs auxiliares;
3. (Plan 014 supersede — **cierre parcial 2026-08-28**) display global `precisionDinero=2` / `precisionPorcentaje=4` vía `app.display.*` + `GET /api/v1/config/display` **DONE 2026-08-28** (T3 Plan 014: `DisplayConfig` + `DisplayConfigResponse` + `DisplayConfigResource` `@PermitAll`; cobertura `DisplayConfigResourceTest` 1/1 + `DisplayConfigResourceOverrideTest` 1/1). La **única rounding del motor aplicada** es la frontera APU→Rubro 2 dp `DOWN` (`internal/Consolidador.java`, regla workbook-consistent: `precioUnitario DOWN 2dp`; `precioTotal = cantidad × PU_2dp` retenido a escala 6 `HALF_UP`; totales de capítulo y `totalGeneral` agregados desde esos valores); motor opera con `BigDecimal` natural (`CALC_PRECISION=3 HALF_UP` retirado). Residual aceptado en GM-19 (`-$6.95`) y GM-20 cap. 1 (`-$0.84`) — no se reabre el motor. `@Digits(integer=8, fraction=2)` en los tres campos monetarios del catálogo cerrado también **DONE 2026-08-28** (T4 Plan 014: `ApuDetallePatchRequest.precioOverride`, `InsumoCrearRequest.precioUnitario`, `InsumoEditarRequest.precioUnitario`; cobertura `DigitsValidationCatalogTest` 3/3). Edición estructural no-links en `Motor.java`/`CalculadorFila.java` **DONE 2026-08-28** (T2 Plan 014: `SnapshotSinAuxiliaresTest` 6/6);
4. ~~completar reordenamiento y precisión de la respuesta de cálculo~~ — **DONE 2026-08-28 (Plan 03)**;
5. ~~implementar plantillas de APU~~ — **DONE 2026-08-29 (Plan 04)**;
6. ~~completar administración de bases centrales y bases personales~~ — **DONE 2026-08-29 (Plan 05)**;
7. ~~completar plantillas de proyecto usando los paquetes existentes~~ — **DONE 2026-08-29 (Plan 06)**;
8. ~~uniformar UUIDv7 en las fronteras REST de los módulos actuales~~ — **DONE 2026-08-30 (Plan 07) · VERIFICACIÓN DIRIGIDA COMPLETA**. Recursos migrados en este pase: `proyecto/firmante/parametros_proyecto`, `insumo/base_insumos` (admin central y bases personales), `PresupuestoApuResource` (con parse de `plantillaId` en frontera), seam `POST /proyectos/{proyectoId}/guardar-plantilla`, `DocumentoResource` (ET). Ya alineados antes: APU/detalle, `plantillas-apu` (P-26), `ApuDetalleResponse.insumoId` UUIDv7. Sin migraciones nuevas (no V008/V009); PK/FK siguen `BIGINT`; motor intacto. Las suites dirigidas disponibles suman **233/233 verdes**; la suite completa queda para el [Plan 08](./planes-para-estar-al-dia/08-cierre-documental-y-verificacion.md). Ver [`planes-para-estar-al-dia/07-uuidv7-fronteras-rest.md`](./planes-para-estar-al-dia/07-uuidv7-fronteras-rest.md);
9. ~~cerrar documentación, Bruno y verificación transversal~~ — **DONE 2026-08-30 (Plan 08)**: Spotless y build verdes; suite completa 313/2/0/1, con solo GM-19/GM-20 residuales aceptados y GM-24 omitido upstream. Véase [`planes-para-estar-al-dia/08-cierre-documental-y-verificacion.md`](./planes-para-estar-al-dia/08-cierre-documental-y-verificacion.md) para el detalle.

El write-through global que exige un módulo profundo `recalculo` queda
**diferido**, porque crear ese nuevo módulo contradice el alcance solicitado en
esta etapa.

> **I-07 — DONE (2026-09-01).** La planificación completa del módulo
> `presupuesto` (P-28, P-29, P-30, P-31, P-32) está en
> [`docs/modulos/05-presupuesto/00.md`](05-presupuesto/00.md): 8 planes
> 015 + 019–025 que cubren la activación del módulo profundo
> `recalculo` (Plan 020), la identidad pública UUIDv7 para `capitulo` y
> `rubro` (Plan 019, V008 estructural), el auto-create de presupuesto v1
> vigente (Plan 021), CRUD capítulos (Plan 022), CRUD rubros 1:1 APU con
> write-through (Plan 023), versionado + deep copy + vigente + comparación
> (Plan 024) y validación de integridad + Bruno + cierre (Plan 025).
> **Plans 015 + 019–025 DONE.** Plan 025 entrega
> `GET /presupuestos/{id}/validacion` (UUIDv7 validado en frontera,
> owner-scope), DTOs `ValidacionPresupuestoResponse` +
> `RubroRefResponse`, `PresupuestoRepository.findRubrosCubiertosPorCronograma`
> (SQL nativo narrow, sin entidad JPA `Actividad`/`Cronograma`),
> `PresupuestoValidacionResourceIT` 13/13 escrito primero, Bruno
> [`api/bruno/10-presupuesto/`](../../api/bruno/10-presupuesto/)
> autocontenido (23 requests: 5 helpers + 18 casos temáticos cubriendo
> P-28/P-29/P-30/P-31/P-32 + 3 negativos UUIDv7/owner-scope),
> corrido dinámicamente contra PostgreSQL 18 limpio + fast-jar con
> **23/23 requests, 83/83 tests, 0 failures/errors/skips, 4.554 s CLI
> / 6.070 s wall, Bruno CLI 4.1.0**, y sincronización documental
> completa. **No** se ejecuta commit unitario ni merge (instrucción
> explícita del orquestador). **Evidencia medida (2026-09-01):**
> `PresupuestoValidacionResourceIT` 13/13; `./gradlew test --tests
> 'ec.uce.propuestas.presupuesto.*'` 98/98; APU 52/52;
> `recalculo` 4/4; motor 45 = 42 pass + 2 aceptados (GM-19 `-$6.95`,
> GM-20 cap. 1 `-$0.84`) + 1 skipped (GM-24 `@Disabled`) + 0 errors;
> suite completa **438 = 435 pass + 2 aceptados + 1 skipped + 0
> errors**; `./gradlew spotlessCheck` PASS; `./gradlew build -x test`
> PASS; `git diff --check` limpio; `graphify update .` final
> **DONE — 2.972 nodos / 9.064 aristas / 141 comunidades**. Sin bloqueos correctivos identificados en
> los planes 019–024; las dos observaciones de auditoría no son defectos
> (`TRUNCATE ... CASCADE` cubre reset `cronograma`/`actividad`; D-09
> significa códigos APU únicos por presupuesto). Tras cierre Plan 025,
> la **siguiente tarea de planificación** es **Plan 026 (I-08 —
> cronograma CRUD)**, que aún **no** existe como archivo ejecutable y
> debe autorarse en su propia sesión.

---

## 2. Regla de precedencia de decisiones

Hay documentos de `thesis-docs` que todavía describen APUs auxiliares enlazados,
pero esa descripción quedó superada.

### 2.1 Decisión final: no existen enlaces entre APUs

Fuente más reciente y específica:

- `../thesis-docs/DOCUMENTOS/entrevistas/04/temporal/Respuesta_Entrevista_N04_TERMPORAL.md`
  §2: **“NO EXISTEN ENLACES EN APUS AUXILIALES”**. Si un caso necesita un
  supuesto “rubro auxiliar”, se crea otro rubro/APU ordinario independiente.
- Decisión explícita del autor durante la alineación backend: no implementar
  `es_auxiliar`, `apu_auxiliar_id`, `cdAuxiliar`, propagación entre APUs ni
  `CAMBIO_AUXILIAR`.

Por tanto:

- no agregar `apu.es_auxiliar`;
- no agregar `apu_detalle.apu_auxiliar_id`;
- no crear `ApuValidacionService` para anidamiento;
- no crear endpoints o DTOs con `apuAuxiliarId`;
- no mantener ramas auxiliares dentro del motor;
- un APU puede contener cualquier subconjunto de EQUIPO, MANO_OBRA, MATERIAL y
  TRANSPORTE, incluso una sola sección.

### 2.2 Documentos globales que siguen desactualizados

Deben reconciliarse con la decisión final de no-links:

- `../thesis-docs/README.md`;
- `../thesis-docs/plan/roadmap/01-plan-iteraciones-xp.md`;
- `../thesis-docs/plan/architecture/06-database-schema.md`;
- `../thesis-docs/plan/architecture/07-api-contract.md`;
- `../thesis-docs/plan/architecture/08-codebase-design.md`;
- `../thesis-docs/plan/domain/02-data-model.md`;
- `../thesis-docs/plan/design/03-procesos-detalle.md`;
- `../thesis-docs/plan/design/07-decisiones-i06-pendientes.md`;
- `../thesis-docs/plan/quality/02-catalogo-pruebas.md`;
- `../thesis-docs/plan/quality/03-trazabilidad.md`;
- `../thesis-docs/plan/quality/04-poblamiento-bd.md`;
- `../thesis-docs/DOCUMENTOS/requerimientos/v1.1-functional-requirements.md`;
- `../thesis-docs/DOCUMENTOS/requerimientos/v1.2-functional-requirements.md`;
- `docs/modulos/04-apu-avanzado.md` de este backend.

Los casos `TC-P25-*` ligados a referencias auxiliares deben marcarse como
**obsoletos/superseded**, no implementarse.

### 2.3 Otras decisiones globales vigentes

| Tema | Decisión vigente | Fuente principal |
|---|---|---|
| Precisión del motor | **`BigDecimal` natural** (Plan 014 supersede 2026-08-28; `CALC_PRECISION=3 HALF_UP` retirado) | `plans/014-motor-precision-no-links.md`, `thesis-docs/CLAUDE.md`, `plan/domain/02-data-model.md` §0/§16/§17 #19 |
| Consolidación APU→Rubro | precio unitario y total a 2 dp con `DOWN` | `plan/design/07-decisiones-i06-pendientes.md` N04-bis; backend `plans/006` |
| Presentación | `DISPLAY_PRECISION=2`, solo UI/export | `plan/domain/02-data-model.md`; `plan/design/04-export-sercop-spec.md` |
| Persistencia | `NUMERIC(14,6)` | `plan/architecture/06-database-schema.md` |
| Bases | CENTRAL/PERSONAL siempre se copian a PROYECTO | `plan/design/07-decisiones-i06-pendientes.md` §A9 |
| D-12 | archivar central; después puede borrarse sin bloqueo | `plan/design/03-procesos-detalle.md` §J D-12 |
| ET | un DOCX por proyecto, títulos personalizables | `plan/design/03-procesos-detalle.md` P-45 |
| Librería documental | Apache POI en backend | `plan/README.md`, `plan/backend/01-quarkus-backend.md` |

---

## 3. Estado Git que debe resolverse primero

> **Actualización 2026-08-28:** el bloque descrito a continuación
> (`ParametrosProyectoCambio` + ajustes en `01-proyecto.md`, `Resource` y
> `Service`) ya está **commiteado en `main`** y constituye la costura
> neutral de WU-06. Este §3 se conserva como checklist histórico;
> mantenlo en este documento mientras siga siendo la lista de fuentes
> pendientes de alinear.

Histórico (corte auditado previo a la reconciliación):

En el corte auditado, `main` estaba limpio hasta `9ccb25c`, pero conservaba
un bloque sin commit:

```text
M  docs/modulos/01-proyecto.md
M  src/main/java/ec/uce/propuestas/proyecto/resource/ParametrosProyectoResource.java
M  src/main/java/ec/uce/propuestas/proyecto/service/ParametrosProyectoService.java
?? src/main/java/ec/uce/propuestas/proyecto/service/ParametrosProyectoCambio.java
?? src/test/java/ec/uce/propuestas/proyecto/service/ParametrosProyectoCambioTest.java
```

Este bloque implementa la costura neutral de WU-06:

```java
ParametrosProyectoCambio(
    Long proyectoId,
    boolean porcentajeIndirectoCambio,
    boolean porcentajeHerramientaMenorCambio,
    ParametrosProyectoResponse parametros)
```

**Acción ejecutada:**

- Bloque commiteado como `feat(proyecto): expose parameter change seam`.
- `ParametrosProyectoResource` devuelve únicamente `cambio.parametros()`
  (`ParametrosProyectoResponse`); los flags internos y el `Long proyectoId`
  **no** se exponen por REST.
- La migración del `proyectoId` en path y de
  `ParametrosProyectoResponse.id` a UUIDv7 quedó **cerrada por Plan 07
  (DONE 2026-08-30 · VERIFICACIÓN DIRIGIDA COMPLETA)**; ver
  [`planes-para-estar-al-dia/07-uuidv7-fronteras-rest.md`](planes-para-estar-al-dia/07-uuidv7-fronteras-rest.md)
  para la matriz completa de fronteras.

Si en el futuro se decide revertir la costura, hay que revertir
exactamente esos cinco paths; no mezclar esa decisión con los bloques
siguientes.

---

## 4. Matriz de capacidades

### Leyenda

- **DONE:** comportamiento y prueba principal existentes.
- **PARTIAL:** existe una parte útil, pero el contrato no está cerrado.
- **MISSING:** no existe implementación funcional.
- **DEFERRED:** exige un módulo nuevo o una iteración fuera del alcance actual.

| Capacidad | Estado | Evidencia actual | Falta exacta |
|---|---|---|---|
| P-23 `%CI` por APU | **PARTIAL** | `ApuResource` y `ApuCrudService.actualizarPorcentajeIndirecto` | propagación cuando cambia el default del proyecto; depende de write-through global |
| P-24 descuento legacy por APU | **WITHDRAWN — Plan 015 (2026-09-01)** | seam retirado: `Apu.porcentajeDescuento`, `motor.ParametrosCalculo.porcentajeDescuento`, `motor.ApuCalculado.costoDirectoAjustado`, `ApuResponse.porcentajeDescuento`, `ApuCalculoParametros.descuento`, `ApuCalculoResumen.cdAjustado`/`operacionCdAjustado`, `PATCH /apus/{id}/porcentaje-descuento` | sobreviven **FORMA 1** (mutación de insumos PROYECTO, MO exenta, regulada por `parametros_sistema.rango_descuento_min/max`) y **FORMA 2** (edición atómica de insumo PROYECTO, sin seam nuevo). Columna BD `apu.porcentaje_descuento` queda como compatibility seam inert. |
| Descuento FORMA 1 global | **DEFERRED** | tablas estructurales de snapshot presentes | servicio de presupuesto + recálculo transaccional |
| Descuento FORMA 2 | **PARTIAL** | `PUT` de insumos PROYECTO existente | recalcular APUs que heredan el precio; debounce pertenece al frontend |
| P-25 enlaces auxiliares | **OBSOLETO** | schema/entities actuales correctamente no los tienen | eliminar referencias antiguas de docs y motor; no crear columnas/endpoints |
| P-26 plantillas de APU | **DONE 2026-08-29 (Plan 04)** | `PlantillaApuService`, `PlantillaApuResource`, `PlantillaApuGuardarResource`, DTOs, `SnapshotApuMapper` (price-free writer + lenient reader), `ResolverInsumoPlantillaService` (PROYECTO→CENTRAL→PERSONAL→pendiente), integración en `ApuCrudService.crear` (`plantillaId` opcional), `V005__allow_zero_pending_apu_detail_prices.sql` (relax estructural `>= 0`, no reseed). Tests verdes: `ec.uce.propuestas.plantilla.*` **34/34** (SnapshotApuMapperTest 5/5 + PlantillaApuResourceIT 12/12 + ApuCalculoServiceNullableInsumoTest 1/1 + PlantillaApuServiceTest 16/16). Regresión dirigida adyacente **47/47** verde (ApuResourceIT 36/36 + ApuCalculoServiceIT 2/2 + ResolverInsumoProyectoTest 9/9). HTTP 201 sin advertencias / 200 con `advertencias[]` no vacío. Snapshot nunca persiste precios efectivos, IDs de insumo ni links al APU origen; fila pendiente se distingue de insumo real con `precio_unitario = 0` por `insumo_id IS NULL` + `advertencias[]`. **No** se reporta suite completa. | P-46 quedó cerrado por Plan 06; nada pendiente para P-26. |
| P-27 desglose de cálculo | **DONE** (Plan 03, 2026-08-28) | DTOs, `ApuCalculoService.proyectar`, `GET /calculo` con lineas ordenadas por `orden` (sin HM-primero) y resultado a 6 dp; TC-P27-01..04 verdes | display layer aplica `precisionDinero` / `precisionPorcentaje` desde config global — presentación, no motor |
| Duplicar APU | **DONE** | `ApuDuplicarService`, `POST /duplicar` | comprobar que no reaparezca vocabulario auxiliar |
| P-45 ET por APU | **DONE** | GET/PUT ET, `DocumentoResource`, `EspecificacionesTecnicasService` | solo sincronizar docs: usa Apache POI, no docx4j |
| P-46 plantilla de proyecto | **DONE (Plan 06, 2026-08-29)** | `PlantillaProyectoService` + `SnapshotProyectoMapper` (writer price-free/structural + reader tolerante V004) + `SnapshotCabecera` reutilizable; recursos `GET/DELETE /plantillas-proyecto`, `POST /proyectos/{proyectoId}/guardar-plantilla`, `POST /proyectos/desde-plantilla/{plantillaId}`; defaults de `ParametrosSistema`; V006/V007. Verificación principal 83/83 verde, build verde y diff limpio; regresiones APU 41/41, insumo 45/45, identifier 10/10. | nada dentro de Plan 06; suite completa no ejecutada y Spotless global conserva 31 violaciones preexistentes ajenas |
| A3 reordenamiento | **DONE** (Plan 03, 2026-08-28) | `ApuDetallePatchRequest.orden`, `ApuCrudService.reordenarEnSeccion` (MOVE atómico), HM order-only, `ApuCalculoService.buildSecciones` ordena por `orden` ascendente | nada; ver `planes-para-estar-al-dia/03-contrato-apu-actual.md` |
| A6 rangos globales | **DONE** | columnas, GET/PUT admin, validación dinámica, `ParametrosRangoDinamicoTest`, `ParametrosProyectoCambio` + test commitados | nada (la costura neutral ya está expuesta para futura propagación) |
| A9 base PERSONAL | **DONE** | `BasesPersonalesService/Resource` | DELETE personal opcional indicado en Plan 04 |
| A9 copia al usar | **DONE** | `ResolverInsumoProyectoService`, integración en `ApuCrudService` | nada para creación de filas; reutilizarlo desde plantillas |
| D-12 central | **DONE 2026-08-29 (Plan 05)** | `AdminBaseCentralResource` (P-39) bajo `/admin/bases-centrales`; `POST /archivar` + `DELETE` con 409 si activa; copia PROYECTO preservada | nada dentro de Plan 05; el catálogo `/bases-centrales` sigue ocultando archivadas |
| Precisión del motor | **DONE 2026-08-28 (Plan 02 + Plan 014 T1/T3/T4)** | motor con `BigDecimal` natural (Plan 014 supersede #7); única rounding del motor en frontera APU→Rubro (workbook-consistent); display global `app.display.*` + `GET /api/v1/config/display`; `@Digits` en catálogo cerrado | presentación aplica `precisionDinero`/`precisionPorcentaje`; residual GM-19/GM-20 aceptado |
| Consolidación GM-19/20 | **PARTIAL — CIERRE CON RESIDUO ACEPTADO (2026-08-28)** | regla workbook-consistent aplicada en `Consolidador.java` (`PU DOWN 2dp`; `PT = cantidad × PU_2dp` retenido a escala 6 `HALF_UP`); `ConsolidadorFronteraTest` 5/5 verde | residual aceptado: GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84` (no se reabre el motor); T2–T4 de [`plans/014`](../../plans/014-motor-precision-no-links.md) **IMPLEMENTADOS** (2026-08-28): `SnapshotSinAuxiliaresTest` 6/6 + `DisplayConfigResourceTest` 1/1 + `DisplayConfigResourceOverrideTest` 1/1 + `DigitsValidationCatalogTest` 3/3; DIAG borrado. |
| UUIDv7 en APU | **DONE** | paths APU y detalle usan UUIDv7 | nada |
| UUIDv7 resto de módulos | **DONE (2026-08-30) · VERIFICACIÓN DIRIGIDA COMPLETA** | `proyecto/firmante/parametros_proyecto`, `insumo/base_insumos` (admin central y bases personales), `PresupuestoApuResource` (con parse de `plantillaId` en frontera), seam `POST /proyectos/{proyectoId}/guardar-plantilla` y `DocumentoResource` (ET) migrados en Plan 07; APU/detalle y `plantillas-apu` (P-26) ya estaban alineados; `ParametrosProyectoResponse.proyectoId` y `InsumoUsoResponse.apuId` migrados a `UUID`; `CopiarBaseRequest` con `UUID baseId`/`UUID proyectoId`. Sin migraciones nuevas (no V008/V009); PK/FK siguen `BIGINT`; motor intacto | suites dirigidas verdes; suite completa y Bruno se reservan al [Plan 08](./planes-para-estar-al-dia/08-cierre-documental-y-verificacion.md) |
| Write-through global | **DEFERRED** | costura `ParametrosProyectoCambio` ya commiteada y neutral | módulo profundo `recalculo`, excluido por alcance actual |

---

## 5. Archivos ya alineados que no deben rehacerse

### Seeds e identidad

- `V001__baseline.sql`: schema reconstruido con BIGINT interno y UUIDv7 público.
- `V002__seed.sql`, `V003__seed_insumos.sql`, `V004__seed_escenarios.sql`:
  IDs explícitos y sin compatibilidad temporal.
- `RepresentativeSeedsIT`: contrato de fuente y rebuild determinista.
- `PublicIdPersistenceTest`: persistencia/immutabilidad UUIDv7.

### APU

- `%CI` y (WITHDRAWN — Plan 015, 2026-09-01) descuento legacy por APU; sobreviven FORMA 1 + FORMA 2 (ver [`../../plans/015-retirar-descuento-apu.md`](../../plans/015-retirar-descuento-apu.md)).
- duplicación profunda;
- ET;
- cálculo/desglose;
- IDs públicos en recursos APU/detalle;
- copia al usar para insumos CENTRAL/PERSONAL.

### Proyecto/insumo/documento

- rangos dinámicos;
- bases PERSONALES;
- materialización PROYECTO;
- exportación DOCX de ET con Apache POI.

---

## 6. Plan de ejecución directa por bloques

No se usan fases SDD. Cada bloque se aplica directamente a `main` y termina en
un commit independiente. Las pruebas se acumulan y se ejecutan al final.

### Bloque 0 — línea base y fuente de verdad reconciliadas

**Estado:** aplicado en el árbol de trabajo; pendiente de revisión y commit por el
usuario.

Resultados:

1. `ParametrosProyectoCambio` ya estaba terminado y commiteado antes de este
   bloque; su seam permanece neutral y el write-through global está diferido;
2. `docs/modulos/04-apu-avanzado.md` quedó reconciliado:
   - P-25 auxiliar, `ApuValidacionService`, `CAMBIO_AUXILIAR`, `es_auxiliar`,
     `apu_auxiliar_id` y `cdAuxiliar` están marcados como obsoletos/prohibidos;
   - Apache POI sustituye cualquier instrucción previa de usar docx4j;
   - lo implementado está marcado DONE y `recalculo` queda diferido;
3. los documentos globales enumerados en §2.2 se sincronizaron con la nueva
   versión acumulativa `v1.3-functional-requirements.md`;
4. `plans/README.md` refleja:
   - Plan 013 en estado **PARTIAL** (P-26 y P-46 cerrados 2026-08-29 por Planes 04 y 06; write-through global, FORMA 1, FORMA 2 siguen pendientes; UUIDv7 del resto de módulos cerrado por Plan 07 **DONE 2026-08-30 · VERIFICACIÓN DIRIGIDA COMPLETA**);
   - Plan 006 como "decisión cerrada, código aún pendiente" hasta aplicar DOWN;
   - Plan 07 con la fila 017 y el detalle de fronteras migradas / seams ya alineados (sin migraciones nuevas).

Commit sugerido:

```text
feat(proyecto): expose parameter change seam
```

La reconciliación documental puede ir en un segundo commit:

```text
docs(i06): reconcile advanced APU plan with no-links decision
```

### Bloque 1 — motor: precisión, consolidación y limpieza no-links

**Objetivo:** cerrar la parte matemática de I-02/I-06 sin introducir un módulo
nuevo.

Antes de editar, crear el plan obligatorio:

```text
plans/014-motor-precision-no-links.md
```

> **Estado (cierre parcial 2026-08-28; supersede Plan 08 — 2026-08-30):** items 8 (regla workbook-consistent en `internal/Consolidador.java`) y 11 parcial (auditoría GM-21, fixtures) ejecutados; residual aceptado: GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84`. T1 (consolidación), T2 (no-links estructural en `Motor.java`/`CalculadorFila.java`: `SnapshotSinAuxiliaresTest` 6/6), T3 (`DisplayConfig` + `GET /api/v1/config/display`: `DisplayConfigResourceTest` 1/1 + `DisplayConfigResourceOverrideTest` 1/1) y T4 (`@Digits(integer=8, fraction=2)` en los tres campos del catálogo cerrado: `DigitsValidationCatalogTest` 3/3) **IMPLEMENTADOS 2026-08-28**. Borrado de DIAG **DONE 2026-08-28**. El motor **no** se reabre para cerrar el residual.

Actualizar también:

- `CLAUDE.md` del backend;
- `../thesis-docs/CLAUDE.md`.

Cambios (Plan 014, 2026-08-28 — supersede N04 §#7):

1. `common/config/DisplayConfig.java` *(nuevo)*:
   - `@ConfigMapping(prefix = "app.display")`;
   - `precision` default 2 (env `DISPLAY_PRECISION`);
   - `precision-porcentaje` default 4 (env `DISPLAY_PRECISION_PORCENTAJE`);
   - vive en `common/config/`, **nunca** en `motor/`;
2. `common/config/DisplayConfigResponse.java` *(nuevo)* y
   `common/config/DisplayConfigResource.java` *(nuevo)*:
   - `GET /api/v1/config/display` (`@PermitAll`) → `{precisionDinero, precisionPorcentaje}`;
3. `application.yml` + `.env.example`:
   - bloque `app.display.precision` / `app.display.precision-porcentaje`;
   - variables `DISPLAY_PRECISION=2` y `DISPLAY_PRECISION_PORCENTAJE=4`;
4. `motor/ParametrosCalculo.java`:
   - quitar `porcentajeIndirectoApu`;
   - retiene solo `porcentajeIndirectoDefault` (default del proyecto);
   - el override por APU vive en `ApuSnapshot.porcentajeIndirecto` (nullable);
5. `motor/ApuSnapshot.java` y `ApuCalculado.java`:
   - quitar `esAuxiliar`;
   - representar solamente un APU ordinario;
   - `ApuSnapshot.porcentajeIndirecto` queda nullable (override semántico por APU; null = hereda);
6. `motor/FilaSnapshot.java`:
   - quitar `cdAuxiliar`;
7. `motor/Motor.java` y `motor/internal/CalculadorFila.java`:
   - **edición estructural mínima autorizada** (Plan 014): quitar la rama
     `esAuxiliar`, leer el `%CI` desde `ApuSnapshot.porcentajeIndirecto`,
     ajustar el constructor de `ApuCalculado`, y quitar el fallback
     `cdAuxiliar` en `calcularMaterial`;
   - **sin cambio aritmético**: fórmulas, `MathContext`, orden de
     operaciones y precisión natural de `BigDecimal` intactos;
8. `motor/internal/Consolidador.java` (regla **workbook-consistent**,
   corrección 2026-08-28):
   - `precioUnitario = costoTotal.setScale(2, DOWN)` (única aplicación
     de `DOWN`; reproduce el workbook IESS);
   - `precioTotal = cantidad × precioUnitario, setScale(6, HALF_UP)`
     (retenido a la escala de persistencia 6 `NUMERIC(14,6)` con
     `HALF_UP` **únicamente** en esa frontera de resultado; **no** se
     trunca cada `precioTotal` a 2 dp — la versión previa con
     `setScale(2, DOWN)` simétrico quedaba retirada por deltas
     sistemáticos `GM19 = -$9.37` y `GM20 cap1 = -$3.09` vs workbook
     IESS);
   - totales de capítulo y `totalGeneral` agregados desde esos
     `precioTotal` a escala 6;
   - display/assertion canónico a 2 dp `HALF_UP` ocurre solo en la
     capa de presentación;
   - **única rounding del motor**;
9. `apu/service/ApuCalculoService.java` y los fixtures/tests del motor:
   - ajuste de call sites para compilar; **sin tocar assertions ni expected values**;
10. DTOs monetarios de entrada existentes en `HEAD`
    (`ApuDetallePatchRequest.precioOverride`, `InsumoCrearRequest.precioUnitario`,
    `InsumoEditarRequest.precioUnitario`):
    - `@Digits(integer=8, fraction=2)` solo en estos campos (`integer=8` por
      `NUMERIC(14,6)`);
    - nunca en cantidades, rendimientos, porcentajes, resultados calculados
      ni campos de entidad (`tarifaJornal`, `precioUnitarioTarifa`);
    - `ApuDetalleCrearRequest.precioOverride` **no existe** en `HEAD`; no se
      crea (STOP condition de `plans/014`);
11. borrar `DIAG_rubro_expected_vs_actual` del `MotorConsolidacionTest`;
    auditar GM-21 y borrar entradas del allowlist donde `delta == 0.00`
    (cero entradas restantes es válido);
    GM-24 sigue `@Disabled` por la rotura upstream del fixture EMELNORTE.

Commit sugerido:

```text
feat(motor): apply calculation and consolidation precision
```

### Bloque 2 — cerrar el contrato APU actual

**Objetivo:** completar P-23/P-27/A3 usando únicamente `apu`.

Cambios:

1. `ApuDetallePatchRequest`:
   - agregar `JsonNullable<Integer> orden`;
2. `ApuCrudService.editarDetalle`:
   - persistir el nuevo orden;
   - mantener la fila HM protegida contra borrado, pero permitir reordenarla;
3. `ApuCalculoService`:
   - resultados monetarios proyectados a `precisionDinero` (display 2 dp) desde config global;
   - operandos de `operacion` a precisión completa;
   - respetar orden persistido;
4. revisar `ApuCalculoResponse`, `ApuCalculoLinea` y `ApuCalculoResumen` para no
   exponer BIGINT ni valores auxiliares;
5. añadir/ajustar pruebas:
   - TC-P23 set/clear;
   - TC-P27-01 shape;
   - TC-P27-02 reordenamiento;
   - TC-DECIMALES-CALC3-DISP2 API.

Commit sugerido:

```text
feat(apu): finish calculation response and row ordering
```

### Bloque 3 — plantillas de APU P-26 (DONE 2026-08-29)

**Objetivo:** convertir `plantilla` de una costura estructural a una capacidad
usable, sin crear otro módulo.

Archivos principales:

- nuevos DTOs bajo `plantilla/dto/`;
- `plantilla/service/PlantillaApuService.java`;
- `plantilla/resource/PlantillaApuResource.java`;
- `ApuCrearRequest`;
- `PresupuestoApuResource`;
- `ApuCrudService`;
- `ResolverInsumoProyectoService`.

Comportamiento:

1. listar SISTEMA + PERSONALES propias;
2. obtener, renombrar y eliminar una PERSONAL propia;
3. guardar un APU como plantilla PERSONAL;
4. snapshot JSONB **totalmente price-free**: sólo códigos (`insumoCodigo`),
   cantidades, rendimientos y fila HM. El writer **nunca** emite precios
   efectivos ni overrides explícitos del APU origen; el reader **tolera**
   silenciosamente los campos extra del seed V004 (`tarifaJornal`, `costo`,
   `orden`, `descripcion`, `seccionTipo`, `tipoInsumo`, `publicId`);
5. cargar plantilla al crear APU;
6. resolver cada código:
   - existe en PROYECTO → reutilizar;
   - existe en CENTRAL/PERSONAL visible (no archivada, tipo compatible) →
     copiar a PROYECTO vía `ResolverInsumoProyectoService`;
   - no existe → **fila pendiente** con `insumo_id = NULL` + override `0`
     explícito en la columna de la sección (EQUIPO/MANO_OBRA →
     `tarifaJornal`; MATERIAL/TRANSPORTE → `precioUnitarioTarifa`) +
     `advertencias[]` en respuesta HTTP `200`. El caso se distingue del
     insumo real con `precio_unitario = 0` (catálogo-backed, `insumo_id`
     poblado, HTTP `201` sin advertencias);
7. tolerar los campos extra del seed V004; **V005 es estructural** (relaja
   `CHECK (> 0)` → `CHECK (>= 0)` en `apu_detalle.tarifa_jornal` y
   `apu_detalle.precio_unitario_tarifa`), **no** un reseed del JSONB.

Endpoints:

```text
GET    /plantillas-apu
GET    /plantillas-apu/{id}
PUT    /plantillas-apu/{id}
DELETE /plantillas-apu/{id}
POST   /apus/{id}/guardar-plantilla
POST   /presupuestos/{id}/apus  { plantillaId }
```

Commit sugerido:

```text
feat(plantilla): add reusable APU templates with fallback
```

### Bloque 4 — completar administración de bases actuales

**Objetivo:** cerrar A9/D-12 dentro de `insumo`.

Cambios:

1. bases personales:
   - `DELETE /bases-personales/{id}` con owner-to-404;
2. administración central bajo `/admin/bases-centrales`:
   - listar/crear/renombrar;
   - CRUD de insumos centrales;
   - importar CSV;
   - `PUT /admin/bases-centrales/{id}/archivar`;
   - `DELETE /admin/bases-centrales/{id}` solo después de archivar;
3. archivar oculta del catálogo;
4. borrar no comprueba referencias de proyectos, porque esos proyectos usan
   copias PROYECTO;
5. mantener dos roles únicamente: USUARIO y SUPER_ADMIN.

Commit sugerido:

```text
feat(insumo): complete personal and central base administration
```

### Bloque 5 — plantillas de proyecto P-46 en paquetes existentes

**Objetivo:** completar la decisión N04 más reciente sin crear un módulo nuevo.

Usar exclusivamente:

- `plantilla`;
- `proyecto`;
- `presupuesto`;
- `apu`;
- `insumo`.

Cambios:

1. `PlantillaProyectoService` y resource;
2. guardar snapshot estructural sin precios ni cantidades de obra;
3. recrear proyecto, parámetros, capítulos, rubros/APUs y estructura disponible;
4. resolver insumos con el mismo fallback de P-26;
5. advertir faltantes, nunca enlazar el nuevo proyecto con el original;
6. no copiar cronograma, historial, firmantes ni datos operativos fuera del
   snapshot aprobado.

Endpoints:

```text
GET    /plantillas-proyecto
GET    /plantillas-proyecto/{id}
DELETE /plantillas-proyecto/{id}
POST   /proyectos/{proyectoId}/guardar-plantilla
POST   /proyectos/desde-plantilla/{plantillaId}
```

> **DONE 2026-08-29 (Plan 06).** Ejecutado dentro de módulos existentes y
> reutilizando el parser/fallback de P-26; verificación principal 83/83 verde.

Commit sugerido:

```text
feat(plantilla): add project templates from existing aggregates
```

### Bloque 6 — alinear UUIDv7 en todas las fronteras actuales

**Objetivo:** que ningún resource actual exponga o acepte BIGINT internos.

**Estado (2026-08-29):** **DONE · VERIFICACIÓN DIRIGIDA COMPLETA**
(ver [Plan 07](./planes-para-estar-al-dia/07-uuidv7-fronteras-rest.md)). La
matriz de endpoints, DTOs migrados y seams ya alineados están en el doc del
plan; resumen aquí:

Recursos migrados en este pase:

- `proyecto/firmante/parametros_proyecto` — `UuidV7.parse` en path y parseo
  de `proyectoId`/`firmanteId` UUIDv7;
- `insumo/base_insumos` (admin central + bases personales) — `UuidV7.parse`
  en path; `InsumoUsoResponse.apuId` y `CopiarBaseRequest` (`UUID baseId`,
  `UUID proyectoId`) migrados a `UUID`;
- `PresupuestoApuResource` — `UuidV7.parse(presupuestoId)` + parse de
  `ApuCrearRequest.plantillaId` en frontera (`normalizarPlantillaId`);
- seam `POST /proyectos/{proyectoId}/guardar-plantilla` y
  `POST /proyectos/desde-plantilla/{plantillaId}` — `UuidV7.parse` en path;
- `DocumentoResource` (ET) — `UuidV7.parse(presupuestoId)` + owner-to-404
  sobre presupuesto y proyecto.

Recursos **ya alineados** antes de este plan (no modificados):

- APU y detalle (`apu/resource/ApuResource.java`);
- `plantillas-apu` (P-26) y `ApuDetalleResponse.insumoId` UUIDv7.

Reglas aplicadas (sin cambios):

1. paths públicos reciben UUIDv7 como `String` y validan mediante
   `UuidV7.parse` (regex v7 + `UUID.version() == 7` + variant `2`);
2. repositories resuelven UUID + owner a BIGINT una sola vez por frontera de
   service;
3. JSON usa el nombre semántico `id`, nunca `public_id` ni `publicId`;
4. owner ajeno → 404 `no-encontrado` (RNF-05; nunca 403);
5. UUID malformado/no-v7 → 400 `validacion`;
6. PK/FK y joins internos siguen siendo `BIGINT` (V001 §1; sin
   migraciones nuevas en este plan — no se creó V008/V009 y no se reabre
   V001–V007);
7. motor intacto (`BigDecimal` natural; sin cambios en `Motor.java` ni en
   `internal/Consolidador.java`).

**Sin migraciones nuevas:** la columna `public_id` (UUID con
`DEFAULT uuidv7()`) ya existía en V001 para `usuario`, `proyecto`,
`firmante`, `presupuesto`, `apu`, `apu_detalle`, `base_insumos`, `insumo`,
`plantilla_apu`, `plantilla_proyecto`, con trigger de inmutabilidad. No se
creó V008/V009 ni se editó V001–V007.

**Verificación PENDING:** las suites Gradle
(`./gradlew test --tests 'ec.uce.propuestas.{proyecto,insumo,apu,plantilla,
documento}.*'`, `./gradlew build -x test`, `./gradlew spotlessCheck`) y la
búsqueda acotada de IDs `Long` en DTOs públicos / `@PathParam` quedan
reservadas para el cierre del
[Plan 08](./planes-para-estar-al-dia/08-cierre-documental-y-verificacion.md); verificación transversal completada.

Commit sugerido (no emitido por este pase):

```text
refactor(api): finish public UUID boundaries
```

### Bloque 7 — documentación, Bruno y cierre

Actualizar:

- `docs/modulos/01-proyecto.md`;
- `docs/modulos/02-insumo.md`;
- `docs/modulos/03-apu.md`;
- `docs/modulos/04-apu-avanzado.md`;
- `docs/modulos/README.md`;
- `docs/00-ESTADO-ACTUAL.md`;
- `plans/README.md`;
- colección `api/bruno/09-i02-i06/` con UUIDv7 públicos.

Casos mínimos Bruno:

- rangos configurables;
- PERSONAL y copia CENTRAL;
- plantillas APU y fallback;
- reordenamiento;
- cálculo a 3 dp;
- ET y títulos;
- archivar/borrar central;
- UUID inválido/ajeno.

Commit sugerido:

```text
docs(i06): reconcile backend modules and API examples
```

> **Cierre de Plan 04 (2026-08-29):** `docs/modulos/planes-para-estar-al-dia/04-plantillas-apu.md`,
> `docs/modulos/README.md`, `docs/modulos/estado-actual.md` (este doc) y
> `plans/README.md` quedaron alineados al comportamiento implementado
> (snapshot totalmente price-free; fila pendiente con `insumo_id = NULL` +
> override `0`; distinción pendiente vs insumo real con `precio_unitario = 0`;
> V005 estructural — no reseed; `ec.uce.propuestas.plantilla.*` 34/34 +
> regresión dirigida 47/47). Los canónicos activos de `thesis-docs` (06-schema,
> 07-API, 02-data-model, 03-procesos-detalle, 02-catalogo-pruebas) se
> actualizaron en la misma pasada. Plan 013 sigue PARTIAL en su conjunto
> (write-through global, FORMA 1 y FORMA 2 permanecen pendientes).
>
> **Cierre de Plan 07 — UUIDv7 en fronteras REST (2026-08-30; DONE):**
> se migraron `proyecto/firmante/parametros_proyecto`, `insumo/base_insumos`
> (incluidas bases personales y administración central), la frontera
> `PresupuestoApuResource`, guardar plantilla de proyecto y `DocumentoResource`.
> APU/detalle y plantillas ya tenían UUIDv7 para sus IDs propios; Plan 07 cerró
> además la referencia anidada `insumoId`. No hubo migraciones: V007 sigue
> siendo la última y PK/FK permanecen `BIGINT`. Verificación dirigida:
> **233/233 tests disponibles verdes**; `presupuesto.*` aún no contiene tests.
> `git diff --check` y el build sin el gate Spotless están verdes. El build
> exacto permanece bloqueado solo por 23 archivos con formato preexistente,
> ninguno modificado por Plan 07. Suite completa y Bruno cerrados en Plan 08.

### Bloque 8 — I-11 Panel Super-Admin (gate documental cerrado)

> **Corte (2026-09-07):** I-11 está **PLANNED** con el gate documental
> **Plan 032 DONE**. La ejecución de los planes 033–040 queda
> pendiente y se autoriza en su propia sesión tras 032. Este bloque
> **no reescribe** las secciones históricas; anota el estado I-11
> para que la matriz de capacidades y el DAG sigan siendo
> revisables sin contradicción.

**Estado de I-11:**

- **Plan 032 (Sincronizar contrato e inventario admin) — DONE
  (2026-09-07).** Acta firmada en
  [`docs/modulos/panel-admin/00-acta-reconciliacion.md`](panel-admin/00-acta-reconciliacion.md);
  inventario operativo en
  [`docs/modulos/panel-admin/00-inventario-trabajo.md`](panel-admin/00-inventario-trabajo.md).
  **21 decisiones locked verbatim** (20 originales + adenda firmada D-21);
  **15 STOP conditions** con disposición explícita (10 CLOSED,
  4 DEFERRED a I-12, 1 CLOSED con gate RED-first en 035);
  catálogo D-13 verbatim (26 eventos); matriz
  canónica `evento → detalle`; paridad P-38…P-42 contra la
  implementación; hechos numéricos V004 (19/6/4); 4 seeds
  `valor_referencia` (SBU, APORTE_PATRONAL, FAS,
  HORAS_OPERACION_ANUAL); sin claims de commit; sin claims de
  suite completa.
- **Planes 033–040 — PLANNED / TODO.** 033 es la **siguiente tarea
  autorizada** tras 032. Detalle por plan en el inventario.
- **Cambios de Plan 031 — preservados intactos** (sin commit; fuera
  del alcance de 032). 039 los reusa tal cual al instrumentar
  `documento.exportado`.

**Decisiones del usuario registradas en el acta (2026-09-07):**

1. **DELETE base central activa:** el comportamiento actual (409
   `base-no-archivada`) es **canónico**; no se invierte. 035 no
   modifica este camino.
2. **Plan 037 introduce `ParametrosSistemaResponse`**; el `GET
   /proyectos/parametros-sistema` deja de exponer la entidad JPA.
3. **Self-delete / last-active SUPER_ADMIN / cambio de email admin
   / primer SUPER_ADMIN bootstrap** se difieren a **I-12**. 034 no
   codifica estas matrices.
4. **P-09 duplicación de proyecto** permanece diferida según
   decisión histórica N02 §3. El evento `proyecto.duplicado` se
   mantiene en el enum D-13 pero **no** tiene productor runtime
   (`STOP-032-P09-DUPLICAR` cerrado con disposición "no producer
   yet"). 038 no implementa seam; 040 no fabrica test skipped.
5. **DELETE insumo central** se cierra con **409 `insumo-en-uso`**,
   reemplazando el stub `conteoUsosApu() = 0L` y el mapping 400
   `validacion` actual. Plan 035 debe entregar RED-first (test
   rojo previo que reproduce 400 → luego mapeo a 409).

**Hechos verificables (sin totales de suite futura):**

| Hecho | Valor | Fuente |
|---|---|---|
| Highest migration | V009 | `V009__cronograma_persistencia.sql` |
| Próxima migración libre | V010 (en ejecución 033) | D-01 del acta |
| `log_actividad` filas V004 | 19 | V004 §11 |
| `log_actividad` filas legacy V004 | 6 (4 nombres no canónicos) | V004 §11 |
| Nombres legacy no canónicos | 4 | `base.insumos.copiada`, `rubro.creado`, `cronograma.creado`, `presupuesto.vigente_marcado` |
| Seeds `valor_referencia` | 4 | SBU, APORTE_PATRONAL, FAS, HORAS_OPERACION_ANUAL |
| Eventos catálogo D-13 | 26 | D-18 del acta |
| Forma `Page<T>` canónica | `items,total,page,size,totalPaginas` | `common/dto/Page.java` |
| Default `size` | 25 | `InsumoResource.java:89` (entre otros) |
| Tope `size` | 200 (a confirmar en 033–040) | D-08 |
| Stub `conteoUsosApu` | `return 0L` | `InsumoCrudService.java` |
| Mapping FK insumo-en-uso actual | 400 `validacion` | `InsumoCrudService.eliminar` |
| Mapping FK base-no-archivada actual | 409 `base-no-archivada` | `BaseInsumosService.java:156` |
| `GET /proyectos/parametros-sistema` retorno actual | entidad JPA `ParametrosSistema` | `ProyectoResource.java:127` |
| `/proyectos/{id}/duplicar` | ausente | búsqueda exhaustiva en `proyecto/` |

> **Nota:** este bloque **no predice** conteos de `./gradlew test`
> para 033–040. Cada plan enuncia comandos focales y la suite
> completa se reporta solo como medición opcional al cierre.

---

## 7. Trabajo explícitamente diferido

No debe introducirse indirectamente dentro de otros servicios.

### Nuevo módulo `recalculo`

Diferido por la restricción actual de no crear módulos nuevos. Mientras no
exista, permanecen incompletos:

- propagación de `%HM` a todos los APUs;
- propagación del `%CI` default solo a APUs sin override;
- descuento global FORMA 1;
- recálculo tras edición atómica FORMA 2;
- rollback transaccional de una recalculación masiva;
- actualización de presupuesto/cronograma posterior.

### Funcionalidad de iteraciones posteriores

- cronograma completo;
- export SERCOP xlsx/pdf completo;
- administración de usuarios;
- VAE;
- operaciones bulk de APU;
- frontend drag-and-drop/debounce visual.

---

## 8. Criterios de aceptación por bloque

| Bloque | Resultado observable |
|---|---|
| 0 | `main` sin cambios flotantes; docs ya no ordenan enlaces auxiliares |
| 1 | **PARCIAL — CIERRE CON RESIDUO ACEPTADO (2026-08-28)**: motor opera con `BigDecimal` natural; APU→Rubro usa `PU DOWN 2dp` + `PT = cantidad × PU_2dp` retenido a escala 6 `HALF_UP` (regla workbook-consistent). `ConsolidadorFronteraTest` 5/5 verde. **GM-19 (`-$6.95`) y GM-20 cap. 1 (`-$0.84`) con residual aceptado — no se reabre el motor.** T1 (consolidación) **DONE**; T2 (no-links estructural), T3 (display config global) y T4 (`@Digits`) **IMPLEMENTADOS 2026-08-28** (Plan 014). |
| 2 | PATCH orden funciona; cálculo respeta orden y precisión |
| 3 | plantilla PERSONAL se guarda/carga; fallback produce advertencias; fila pendiente con `insumo_id = NULL` + override `0` (V005 estructural) |
| 4 | central archivada desaparece; borrado no afecta copias PROYECTO |
| 5 | proyecto se crea desde snapshot sin enlazar datos originales |
| 6 | **DONE 2026-08-30 · VERIFICACIÓN DIRIGIDA COMPLETA** — APIs actuales no filtran BIGINT internos; matriz completa en [Plan 07](./planes-para-estar-al-dia/07-uuidv7-fronteras-rest.md); sin migraciones nuevas; PK/FK `BIGINT`; motor intacto. Suites dirigidas verdes; suite completa y Bruno quedan para Plan 08. |
| 7 | docs/Bruno describen exactamente el código final |
| 8 | **I-11 PLANNED; Plan 032 DONE (2026-09-07)** — acta firmada + inventario publicado; **21 decisiones locked** (20 originales + adenda firmada D-21); **15 STOP** con disposición (10 CLOSED + 4 DEFERRED a I-12 + 1 CLOSED con gate RED-first en 035); catálogo D-13 verbatim (26 eventos); matriz canónica `evento → detalle`; paridad P-38…P-42; V004 19/6/4; 4 seeds `valor_referencia`; sin claims de commit ni de suite completa. Planes 033–40 pendientes; 033 es la siguiente tarea autorizada tras 032. |

---

## 9. Verificación final para ejecutar al terminar

El usuario ejecutará estas comprobaciones al cierre:

```bash
cd /home/kaandradec/Documents/workspace/uce/proyecto-grado/thesis-back-quarkus

# Formato/compilación
./gradlew spotlessCheck
./gradlew build -x test

# Schema/seeds/identidad
./gradlew test --tests 'ec.uce.propuestas.schema.*'
./gradlew test --tests 'ec.uce.propuestas.identifier.*'

# Motor
./gradlew test --tests 'ec.uce.propuestas.motor.*'

# Módulos actuales
./gradlew test --tests 'ec.uce.propuestas.proyecto.*'
./gradlew test --tests 'ec.uce.propuestas.insumo.*'
./gradlew test --tests 'ec.uce.propuestas.apu.*'
./gradlew test --tests 'ec.uce.propuestas.plantilla.*'
./gradlew test --tests 'ec.uce.propuestas.documento.*'

# Suite completa
./gradlew test

# Restricciones críticas
! grep -RInE 'double|float' src/main/java/ec/uce/propuestas/motor/
! grep -RInE 'apu_auxiliar_id|apuAuxiliarId|CAMBIO_AUXILIAR|cdAuxiliar|esAuxiliar' \
  src/main src/test docs/modulos
! find src/main/resources/db/migration -maxdepth 1 \( -name 'V005*' -o -name 'V006*' \) -print | grep .

git diff --check
git status --short
```

Para validar el grafo después de todos los cambios:

```bash
cd /home/kaandradec/Documents/workspace/uce/proyecto-grado
graphify update .
```

---

## 10. Definición de “backend actual al día”

Se considera alcanzada cuando:

- no quedan cambios sin commit en `main`;
- los documentos ya no contradicen la decisión no-links;
- las capacidades DONE/PARTIAL de esta matriz están cerradas dentro de módulos
  existentes;
- lo diferido está marcado explícitamente y no tiene imports o stubs ocultos;
- las APIs actuales usan UUIDv7 públicos;
- motor, seeds, schema y suite completa pasan;
- no se creó ningún módulo nuevo de primer nivel.
