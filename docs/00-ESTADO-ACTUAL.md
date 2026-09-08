# Estado actual del backend — `thesis-back-quarkus`

**Última actualización:** 2026-09-07
**Proyecto:** Plataforma SERCOP de propuestas técnico-económicas (backend Quarkus).

## Resumen

El backend tiene implementadas y verificadas las iteraciones **I-01 a I-10**:
fundaciones, autenticación, proyectos, insumos, motor de cálculo, APU,
recálculo, presupuesto, cronograma y exportación del cronograma.

La última suite completa medida (cierre de Plan 031 al 2026-09-07)
muestra que el motor conserva los mismos residuales históricos
aceptados; este documento **no predice conteos**:

- **GM-19 y GM-20:** únicos fallos; residuales históricos aceptados y
  documentados. No se reabre el motor.
- **GM-24:** única prueba omitida, por fixture upstream incompleto de
  EMELNORTE.
- **0 errores**.

(La única orientación histórica de la suite completa se documenta
en [`plans/panel-admin/README.md`](../plans/panel-admin/README.md);
este doc no la replica.)

También están verificados `spotlessCheck`, `build -x test`, `git diff --check`,
la validación MSPDI contra el XSD oficial de Microsoft Project 2007 y la
colección Bruno de cronograma (**20/20 requests, 70/70 tests**). Plan 031 está
implementado y verificado, pero sus cambios permanecen sin commit porque no se
autorizó esa operación.

## Estado por iteración

| Iteración | Área | Estado |
|---|---|---|
| I-01 | Bootstrap, esquema y autenticación | ✅ DONE |
| I-02 | Motor de cálculo | ✅ DONE — GM-19/GM-20 aceptados; GM-24 omitido |
| I-03 | Proyectos, parámetros y firmantes | ✅ DONE |
| I-04 | Insumos, bases e importación CSV | ✅ DONE |
| I-05 | APU núcleo: filas M/N/O/P y HM | ✅ DONE |
| I-06 | APU avanzado, plantillas, ET, UUIDv7 y decisiones N04 | ✅ DONE / parciales históricos documentados |
| I-07 | Presupuesto, recálculo, versionado y validación | ✅ DONE — Planes 019–025 |
| I-08 | Cronograma base y configuración | ✅ DONE — Planes 026–029 |
| I-09 | Vistas, curva S y desactualización | ✅ DONE — Plan 030 |
| I-10 | Exportación XLSX/PDF/MSPDI | ✅ DONE — Plan 031 |
| I-11 | Panel Super-Admin y piloto SUS | 🚧 EN PROGRESO — Plan032 DONE (2026-09-07); 033–040 pendientes |
| I-12 | Validación final y hardening | ⬜ Pendiente de planificación |

## Funcionalidad backend disponible

- Autenticación, perfil, recuperación de acceso y aceptación de invitaciones.
- CRUD de proyectos, firmantes, parámetros e insumos.
- Bases de insumos, copia de bases e importación CSV.
- CRUD de APUs, filas M/N/O/P, fila HM, plantillas y cálculo.
- Recálculo por versión, APU e insumo.
- Presupuestos con capítulos jerárquicos, rubros, totales, versiones,
  comparación y validación de integridad.
- Cronograma 1:1 por presupuesto, actividades, períodos no consecutivos,
  distribución, avance, Gantt, cronograma valorizado, curva S y detección de
  desactualización.
- Exportación server-side del cronograma a XLSX, PDF y MSPDI XML compatible
  con Microsoft Project 2007, con preflight y parse-back.
- Fronteras REST con UUIDv7 y aislamiento por propietario.
- Administración de bases centrales P-39 ya implementada desde Plan 015bis.
- Lectura y edición de los defaults de `parametros_sistema` ya implementadas
  mediante `/proyectos/parametros-sistema`.

## I-11 en progreso — Panel Super-Admin

> **Estado al 2026-09-07:** I-11 está **EN PROGRESO** con el gate
> documental **Plan 032 DONE**. La secuencia ejecutable vive en
> [`plans/panel-admin/`](../plans/panel-admin/README.md); consultar el
> [`acta firmada`](modulos/panel-admin/00-acta-reconciliacion.md) y el
> [`inventario operativo`](modulos/panel-admin/00-inventario-trabajo.md).

| Plan | Alcance | Estado |
|---|---|---|
| 032 | Sincronización canónica e inventario definitivo | **DONE (2026-09-07)** — acta firmada + inventario publicado |
| 033 | Base de `log_actividad`, catálogo D-13 y consulta admin | **TODO — próxima tarea autorizada** |
| 034 | Gestión de usuarios e invitaciones de 72 h | TODO |
| 035 | Auditoría/cierre de bases centrales ya existentes | TODO |
| 036 | Plantillas APU `SISTEMA` | TODO |
| 037 | Parámetros del sistema y valores de referencia | TODO |
| 038 | Instrumentación D-13 en identidad y catálogos | TODO |
| 039 | Instrumentación D-13 en presupuesto, cronograma y documentos | TODO |
| 040 | Integración, Bruno, cierre técnico y piloto SUS | TODO |

