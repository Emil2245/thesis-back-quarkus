# 035 — Bases centrales — cierre (P-39)

**Estado:** TODO · I-11 · P-39 / US-36 / TC-P39-01..03.

> P-39 ya está **DONE 2026-08-29** por
> [`docs/modulos/planes-para-estar-al-dia/05-administracion-bases.md`](../../docs/modulos/planes-para-estar-al-dia/05-administracion-bases.md)
> (alias "Plan 015bis"): `AdminBaseCentralResource` bajo
> `/admin/bases-centrales` con crear / renombrar / archivar /
> borrar (D-12, sin bloqueo por copias PROYECTO) + CRUD e import
> CSV de insumos. **Este plan no lo reescribe.**
>
> **Divergencias canónicas a resolver por el acta 032 antes de 035:**
> el canon exige que el acta decida entre `DELETE` base central
> **204/404** (directo) y la implementación actual **409
> `base-no-archivada`** (cuando la base está activa); y entre
> `DELETE` insumo en base central **409 `insumo-en-uso`** (canon)
> y la implementación actual **400 `validacion`**. 035 **no
> prefija** la decisión: aplica la decisión del acta solo tras
> RED con tests rojos previos que reproduzcan el comportamiento
> actual. **No se usan marcas de paridad fabricadas** (`✔`/`⚠`/`✗`);
> solo hechos verificados con test rojo previo.
>
> La emisión D-13 `admin.base_editada` se materializa solo para
> mutaciones **exitosas**; no existen emisiones para operaciones
> rechazadas en este proceso.

## Proceso / historia / criterios

