# 039 — Instrumentación D-13: presupuesto, cronograma, documento

**Estado:** TODO · I-11 · tercera ola de emisores D-13.

> Cubre los eventos `presupuesto.version_creada`,
> `presupuesto.version_activada`, `cronograma.editado` y
> `documento.exportado`. **Preserva estrictamente** la
> transacción única y el cierre TOCTOU de Plan 031 (export
> cronograma); **no** toca `motor/`, `recalculo/`, ni los
> `BigDecimal` workbook-consistent. Emite siempre dentro de la
> misma `@Transactional` exterior (sin ghost events) a través
> de `LogActividadService` (033).
>
> **Solo operaciones exitosas emiten.** Las exportaciones
> **bloqueadas** (409 `export-bloqueado` de Plan 031) **no**
> producen fila `log_actividad`; la decisión 12 de 032 elimina
> el patrón `emitirFailure`/`REQUIRES_NEW`/`codigoError`.
>
> Los 4 nombres legacy de V004 (`base.insumos.copiada`,
> `rubro.creado`, `cronograma.creado`,
> `presupuesto.vigente_marcado`) **no** entran al enum
> runtime y **se excluyen** explícitamente de la cobertura
> exacta de 26 eventos del test de cobertura 040.

## Proceso / historia / criterios

- **Proceso:** transversal (instrumentación).
- **Historia:** US-39 (cobertura completa; verificación final
  en 040).
- **Iteración:** I-11.
- **Criterio de cobertura:** los 4 eventos listados arriba se
  emiten **exactamente una vez** por la mutación correspondiente
  (operación exitosa), dentro de la misma `@Transactional`
  exterior.

## Objetivo medible

Una ejecución futura debe demostrar que:

1. `presupuesto.version_creada` se emite cuando se crea una
   nueva versión de presupuesto (deep copy); `detalle = {
   "presupuestoOrigenId": <UUIDv7>, "versionNueva": <int> }`;
   el UUIDv7 del `Presupuesto` nuevo vive en `entidadId`
   top-level (no en `detalle`);
2. `presupuesto.version_activada` se emite cuando una versión
   se marca vigente (toggle `es_vigente`) en el método público
   real del servicio (`VersionadoService.marcarVigente`);
   `detalle = { "version": <int>, "presupuestoPreviamenteVigenteId":
   <UUIDv7>|null }`; el UUIDv7 del `Presupuesto` activado vive
   en `entidadId` top-level;
3. `cronograma.editado` se emite cuando hay mutación de
   configuración, actividades, segmentos o avance del cronograma
   (no se emite en lectura); `detalle = { "operacion":
   "<clave>" }`; el UUIDv7 del `Cronograma` vive en `entidadId`
   top-level. La clave `operacion`
   pertenece **exactamente** al conjunto cerrado de **6 valores
   canónicos** (decisión 56 abajo);
4. `documento.exportado` se emite cuando el preflight o la
   descarga materializa bytes (XLSX, PDF, MSPDI XML de Plan 031
   o ET DOCX existente), solo en operaciones **exitosas**;
   `detalle = { "formato": "XLSX|PDF|MSPDI|DOCX", "bytes":
   <long>, "stale": <bool> }`; el UUIDv7 del `Presupuesto`
   afectado (cuando existe) vive en `entidadId` top-level, o
   `null` cuando no hay presupuesto asociado.
   La descarga del cronograma MSPDI de Plan 031 emite con
   `formato=MSPDI` y `entidadId` poblado; la exportación ET
   DOCX emite con `formato=DOCX` y `presupuestoId` poblado;
5. los 4 emisores viven en los **servicios públicos** (no en
   `motor/`, no en `recalculo/`, no en clases internas); los
   casos de uso son los recursos
   (`PresupuestoVersionResource`, `PresupuestoVigenciaResource`,
   `CronogramaService` (configurar, programar.*),
   `VistasCronogramaService.marcarRevisado`,
   `CronogramaDocumentoResource` de Plan 031, `DocumentoResource`
   ET existente);