El Plan 032 es un gate obligatorio: debía resolver las divergencias de
contrato, identificadores UUIDv7, paginación, semántica de invitación
y referencias del log antes de autorizar código. **Plan 032 firma
su acta el 2026-09-07** con 21 decisiones locked verbatim (20 originales + adenda firmada D-21), 15 STOP
conditions con disposición explícita (10 CLOSED, 4 DEFERRED a I-12, 1
CLOSED con gate RED-first en 035), el catálogo D-13 verbatim (26
eventos), la matriz canónica `evento → detalle` y la paridad
P-38…P-42 contra la implementación. Decisiones del usuario
registradas: 409 `base-no-archivada` ratificado para `DELETE base
central activa`; `DELETE insumo` con FK real → 409 `insumo-en-uso`
queda **gated por RED-first en 035** (la implementación actual
expone el stub `conteoUsosApu() = 0L` y no satisface hoy 409);
DTO `ParametrosSistemaResponse` seleccionado por 037 (ya no es
condicional); self-delete / last-admin / email admin / primer
SUPER_ADMIN bootstrap y `proyecto.duplicado` diferidos a I-12 por
preferencia explícita (P-09 sigue la decisión histórica N02 §3).
Los Planes 033–040 se ejecutan secuencialmente; 033 es la
**siguiente tarea autorizada**. El piloto SUS de I-11 requiere
frontend y 1–2 participantes humanos; sus resultados nunca se
fabrican. La medición SUS completa con **n ≥ 5** pertenece a
I-12.

### Siguiente acción

- **Plan 033 — Log de actividad — base** (P-42 / US-39 / TC-P42-01..02
  foundation): enum `EventoLogActividad` con 26 verbatim; una
  migración aditiva con el siguiente número disponible (nombre
  neutral `V???__log_actividad_identidad_publica.sql`) que añade
  **dos** columnas nuevas a `log_actividad`:
  `public_id UUID NOT NULL DEFAULT uuidv7()` (D-01) + índice único
  + trigger de inmutabilidad **y** `entidad_public_id UUID NULL`
  sin FK, sin DEFAULT, sin UNIQUE (D-21; server-authored; los logs
  sobreviven al borrado de la entidad afectada); la columna legacy
  `entidad_id BIGINT` (V001 §2.15) permanece inalterada y nunca
  cruza REST; `LogActividadResponse.entidadId` mapea exclusivamente
  desde `entidad_public_id` (V001–V009 intactas); V004 y filas
  pre-033 quedan con `entidad_public_id IS NULL` y el DTO devuelve
  `entidadId: null` para esas filas; `LogActividadService.emitir(...)`
  con `@Transactional(TxType.MANDATORY)`; sin `emitirFailure`,
  `REQUIRES_NEW` ni `codigoError`; solo operaciones exitosas emiten.
  Detalle en [`plans/panel-admin/033-log-actividad-base.md`](../plans/panel-admin/033-log-actividad-base.md).

## Lo que falta

1. Ejecutar secuencialmente los Planes **033–040** de I-11 (032 ya
   cerrado al 2026-09-07 — acta firmada e inventario publicado).
2. Implementar o coordinar las pantallas frontend S-37…S-42 antes del piloto
   SUS.
3. Ejecutar el piloto SUS con 1–2 participantes y registrar evidencia real.
4. Planificar y ejecutar I-12: hardening, mediciones finales, SUS n ≥ 5 y
   paquete de evidencias de tesis.
5. Probar el pipeline CI/CD en el proveedor remoto; la evidencia local no
   sustituye la primera ejecución real del workflow.

## Pendientes conocidos y aceptados

- GM-19/GM-20 conservan los residuales documentados; no bloquean la siguiente
  iteración.
- GM-24 permanece omitido por el fixture upstream incompleto.
- Los cuatro nombres históricos no canónicos sembrados por V004 en
  `log_actividad` quedan **reconciliados documentalmente** en
  [`docs/modulos/panel-admin/00-acta-reconciliacion.md`](modulos/panel-admin/00-acta-reconciliacion.md)
  §2 D-17: son historial legacy (legibles/filtrables por
  `GET /admin/logs?evento=`, nunca se emiten de nuevo, nunca se
  admiten al enum runtime, excluidos de la cobertura de 040);
  no se edita V004 ni se hace backfill. 19 filas en total
  (6 legacy sobre 4 nombres + 13 con claves del catálogo D-13).
- P-39 y los defaults de P-41 ya existen: I-11 los audita y completa sin
  reimplementarlos. P-39 (`AdminBaseCentralResource`) tiene
  409 `base-no-archivada` ya conforme; el mapeo de
  `DELETE insumo` → 409 `insumo-en-uso` queda **gated por RED-first**
  en Plan 035. P-41 conserva la ruta canónica
  `/proyectos/parametros-sistema`; Plan 037 introduce el DTO
  `ParametrosSistemaResponse` para dejar de exponer la entidad JPA.
- Los cambios verificados de Plan 031 siguen sin commit (preservados
  intactos, fuera del alcance de 032).

## Fuentes de detalle

- Estado y planes ejecutados: [`plans/README.md`](../plans/README.md).
- Acta e inventario I-11:
  [`docs/modulos/panel-admin/00-acta-reconciliacion.md`](modulos/panel-admin/00-acta-reconciliacion.md)
  + [`docs/modulos/panel-admin/00-inventario-trabajo.md`](modulos/panel-admin/00-inventario-trabajo.md).
- Planificación I-11: [`plans/panel-admin/README.md`](../plans/panel-admin/README.md).
- Módulo presupuesto: [`docs/modulos/05-presupuesto/00.md`](modulos/05-presupuesto/00.md).
- Módulo cronograma: [`docs/modulos/06-cronograma/00.md`](modulos/06-cronograma/00.md).
- Decisiones y contrato canónico: `../thesis-docs/plan/`.