- **Proceso:** P-39.
- **Historia:** US-36.
- **Iteración:** I-11.
- **Criterios de aceptación (quality/02):**
  - **TC-P39-01:** admin → CRUD base central + import CSV destino
    central → equivalente a P-14/P-15 sobre base CENTRAL.
  - **TC-P39-02:** base central referenciada por proyectos →
    `POST …/archivar` → oculta del catálogo
    (`GET /bases-centrales` del usuario); datos de proyectos
    intactos (D-12).
  - **TC-P39-03:** fila de usuario que copió precio de central →
    admin edita el precio central → la fila del usuario **no
    cambia** (snapshot — asunción A9/§17 #16).

## Objetivo medible

Una ejecución futura debe demostrar que:

1. `AdminBaseCentralResource` (Plan 015bis) cumple, **tras la
   reconciliación del acta 032**, la tabla `07-api-contract.md §9`
   filas P-39:
   - `GET    /admin/bases-centrales?incluirArchivadas=` → 200
     `[AdminBaseCentralResponse]` (UUIDv7 + nombre + tipo +
     archivada + `totalInsumos`).
   - `POST   /admin/bases-centrales` → 201 + UUIDv7 + 400
     `validacion`.
   - `PUT    /admin/bases-centrales/{id}` → 200 + 400 + 404.
   - `POST   /admin/bases-centrales/{id}/archivar` → 200 + 404.
   - `DELETE /admin/bases-centrales/{id}` → el comportamiento lo
     fija el acta 032 (204/404 directos **o** 409
     `base-no-archivada`); los proyectos con copia PROYECTO no se
     bloquean (D-12 + §17 #16).
   - `POST   /admin/bases-centrales/{id}/insumos` →
     201/400/404 + `codigo-duplicado`.
   - `PUT    /admin/bases-centrales/{id}/insumos/{iid}` →
     200/400/404; **las ediciones no afectan APUs de usuarios**
     (N04 §A9 — fila de APU apunta siempre a PROYECTO).
   - `DELETE /admin/bases-centrales/{id}/insumos/{iid}` → el
     comportamiento lo fija el acta 032 (409 `insumo-en-uso`
     **o** 400 `validacion`).
   - `POST   /admin/bases-centrales/{id}/insumos/import?soloValidar=`
     → 200/400/404 + upsert D-06.
2. La **única** mutación nueva esperada por 035 es la emisión
   D-13 `admin.base_editada` a través de 033. El nombre del evento
   es **exactamente** `admin.base_editada` (un solo nombre canónico;
   no hay variantes). `detalle = { "operacion": "<clave>",
   "cantidadInsumos": <int> }`; `entidadId` top-level lleva el
   UUIDv7 de la `BaseInsumos` central afectada. Las claves exactas
   de `operacion` (8 valores cerrados en la decisión 37) son las
   que 033 publica en el mapa `detallesEsperados()` del enum
   `EventoLogActividad` (matriz canónica del acta 032 decisión 2)
   y `LogActividadDetalleValidator`, ya materializado por 033, las
   valida; 035 **no** redefine claves, **no** amplía el mapa,
   **no** crea otro validador. La clave `cantidadRubros` **no**
   entra al detalle de P-39: el recurso admin de bases centrales
   no reporta rubros del proyecto, solo la cantidad de insumos en
   la base afectada.
3. Cualquier **gap estrecho** detectado en la auditoría (p. ej.
   ausencia de un código 409 específico, validación faltante,
   respuesta con campo que rompe TC-P42-02) se cierra **solo**
   si la auditoría demuestra el defecto con un test rojo previo.
   No se reabre el recurso; el cierre es un parche dirigido.
4. La regresión dirigida `ec.uce.propuestas.insumo.*` sigue
   verde: 035 no añade tests de CRUD de insumo (ya están), solo
   `@QuarkusTest` para la emisión D-13 y, si el gap aparece, un
   `@QuarkusTest` focal del gap.
5. Bruno 12-admin (creado en 040) tendrá 3 requests `TC-12-P39-*`
   para TC-P39-01..03.

## Dependencias y gates

| Gate | Requisito | Efecto |
|---|---|---|
| G0 — 032 cerrado | Acta firmada; decisiones 1–20 vigentes; **decisión 11** resuelve DELETE base/insumo. | Habilita código. |
| G1 — 033 cerrado | `LogActividadService.emitir` operativo; `MANDATORY` verificado. | Habilita emisión D-13. |
| G2 — Plan 015bis DONE | `AdminBaseCentralResource` (16/16 IT), `InsumoCrudService`, `ImportacionInsumoService`, `CsvInsumoParser` operativos. | Habilita reuso estricto. |
| G3 — D-12 vigente | Archivar central; borrar sin bloqueo tras archivar; copia PROYECTO intacta. | Habilita TC-P39-02. |
| G4 — D-06 vigente | Upsert CSV por `codigo`. | Habilita TC-P39-01 (import). |
| G5 — A9 vigente | Fila de APU siempre apunta a PROYECTO (no CENTRAL). | Habilita TC-P39-03. |
| G6 — cierre | Regresión `insumo.*` verde; emisión D-13 verde; gap (si existe) cerrado con evidencia. | Evidencia medible. |

`STOP-035-REESCRITURA` se activa si la auditoría detecta necesidad
de reescribir `AdminBaseCentralResource` (p. ej. cambiar el orden
de los endpoints, mover de paquete, agregar un middleware nuevo).
Reabrir 032 antes de continuar.

`STOP-035-DIVERGENCIA-SIN-ACTA` se activa si el acta 032 no
resuelve `STOP-032-DELETE-BASE-CENTRAL` o
`STOP-032-DELETE-INSUMO-CENTRAL` y 035 intenta predecidir. Reabrir
032.

## Fuentes que deben releerse

- `plans/panel-admin/032-sincronizar-contrato-inventario-admin.md`
  (decisiones 11, 12, 20).
- `plans/panel-admin/033-log-actividad-base.md` (API de emisión).
- [`docs/modulos/planes-para-estar-al-dia/05-administracion-bases.md`](../../docs/modulos/planes-para-estar-al-dia/05-administracion-bases.md)
  (estado DONE 2026-08-29; contrato exacto; tabla "Cambios en este
  pase"; criterios de terminado; STOP conditions ya cerradas).
- `../../../thesis-docs/plan/design/03-procesos-detalle.md` §H/P-39
  y §J/D-12 (literal).
- `../../../thesis-docs/plan/architecture/07-api-contract.md` §9
  filas P-39.
- `../../../thesis-docs/plan/architecture/06-database-schema.md` §2.6
  (`base_insumos`, `insumo`).
- `../../../thesis-docs/plan/quality/02-catalogo-pruebas.md`
  TC-P39-01..03.
- `../../../thesis-docs/plan/design/07-decisiones-i06-pendientes.md`
  §A9 (copia al usar; las filas APU no referencian CENTRAL).
- `src/main/java/ec/uce/propuestas/insumo/resource/AdminBaseCentralResource.java`
  (Plan 015bis).
- `src/main/java/ec/uce/propuestas/insumo/service/BaseInsumosService.java`
  (métodos `crearCentral`, `renombrarCentral`, `archivarCentral`,
  `eliminarCentralArchivada`, `obtenerCentralPorPublicId`).
- `src/main/java/ec/uce/propuestas/insumo/service/InsumoCrudService.java`
  y `service/importacion/ImportacionInsumoService.java`.
- `src/test/java/ec/uce/propuestas/insumo/resource/AdminBaseCentralResourceIT.java`
  (16/16 verde; base de la auditoría).

## Estado inicial esperado

- `AdminBaseCentralResource` con 9 endpoints canónicos
  (`GET/POST/PUT/DELETE`, `…/archivar`, `…/insumos` CRUD,
  `…/insumos/import`).
- `AdminBaseCentralResourceIT` **16/16 verde** (Plan 015bis).
- `InsumoCrudService`, `ImportacionInsumoService`, `CsvInsumoParser`,
  DTOs `InsumoCrearRequest`, `InsumoEditarRequest`,
  `ImportResultadoResponse`, `InsumoImportForm` reusables.
- Sin emisión D-13 todavía (`admin.base_editada` se agrega en
  035).
- `problema/conflicto` helper (`ProblemaException.conflicto`) ya
  existe para 409 tipados.

## Decisiones locked adicionales (035)

Se suman a las 20+8 anteriores; no las contradicen:

36. **Reuso estricto:** 035 **no** modifica la firma ni el orden
    de los métodos existentes en `AdminBaseCentralResource`. Si
    la auditoría encuentra un gap, se cierra con un parche
    mínimo (verificación adicional, código de error tipado
    nuevo) **dentro** del método existente; no se renombra ni
    se mueve.
37. **Emisión D-13 (035) — solo operaciones exitosas:**
    `admin.base_editada` se emite en cada mutación exitosa del
    recurso admin de bases:
    - `crear` (POST base);
    - `renombrar` (PUT base);
    - `archivar` (POST …/archivar);
    - `borrar` (DELETE base — **emisión previa al DELETE**
      cuando el canon exige 204/404 y la operación se confirma
      exitosa);
    - `importar` (POST …/insumos/import);
    - `crearInsumo` (POST …/insumos);
    - `editarInsumo` (PUT …/insumos/{iid});
    - `borrarInsumo` (DELETE …/insumos/{iid}).
    `detalle = { "operacion": <clave>, "cantidadInsumos": <int> }`
    (la clave `operacion` es una de las 8 listadas arriba, cerrada);
    el UUIDv7 de la `BaseInsumos` central afectada vive en
    `entidadId` top-level (no en `detalle`). Esto alinea 035 con la
    matriz canónica publicada por el acta 032 (decisión 2).
    Las **operaciones de solo lectura** (`GET` listado, `GET`
    por id) **no** emiten. Las **operaciones rechazadas** (409,
    400 `validacion`) **no** emiten. El emisor se invoca dentro
    de la `@Transactional` exterior del método admin
    (`MANDATORY`); un rollback del flujo borra la fila del log.
38. **No emisión por CRUD de insumo del usuario (`InsumoResource`,
    `BasePersonal`):** la emisión `insumo.creado`,
    `insumo.editado`, `insumo.eliminado`, `insumos.import_csv`,
    `base.copiada_a_proyecto` se delega a 038. 035 solo emite
    `admin.base_editada` desde el recurso admin. Esta división
    evita doble emisión: una fila de insumo creada en CENTRAL por
    el admin produce `admin.base_editada` (035) y **no**
    `insumo.creado` (038); los eventos D-13 son disjuntos por
    responsabilidad del caller.
39. **Auditoría de paridad (sin reescritura, sin marcas
    fabricadas):** la auditoría produce un reporte corto
    `docs/modulos/panel-admin/035-auditoria-p39.md` que compara
    cada fila de `07-api-contract.md §9` P-39 contra
    `AdminBaseCentralResource` + DTOs. El reporte lista, **solo
    con hechos verificados por test rojo previo**, el estado de
    cada fila:
    - **`paridad`**: paridad exacta contra el canon;
    - **`divergencia`**: desviación del canon; 035 aplica la
      reconciliación del acta 032 con test rojo previo;
    - **`gap`**: defecto demostrable; 035 lo cierra con parche
      mínimo dirigido.
    **No se usan marcas `✔`/`⚠`/`✗` fabricadas** sin evidencia.
    Solo los gaps estrechos (que afectan TC-P39) entran en
    alcance de 035.

## Alcance

### Incluye

- Auditoría de paridad documentada en
  `docs/modulos/panel-admin/035-auditoria-p39.md`.
- Emisión D-13 `admin.base_editada` (solo operaciones exitosas)
  desde `AdminBaseCentralResource` y desde los métodos admin de
  `BaseInsumosService` (los métodos admin que el recurso llama).
- Si la auditoría detecta un **gap estrecho** (p. ej. código 409
  con texto distinto al canon), parche mínimo con un test rojo
  previo y verde posterior.
- Tests `@QuarkusTest`:
  - `AdminBaseCentralLogAuditoriaIT` (8 escenarios: 1 por
    operación listada en decisión 37; asserta fila `log_actividad`
    con `evento=admin.base_editada` y `detalle.operacion=<clave>`).
  - Si gap: `AdminBaseCentralGapXXXIT` focal.
- Bruno 3 (creado en 040) tendrá 3 requests `TC-12-P39-01..03`
  (los crea 040 con ayuda del reporte de auditoría; 035 no
  escribe Bruno).
- Actualización del inventario I-11 con el reporte de auditoría.

### No incluye

- Reescribir `AdminBaseCentralResource` (Plan 015bis).
- Cambiar `InsumoCrudService` ni `ImportacionInsumoService` ni
  `CsvInsumoParser` (reusados tal cual).
- Migración nueva.
- Cambios en `BaseInsumosResponse` (DTO público no-admin).
- Cambios en `BasesPersonalesResource` (P-09/P-17; ajeno a P-39).
- Recalcular APUs tras editar precios centrales (decisión
  histórica: nunca — A9 + §17 #16 + N04).
- Prefijar la matriz de comportamiento de `DELETE` base o
  `DELETE` insumo: lo fija el acta 032.

## Archivos a crear/modificar (candidatos, no autorización)

| Acción | Archivo posible | Condición |
|---|---|---|
| Modificar | `src/main/java/ec/uce/propuestas/insumo/resource/AdminBaseCentralResource.java` | +1 import (`LogActividadService`); +1 línea por endpoint mutante exitoso llamando a `logActividadService.emitir(...)` con la clave de operación. |
| Modificar | `src/main/java/ec/uce/propuestas/insumo/service/BaseInsumosService.java` | Si los métodos admin (`crearCentral`, `renombrarCentral`, `archivarCentral`, `eliminarCentralArchivada`) no son invocados desde el recurso en la misma `@Transactional`, inyectar el emisor allí. |
| Crear | `src/test/java/ec/uce/propuestas/insumo/resource/AdminBaseCentralLogAuditoriaIT.java` | 8 escenarios (decisión 37). |
| Crear (condicional) | `src/test/java/ec/uce/propuestas/insumo/resource/AdminBaseCentralGap<XXX>IT.java` | Solo si la auditoría detecta gap. |
| Crear | `docs/modulos/panel-admin/035-auditoria-p39.md` | Reporte de paridad. |
| Modificar | `docs/modulos/planes-para-estar-al-dia/05-administracion-bases.md` | Una línea: "I-11 035 agrega emisión D-13 `admin.base_editada`; ver `035-auditoria-p39.md`." |
| Modificar | `docs/modulos/panel-admin/00-inventario-trabajo.md` | Marca 035 `DONE`. |
| Modificar (opcional) | `api/bruno/12-admin/TC-12-P39-01..03.bru` | En 040, con base en el reporte de auditoría. |
| No previsto | `src/main/java/ec/uce/propuestas/insumo/dto/BaseInsumosResponse.java` | STOP — DTO público no-admin. |
| No previsto | `src/main/java/ec/uce/propuestas/insumo/service/InsumoCrudService.java` (excepto un emisor si la auditoría lo exige) | STOP — reusado. |
| No previsto | `src/main/resources/db/migration/**` | STOP. |
| No previsto | `src/main/java/ec/uce/propuestas/insumo/resource/InsumoResource.java` (CRUD no-admin) | STOP — la emisión `insumo.*` es de 038. |

## Auditoría de paridad (sin marcas fabricadas)

`035-auditoria-p39.md` lista, para cada fila de `07-api-contract.md §9`
P-39, el estado verificado por test rojo previo y el plan de
reconciliación. Las filas son:

| Fila §9 | Endpoint | Comportamiento actual (verificado) | Acción de 035 |
|---|---|---|---|
| GET | `/admin/bases-centrales?incluirArchivadas=` | Filtro + `incluirArchivadas` default `false` | Sin cambios |
| POST | `/admin/bases-centrales` | 201 + UUIDv7 con `AdminBaseCentralCrearRequest{nombre}` | + emisión `admin.base_editada` (`detalle.operacion=crear`) |
| PUT | `/admin/bases-centrales/{id}` | 200/400/404 con `AdminBaseCentralEditarRequest{nombre}` (rename) | + emisión `admin.base_editada` (`detalle.operacion=renombrar`) |
| POST | `/admin/bases-centrales/{id}/archivar` | 200; archivada=true; oculta del catálogo normal | + emisión `admin.base_editada` (`detalle.operacion=archivar`) |
| DELETE | `/admin/bases-centrales/{id}` | El acta 032 fija el comportamiento canónico (204/404 directos o 409 `base-no-archivada`). Implementación actual = 409 `base-no-archivada`. | **Reconciliación según acta 032**, previa al DELETE si exitoso, con test rojo previo que reproduzca el comportamiento actual. |
| POST | `/admin/bases-centrales/{id}/insumos` | 201/400/404 + `codigo-duplicado` con `InsumoCrearRequest` | + emisión `admin.base_editada` (`detalle.operacion=crearInsumo`) |
| PUT | `/admin/bases-centrales/{id}/insumos/{iid}` | 200/400/404 con `InsumoEditarRequest`; sin afectar APUs de usuario | + emisión `admin.base_editada` (`detalle.operacion=editarInsumo`) |
| DELETE | `/admin/bases-centrales/{id}/insumos/{iid}` | El acta 032 fija el comportamiento canónico (409 `insumo-en-uso` o 400 `validacion`). Implementación actual = 400 `validacion`. | **Reconciliación según acta 032**, previa al DELETE si exitoso, con test rojo previo que reproduzca el comportamiento actual. |
| POST | `/admin/bases-centrales/{id}/insumos/import?soloValidar=` | 200/400/404 con `ImportResultadoResponse`, upsert D-06 | + emisión `admin.base_editada` (`detalle.operacion=importar`) |

Estados reportables solo con test rojo previo:

- **`paridad`** — paridad exacta contra el canon.
- **`divergencia`** — desviación del canon; 035 aplica la
  reconciliación del acta 032.
- **`gap`** — defecto demostrable; 035 lo cierra con parche
  mínimo dirigido.

## Secuencia TDD (estricta)

### RED

1. `AdminBaseCentralLogAuditoriaIT` (8 escenarios, **solo
   operaciones exitosas**):
   - Crear base → 1 fila `admin.base_editada` con
     `detalle.operacion=crear`; el UUIDv7 de la base vive en
     `entidadId` top-level (no en `detalle`).
   - Renombrar base → 1 fila `operacion=renombrar`.
   - Archivar base → 1 fila `operacion=archivar`.
   - Borrar base archivada → 1 fila `operacion=borrar` (cuando
     el canon exige 204/404; con el comportamiento del acta).
   - Importar CSV (`soloValidar=false`) → 1 fila
     `operacion=importar` con `cantidadInsumos` consistente.
   - Crear insumo en base → 1 fila `operacion=crearInsumo`.
   - Editar insumo en base → 1 fila `operacion=editarInsumo`.
   - Borrar insumo en base → 1 fila `operacion=borrarInsumo`
     (cuando el canon exige éxito).
2. Test de TC-P39-03 (regresión ya existente o nuevo): un
   usuario con un insumo PROYECTO copiado de CENTRAL; el admin
   edita el precio central; el insumo PROYECTO del usuario
   **no cambia** (`assertEquals` con el valor previo). Si el test
   ya está en `InsumoCrudServiceIT` (probable), se reusa; si no,
   se agrega.
3. Test de no-emisión en lectura: `GET /admin/bases-centrales`
   no produce fila `log_actividad`.

### GREEN

Inyectar `LogActividadService` en `AdminBaseCentralResource` y
emitir en cada mutación exitosa con la clave canónica de la
decisión 37. Para los gaps detectados, parchear solo dentro del
método existente, con test rojo previo que reproduzca el
comportamiento actual.

### TRIANGULATE

- Importación con `soloValidar=true` no emite (operación dry-run).
- Renombrar con el mismo nombre: ¿emite o no? Decisión: emite
  siempre que el endpoint devuelva 200, porque el admin ejecutó
  una acción (el log es de **intentos exitosos**, no de cambios
  efectivos). Documentar en `035-auditoria-p39.md`.
- `DELETE` insumo o base rechazados (cualquiera sea el código que
  fije el acta 032): **no emiten** (decisión 37).

### REFACTOR

- Centralizar el emisor en un helper estático
  `AdminBaseCentralEventos.emitir(log, baseId, operacion,
  cantidadInsumos)` si la duplicación de las 8 llamadas resulta
  molesta (>10 líneas duplicadas). 035 lo deja inline y decide
  en 040.

## Catálogo mínimo de pruebas

| Caso | Resultado |
|---|---|
| TC-P39-01 CRUD + import | Verde (ya existe; reusa). |
| TC-P39-02 archivar oculta | Verde (ya existe; reusa). |
| TC-P39-03 edición central no afecta PROYECTO | Verde (ya existe o se agrega). |
| Emisión `admin.base_editada` × 8 operaciones | 8 filas `log_actividad` con clave correcta. |
| No emisión en GET | 0 filas `log_actividad`. |
| No emisión en operaciones rechazadas | 0 filas `log_actividad`. |
| Sin PII en `detalle` | 0 matches regex. |
| UUIDv7 en `entidadId` top-level | asserta `UUID.version() == 7`. |

## Comandos de verificación

```bash
cd /home/kaandradec/Documents/workspace/uce/proyecto-grado/thesis-back-quarkus

# Regresión dirigida insumo: corre la suite `--tests 'ec.uce.propuestas.insumo.*'`; comparar contra el XML agregado del comando; no predecir conteos.
./gradlew test --tests 'ec.uce.propuestas.insumo.*' \
  -Dquarkus.http.test-port=0 --console=plain

# Focales 035.
./gradlew test --tests 'ec.uce.propuestas.insumo.resource.AdminBaseCentral*IT' \
  -Dquarkus.http.test-port=0 --console=plain
# esperado: AdminBaseCentralResourceIT verde (regresión) +
#           AdminBaseCentralLogAuditoriaIT verde (nuevo).

# Reporte de auditoría presente.
test -f docs/modulos/panel-admin/035-auditoria-p39.md

# Sin migración nueva.
git diff --name-only -- 'src/main/resources/db/migration/**'
# esperado: vacío.

# Sin cambios ajenos al paquete insumo.
git diff --name-only -- 'src/main/java/ec/uce/propuestas/' \
  | grep -v '^src/main/java/ec/uce/propuestas/insumo/'
# esperado: vacío.

# Motor y recalculo intactos.
git diff --name-only -- 'src/main/java/ec/uce/propuestas/motor/**' \
  -- 'src/main/java/ec/uce/propuestas/recalculo/**'
# esperado: vacío.
```

No se predicen conteos de suite completa. El orquestador decide.

## Completion checklist (035)

- [ ] `AdminBaseCentralResource` intacto en firma y orden de
      endpoints (reuso estricto).
- [ ] Emisión `admin.base_editada` desde las 8 operaciones
      mutantes **exitosas** (decisión 37) con la clave de
      operación correcta.
- [ ] No emisión en operaciones de lectura.
- [ ] No emisión en operaciones rechazadas.
- [ ] `035-auditoria-p39.md` firmado: filas verificadas con test
      rojo previo; sin marcas `✔`/`⚠`/`✗` fabricadas.
- [ ] Reconciliación de `DELETE` base e `DELETE` insumo según
      acta 032, con test rojo previo.
- [ ] `InsumoResource` no-admin intacto (la emisión `insumo.*`
      es de 038).
- [ ] Ninguna migración nueva; motor intacto.
- [ ] `git diff --check` limpio.

## Handoff al siguiente plan

Cuando 035 cierre, el orquestador puede iniciar **036** (P-40
plantillas APU de sistema) o cualquier otro de 035–037. 036
agrega el flujo admin de plantillas SISTEMA reusando
`SnapshotApuMapper` price-free.