6. ningún cálculo del motor se modifica; el agregado XML del comando
   del motor muestra los mismos GM-19/GM-20 aceptados y GM-24 omitido
   que en la línea base (no se predicen conteos).

## Dependencias y gates

| Gate | Requisito | Efecto |
|---|---|---|
| G0 — 032 cerrado | Acta firmada; decisiones 1–20 vigentes (en particular 9 y 15: ruta canónica, Plan 031 intacto). | Habilita código. |
| G1 — 033 cerrado | `LogActividadService.emitir` operativo. | Habilita emisión D-13. |
| G2 — 034..038 cerrados | Hay 22 nombres únicos asignados antes de este plan: 3 de 034, 1 de 035, 1 de 036, 1 de 037 y 16 de 038. El camino adicional de `usuario.activado` por invitación no incrementa el número de nombres. `proyecto.duplicado` solo cuenta como productor si 032 cerró su STOP. | Habilita los 4 nombres restantes de 039. |
| G3 — Plan 031 commiteado y limpio | antes de modificar `CronogramaDocumentoResource` u otro archivo de Plan 031, `main` debe tener los cambios de Plan 031 commiteados y `git status --short` debe mostrar **cero** archivos ajenos. Si no, la ejecución de 039 reabre 031 o se aborta. | Habilita la inyección de `LogActividadService` sin arrastrar deuda. |
| G4 — Plan 031 export preservado | `BloqueoExportDetalle`, `BloqueoExportResponse`, `CronogramaExportPreflightResponse`, lanes de `cronograma/export/**`, transacción única y TOCTOU intactos. Solo se agrega una línea `emitir(...)` final dentro de la `@Transactional` exterior del método público. | Habilita `documento.exportado` reusando Plan 031. |
| G5 — `motor/`, `recalculo/` intactos | Workbook-consistent y `BigDecimal` natural sin cambios. | Habilita 039 sin DDL ni aritmética. |
| G6 — UUIDv7 cerrado | `UuidV7.parse` en frontera; PK/FK BIGINT internas. | Habilita `detalle` con UUIDv7. |
| G7 — cierre | 4 `@QuarkusTest` focales verdes; regresión `presupuesto.*`, `cronograma.*`, `documento.*` verde; cobertura D-13 completa. | Evidencia medible. |

`STOP-039-MOTOR` se activa si 039 inserta un emisor dentro de
`motor/` o `recalculo/`. Reabrir 032.

`STOP-039-PLAN031` se activa si 039 reabre la transacción única
o el TOCTOU de Plan 031. Reabrir 032.

## Fuentes que deben releerse

- `plans/panel-admin/032-sincronizar-contrato-inventario-admin.md`
  (decisiones 1, 9, 13, 15, 17).
- `plans/panel-admin/033-log-actividad-base.md` (API de emisión).
- `plans/031-exportacion-cronograma.md` (transacción única,
  TOCTOU, preflight, `BloqueoExportDetalle`,
  `CronogramaDocumentoResource`, `CronogramaExportPreflightResponse`).
- `../../../thesis-docs/plan/design/03-procesos-detalle.md` §J
  D-13 (catálogo verbatim).
- `../../../thesis-docs/plan/quality/02-catalogo-pruebas.md`
  TC-P31..37 (cronograma/export).
- `src/main/java/ec/uce/propuestas/presupuesto/service/VersionadoService.java`
  (`VersionadoService.copiarVersion(...)`, lock pesimista de
  fila `proyecto`; vigencia método real
  `VersionadoService.marcarVigente(...)`).
- `src/main/java/ec/uce/propuestas/presupuesto/resource/PresupuestoVersionResource.java`
  y `PresupuestoVigenciaResource.java`.
- `src/main/java/ec/uce/propuestas/cronograma/service/CronogramaService.java`
  (configurar, programarActividad con las 4 operaciones
  semánticas: `Reemplazar`, `Distribuir`, `Mover`,
  `Redimensionar`).
