# Estado actual del backend — `thesis-back-quarkus`

**Última actualización:** 2026-09-09
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
| I-11 | Panel Super-Admin y piloto SUS | ✅ DONE técnico / piloto SUS 1–2 pendiente — Planes 032–040 |
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

## I-11 — Panel Super-Admin y piloto SUS

> **Cierre técnico al 2026-09-09:** I-11 está **DONE técnico / piloto SUS 1–2 pendiente**. Los Planes 032–040 están DONE técnicamente; el piloto humano no se ha ejecutado por falta de participantes. La secuencia ejecutable vive en
> [`plans/panel-admin/`](../plans/panel-admin/README.md); consultar el
> [`acta firmada`](modulos/panel-admin/00-acta-reconciliacion.md) y el
> [`inventario operativo`](modulos/panel-admin/00-inventario-trabajo.md).

| Plan | Alcance | Estado |
|---|---|---|
| 032 | Sincronización canónica e inventario definitivo | **DONE (2026-09-07)** — acta firmada + inventario publicado |
| 033 | Base de `log_actividad`, catálogo D-13 y consulta admin | **DONE (2026-09-08)** — V010, 26 eventos, emisor `MANDATORY`, JSONB vía `ObjectMapper`, endpoint `SUPER_ADMIN` |
| 034 | Gestión de usuarios e invitaciones de 72 h | **DONE (2026-09-08)** |
| 035 | Auditoría/cierre de bases centrales ya existentes | **DONE (2026-09-08)** |
| 036 | Plantillas APU `SISTEMA` | **DONE (2026-09-08)** |
| 037 | Parámetros del sistema y valores de referencia | **DONE (2026-09-08)** |
| 038 | Instrumentación D-13 en identidad y catálogos | **DONE (2026-09-08)** |
| 039 | Instrumentación D-13 en presupuesto, cronograma y documentos | **DONE (2026-09-08)** |
| 040 | Integración, Bruno, cierre técnico y piloto SUS | **DONE técnico (2026-09-09); piloto SUS 1–2 pendiente** |

Los Planes 032–040 están cerrados técnicamente. Se conservan como decisiones diferidas a I-12: self-delete, last-active SUPER_ADMIN, cambio de email admin, primer SUPER_ADMIN bootstrap y `proyecto.duplicado`. El piloto SUS 1–2 requiere frontend y participantes humanos; no se fabrican resultados. La medición SUS completa con **n ≥ 5** pertenece a I-12.

### Siguiente acción

- Planificar I-12 en una sesión futura; esa planificación es separada y no se abre dentro de 040.
- Ejecutar el piloto SUS 1–2 cuando estén disponibles el frontend y 1–2 participantes, registrando evidencia real.

## Lo que falta

1. Coordinar frontend y participantes para ejecutar el piloto SUS 1–2 y registrar evidencia real.
2. Planificar y ejecutar I-12: hardening, mediciones finales, SUS n ≥ 5 y paquete de evidencias de tesis.
3. Probar el pipeline CI/CD en el proveedor remoto; la evidencia local no sustituye la primera ejecución real del workflow.

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
