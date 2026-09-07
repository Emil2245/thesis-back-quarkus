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
| I-11 | Panel Super-Admin y piloto SUS | 📝 PLANIFICADO — Planes 032–040, todavía sin implementar |
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

## I-11 planificada — Panel Super-Admin

La secuencia ejecutable está en
[`plans/panel-admin/`](../plans/panel-admin/README.md). La planificación no
equivale a implementación.

| Plan | Alcance | Estado |
|---|---|---|
| 032 | Sincronización canónica e inventario definitivo | TODO — gate documental |
| 033 | Base de `log_actividad`, catálogo D-13 y consulta admin | TODO |
| 034 | Gestión de usuarios e invitaciones de 72 h | TODO |
| 035 | Auditoría/cierre de bases centrales ya existentes | TODO |
| 036 | Plantillas APU `SISTEMA` | TODO |
| 037 | Parámetros del sistema y valores de referencia | TODO |
| 038 | Instrumentación D-13 en identidad y catálogos | TODO |
| 039 | Instrumentación D-13 en presupuesto, cronograma y documentos | TODO |
| 040 | Integración, Bruno, cierre técnico y piloto SUS | TODO |

El Plan 032 es un gate obligatorio: debe resolver las divergencias de contrato,
identificadores UUIDv7, paginación, semántica de invitación y referencias del
log antes de autorizar código. Los Planes 033–040 se ejecutan secuencialmente.
El piloto SUS de I-11 requiere frontend y 1–2 participantes humanos; sus
resultados nunca se fabrican. La medición SUS completa con **n ≥ 5** pertenece
a I-12.

## Lo que falta

1. Ejecutar secuencialmente los Planes **032–040** de I-11.
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
  `log_actividad` deben reconciliarse documentalmente en Plan 032; no se edita
  una migración aplicada ni se admiten como nuevos eventos runtime.
- P-39 y los defaults de P-41 ya existen: I-11 los audita y completa sin
  reimplementarlos.
- Los cambios verificados de Plan 031 siguen sin commit.

## Fuentes de detalle

- Estado y planes ejecutados: [`plans/README.md`](../plans/README.md).
- Planificación I-11: [`plans/panel-admin/README.md`](../plans/panel-admin/README.md).
- Módulo presupuesto: [`docs/modulos/05-presupuesto/00.md`](modulos/05-presupuesto/00.md).
- Módulo cronograma: [`docs/modulos/06-cronograma/00.md`](modulos/06-cronograma/00.md).
- Decisiones y contrato canónico: `../thesis-docs/plan/`.