- `src/main/java/ec/uce/propuestas/cronograma/service/VistasCronogramaService.java`
  (`marcarRevisado(...)`).
- `src/main/java/ec/uce/propuestas/cronograma/resource/CronogramaConfiguracionResource.java`
  (PUT …/configuracion).
- `src/main/java/ec/uce/propuestas/cronograma/resource/ActividadProgramarResource.java`
  (PATCH …/actividades/{actividadId}).
- `src/main/java/ec/uce/propuestas/cronograma/resource/VistasCronogramaResource.java`
  (POST …/revisado).
- `src/main/java/ec/uce/propuestas/documento/CronogramaDocumentoResource.java`
  (Plan 031 sin commitear; preflight + descarga).
- `src/main/java/ec/uce/propuestas/documento/DocumentoResource.java`
  (ET DOCX existente; método público
  `exportarEspecificacionesTecnicas`).
- `src/main/java/ec/uce/propuestas/presupuesto/repository/PresupuestoRepository.java`
  (para `presupuesto.publicId` por `id`).

## Estado inicial esperado

- `presupuesto` módulo DONE (Plans 019–025).
- `cronograma` módulo DONE (Plans 026–031).
- `documento` con ET DOCX operativo y export cronograma de
  Plan 031 **sin commitear** (cambios en working tree; 039 los
  reusa tal cual).
- `LogActividadService` operativo (033).
- 22 nombres únicos asignados a 034–038; este plan añade los cuatro
  restantes. La cobertura runtime de `proyecto.duplicado` depende del
  cierre explícito de `STOP-032-P09-DUPLICAR`.

## Decisiones locked adicionales (039)

Se suman a las anteriores; no las contradicen:

54. **Emisores por servicio público (039) — superficies
    confirmadas contra código:**

    | Evento | Servicio público | Método (signatura real verificada) |
    |---|---|---|
    | `presupuesto.version_creada` | `VersionadoService` | `copiarVersion(UUID proyectoPublicId, UUID origenPublicId, PresupuestoVersionCrearRequest req, Long callerUsuarioId)` |
    | `presupuesto.version_activada` | `VersionadoService` | `marcarVigente(UUID presupuestoPublicId, Long callerUsuarioId)` |
    | `cronograma.editado` | `CronogramaService` y `VistasCronogramaService` | `configurar(...)`, `programarActividad(...)` (4 operaciones semánticas), `VistasCronogramaService.marcarRevisado(...)` |
    | `documento.exportado` | `CronogramaDocumentoResource` (Plan 031) y `DocumentoResource` (ET) | `descargar(...)` (después de materializar bytes), `exportarEspecificacionesTecnicas(...)` |

55. **`cronograma.editado` — operaciones canónicas (6
    exactas):** la clave `operacion` del `detalle` pertenece
    **exactamente** al conjunto cerrado:

    | Clave `operacion` | Recurso HTTP | Método de servicio |
    |---|---|---|
    | `configurar` | `PUT /cronogramas/{cronogramaId}/configuracion` | `CronogramaService.configurar(...)` |
    | `programar.reemplazar_avances` | `PATCH /cronogramas/{cronogramaId}/actividades/{actividadId}` (operación `Reemplazar`) | `CronogramaService.programarActividad(...)` |
    | `programar.distribuir_uniforme` | `PATCH …` (operación `Distribuir`) | `CronogramaService.programarActividad(...)` |
    | `programar.mover_segmento` | `PATCH …` (operación `Mover`) | `CronogramaService.programarActividad(...)` |
    | `programar.redimensionar_segmento` | `PATCH …` (operación `Redimensionar`) | `CronogramaService.programarActividad(...)` |
    | `revisar` | `POST /cronogramas/{id}/revisado` | `VistasCronogramaService.marcarRevisado(...)` |

    **No se inventan endpoints ni operaciones nuevas** (no hay
    `programar.agregar_actividad`, `programar.eliminar_actividad`,
    `programar.agregar_segmento`, etc. en este catálogo). Las
    operaciones reales del servicio `programarActividad` son 4
    (`Reemplazar`, `Distribuir`, `Mover`, `Redimensionar`); este
    plan las mapea a las 4 claves canónicas de PATCH arriba.
    Las operaciones rechazadas (4xx) **no** emiten.

56. **Detalle por evento (canónico):** 039 consume verbatim la matriz
    canónica publicada por el acta 032 (decisión 2); **no** redefine,
    **no** agrega ni **no** amplía claves. Los identificadores
    principales de entidad (UUIDv7) viven en `entidadId` top-level;
    los que viven en `detalle` son los estrictamente necesarios para
    distinguir entidades múltiples en el mismo evento. El cuadro
    local siguiente se cita solo a efectos de implementación; la
    autoridad absoluta es la matriz del acta 032.

    | Evento | Claves `detalle` permitidas | `entidadId` top-level |
    |---|---|---|
    | `presupuesto.version_creada` | `{ "presupuestoOrigenId": <UUIDv7>, "versionNueva": <int> }` | UUIDv7 del `Presupuesto` nuevo |
    | `presupuesto.version_activada` | `{ "version": <int>, "presupuestoPreviamenteVigenteId": <UUIDv7>\|null }` | UUIDv7 del `Presupuesto` activado |
    | `cronograma.editado` | `{ "operacion": "<clave>" }` (clave ∈ 6 valores de la decisión 55) | UUIDv7 del `Cronograma` |
    | `documento.exportado` | `{ "formato": "XLSX\|PDF\|MSPDI\|DOCX", "bytes": <long>, "stale": <bool> }` | UUIDv7 del `Presupuesto` afectado o `null` cuando no hay presupuesto asociado |

    Estas claves son consumidas verbatim desde
    `EventoLogActividad.detallesEsperados()` (mapa estático materializado
    por 033 a partir de la matriz del acta 032). 039 **no** modifica
    el enum, **no** redefine claves, **no** amplía el mapa. Si una
    clave faltara, 039 reabre 032.

57. **Emisión dentro de la transacción (Plan 031 TOCTOU):**
    el emisor `documento.exportado` se invoca **dentro** de la
    `@Transactional` exterior del método público, **después** de
    materializar los bytes y **antes** del return (Plan 031
    preserva el orden de commit con TOCTOU: la materialización
    ocurre dentro del lock pesimista, y la respuesta sale sin
    re-validación posterior). La fila del log participa del
    commit del flujo. Las exportaciones **bloqueadas** (4xx
    preflight) no emiten.

58. **Sin cambio en `motor/` ni `recalculo/`:** los emisores
    viven en los servicios públicos. Si el código de Plan 031
    tiene algún cálculo monetario o de fingerprint, se mantiene
    intacto; 039 solo agrega una línea `log.emitir(...)` al
    final del método público, dentro de la misma `@Transactional`.

59. **Lock pesimista preservado:** el emisor
    `presupuesto.version_activada` se emite dentro del mismo
    `@Transactional` que ya usa `VersionadoService` para el
    lock pesimista de fila `proyecto` (`PresupuestoRepository.
    lockProyectoRow(...)` de Plan 024). La fila del log
    participa del lock; el emisor es `MANDATORY`.

60. **Exclusión explícita de legacy V004:** los 4 nombres
    legacy V004 (`base.insumos.copiada`, `rubro.creado`,
    `cronograma.creado`, `presupuesto.vigente_marcado`) **no**
    son parte de este plan ni del enum runtime. 039 los
    excluye explícitamente: ningún emisor los produce, ningún
    test los incluye, y la cobertura de 26 eventos de 040 los
    excluye.

## Alcance

### Incluye

- Modificación de los 4 servicios públicos listados en la
  decisión 54 para emitir el evento D-13 correspondiente
  (solo operaciones exitosas).
- Tests `@QuarkusTest` **a través de los casos de uso
  públicos** (recursos JAX-RS):
  - `PresupuestoVersionResourceLogAuditoriaIT` (1 escenario:
    `POST /presupuestos/{id}/duplicar` → fila con
    `presupuesto.version_creada`).
  - `PresupuestoVigenciaResourceLogAuditoriaIT` (1 escenario:
    `POST /presupuestos/{id}/vigente` → fila con
    `presupuesto.version_activada`).
  - `CronogramaResourceLogAuditoriaIT` (6 escenarios: 1 por
    `operacion` de la decisión 55, **exactamente** los 6
    valores canónicos).
  - `DocumentoResourceLogAuditoriaIT` (4 escenarios: 1 por
    formato XLSX/PDF/MSPDI/DOCX).
- Validación de detalle (`LogActividadDetalleValidator`) con
  el set de claves permitidas por evento (decisión 56).

### No incluye

- Cambios en `motor/`, `recalculo/`, `PresupuestoRepository`
  (excepto un `lockProyectoRow` ya existente de Plan 024), ni
  en `VersionCalculada`.
- Reabrir la transacción única de Plan 031.
- Reabrir el TOCTOU de Plan 031.
- Reabrir las fórmulas workbook-consistent.
- Cambios en `cronograma/export/` (lanes de Plan 031) más allá
  de la línea `emitir(...)` final dentro de la `@Transactional`.
- Cambios en `BloqueoExportDetalle`/`BloqueoExportResponse`
  (Plan 031 sin commitear; preservados). `CronogramaDocumentoResource`
  **sí** se modifica (únicamente para inyectar `LogActividadService`
  y emitir `documento.exportado`; ver tabla de archivos candidatos
  abajo).
- Aceptar los 4 nombres legacy V004 al enum runtime.
- Patrón eliminado por 032 decisión 12: emisores para
  operaciones rechazadas y persistencia de eventos fallidos. **No
  se implementa.**
- Migración nueva.
- Nuevos eventos D-13.

## Archivos a crear/modificar (candidatos, no autorización)

| Acción | Archivo posible | Condición |
|---|---|---|
| Modificar | `src/main/java/ec/uce/propuestas/presupuesto/service/VersionadoService.java` | Inyectar `LogActividadService`; emitir 2 eventos (`presupuesto.version_creada` en `copiarVersion`, `presupuesto.version_activada` en `marcarVigente`). |
| Modificar | `src/main/java/ec/uce/propuestas/cronograma/service/CronogramaService.java` | Inyectar `LogActividadService`; emitir 1 evento con `operacion` correcta (5 valores: `configurar`, `programar.reemplazar_avances`, `programar.distribuir_uniforme`, `programar.mover_segmento`, `programar.redimensionar_segmento`). |
| Modificar | `src/main/java/ec/uce/propuestas/cronograma/service/VistasCronogramaService.java` | Inyectar `LogActividadService`; emitir 1 evento `cronograma.editado` con `operacion=revisar` en `marcarRevisado`. |
| Modificar | `src/main/java/ec/uce/propuestas/documento/CronogramaDocumentoResource.java` (Plan 031) | Inyectar `LogActividadService`; emitir `documento.exportado` con `formato` correcto (XLSX/PDF/MSPDI) **dentro** de la `@Transactional` antes del return. |
| Modificar | `src/main/java/ec/uce/propuestas/documento/DocumentoResource.java` (ET) | Emitir `documento.exportado` con `formato=DOCX` dentro de su método público. |
| No previsto | `src/main/java/ec/uce/propuestas/usuario/audit/EventoLogActividad.java` | STOP — el mapa `detallesEsperados()` está **congelado** desde 033; 039 **no** lo amplía, **no** redefine claves, **no** agrega eventos al enum. Si una clave faltara, 039 reabre 032. |
| No previsto | `src/main/java/ec/uce/propuestas/usuario/audit/service/LogActividadDetalleValidator.java` | STOP — el validador fue materializado por 033; 039 **solo** lo consume (`LogActividadService.emitir(...)` ya lo invoca con `MANDATORY`). 039 **no** crea, **no** modifica, **no** reescribe el validador. |
| Crear | `src/test/java/ec/uce/propuestas/presupuesto/resource/PresupuestoVersionResourceLogAuditoriaIT.java` | 1 escenario. |
| Crear | `src/test/java/ec/uce/propuestas/presupuesto/resource/PresupuestoVigenciaResourceLogAuditoriaIT.java` | 1 escenario. |
| Crear | `src/test/java/ec/uce/propuestas/cronograma/resource/CronogramaResourceLogAuditoriaIT.java` | 6 escenarios (1 por valor canónico). |
| Crear | `src/test/java/ec/uce/propuestas/documento/resource/DocumentoResourceLogAuditoriaIT.java` | 4 escenarios (1 por formato). |
| Modificar | `docs/modulos/panel-admin/00-inventario-trabajo.md` | Marca 039 `DONE`. |
| No previsto | `src/main/java/ec/uce/propuestas/motor/**`, `recalculo/**` | STOP — 039 no toca motor. |
| No previsto | `src/main/java/ec/uce/propuestas/cronograma/export/**` (lanes Plan 031) | STOP — solo se agrega una línea de emisión al final del método público, dentro de la `@Transactional`. |
| No previsto | `src/main/resources/db/migration/**` | STOP. |

## Secuencia TDD (estricta)

### RED

Para cada recurso público:

1. Escribir el `@QuarkusTest` que ejecuta el flujo público
   (HTTP) y asserta 1 fila `log_actividad` con la clave
   correcta.
2. Verificar que el test **falla** porque el emisor no existe
   aún (RED).

### GREEN

Inyectar `LogActividadService` en el servicio público. Llamar
`emitir(...)` con el evento y `detalle` canónicos al final del
método público (después de la materialización de bytes para
`documento.exportado`), dentro de la `@Transactional` exterior.

### TRIANGULATE

- Concurrencia: dos mutaciones simultáneas sobre el mismo
  presupuesto (lock pesimista preservado).
- `cronograma.editado` con cada una de las **6 operaciones
  canónicas**; asegurar que la clave `operacion` del `detalle`
  coincide exactamente con una de las 6 (test focal por valor).
- `documento.exportado` con `formato=MSPDI` y un XSD pinned
  inválido: la exportación no emite (operación rechazada).
- `presupuesto.version_activada` cuando no hay versión previa
  vigente → `presupuestoPreviamenteVigenteId=null`.

### REFACTOR

- Extraer un helper `DocumentoExportadoHelper.emitirExito(...)`
  si la duplicación entre ET DOCX y cronograma XLSX/PDF/MSPDI
  resulta molesta. 039 lo deja inline y decide en 040.

## Catálogo mínimo de pruebas

| Caso | Resultado |
|---|---|
| `presupuesto.version_creada` | 1 fila con `entidadId` UUIDv7 del `Presupuesto` nuevo y `detalle.presupuestoOrigenId/versionNueva`. |
| `presupuesto.version_activada` | 1 fila con `entidadId` UUIDv7 del `Presupuesto` activado y `detalle.version/presupuestoPreviamenteVigenteId`. |
| `cronograma.editado` × 6 operaciones canónicas | 6 filas con `detalle.operacion` correcta (`configurar`, `programar.reemplazar_avances`, `programar.distribuir_uniforme`, `programar.mover_segmento`, `programar.redimensionar_segmento`, `revisar`). |
| `documento.exportado` XLSX | 1 fila con `formato=XLSX`. |
| `documento.exportado` PDF | 1 fila con `formato=PDF`. |
| `documento.exportado` MSPDI | 1 fila con `formato=MSPDI`. |
| `documento.exportado` DOCX (ET) | 1 fila con `formato=DOCX`. |
| Sin PII en `detalle` | 0 matches regex. |
| Sin emisión en lectura | `GET` no produce fila. |
| Sin emisión en operaciones rechazadas | preflight 409 no produce fila. |
| Motor intacto | GM-19/GM-20 aceptados y GM-24 omitido según línea base; sin desviación nueva. |
| Plan 031 intacto | `git diff --stat` sobre `cronograma/export/**`, `BloqueoExportDetalle`, `BloqueoExportResponse`, `CronogramaDocumentoResource` solo añade línea de emisión. |
| Legacy V004 excluido | ningún test de 039 emite `base.insumos.copiada`, `rubro.creado`, `cronograma.creado` o `presupuesto.vigente_marcado`. |

## Comandos de verificación

```bash
cd /home/kaandradec/Documents/workspace/uce/proyecto-grado/thesis-back-quarkus

# Focales.
./gradlew test --tests 'ec.uce.propuestas.presupuesto.resource.Presupuesto*LogAuditoriaIT' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.cronograma.resource.CronogramaResourceLogAuditoriaIT' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.documento.resource.DocumentoResourceLogAuditoriaIT' \
  -Dquarkus.http.test-port=0 --console=plain

# Regresión.
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.cronograma.*' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.documento.*' \
  -Dquarkus.http.test-port=0 --console=plain

# Motor: la suite agrega GM-19/GM-20 aceptados y GM-24 omitido según la línea base.
./gradlew test --tests 'ec.uce.propuestas.motor.*' \
  -Dquarkus.http.test-port=0 --console=plain

# Plan 031 preservado: `git diff --stat` sobre `cronograma/export/**`
# solo añade líneas de emisión; ningún cambio en
# `BloqueoExportDetalle`, `BloqueoExportResponse`,
# `CronogramaDocumentoResource` excepto una línea de emisión al final
# dentro de la @Transactional.
git diff --stat -- 'src/main/java/ec/uce/propuestas/cronograma/export/**' \
  -- 'src/main/java/ec/uce/propuestas/documento/CronogramaDocumentoResource.java' \
  -- 'src/main/java/ec/uce/propuestas/cronograma/dto/BloqueoExportDetalle.java' \
  -- 'src/main/java/ec/uce/propuestas/cronograma/dto/BloqueoExportResponse.java'
# esperado: solo inserciones de líneas `log.emitir(...)`; cero
#           cambios en la lógica de export.

# Motor y recalculo intactos.
git diff --name-only -- 'src/main/java/ec/uce/propuestas/motor/**' \
  -- 'src/main/java/ec/uce/propuestas/recalculo/**'
# esperado: vacío.

# Sin migración nueva.
git diff --name-only -- 'src/main/resources/db/migration/**'
# esperado: vacío.
```

No se predicen conteos de suite completa. El orquestador decide.

## Completion checklist (039)

- [ ] 4 eventos emitidos con la clave D-13 exacta.
- [ ] Cada evento tiene `detalle` con el set de claves canónicas
      (decisión 56).
- [ ] `cronograma.editado.operacion` ∈ {6 valores canónicos
      cerrados}.
- [ ] `documento.exportado.formato` ∈ {XLSX, PDF, MSPDI, DOCX}.
- [ ] `presupuesto.version_creada` y `presupuesto.version_activada`
      emitidos dentro del lock pesimista preservado.
- [ ] Emisión de `documento.exportado` dentro de la `@Transactional`
      antes del return; TOCTOU de Plan 031 preservado.
- [ ] Plan 031 intacto (cero cambios en la lógica de export).
- [ ] Motor intacto (agregado XML del comando: GM-19/GM-20 aceptados y GM-24 omitido según línea base).
- [ ] Ninguna migración nueva.
- [ ] Legacy V004 excluido (ningún emisor con esos nombres).
- [ ] `git diff --check` limpio.

## Handoff al siguiente plan

Cuando 039 cierre, el orquestador inicia **040** (integración +
piloto SUS 1–2). 040 corre regresiones, Bruno admin, Graphify,
docs y artefactos de piloto SUS.