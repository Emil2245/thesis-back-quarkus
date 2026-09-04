# Estado actual del backend — `thesis-back-quarkus`

**Última actualización:** 2026-09-01
**Proyecto:** Plataforma SERCOP de propuestas técnico-económicas (backend Quarkus).

## Resumen

El backend tiene implementadas y verificadas las iteraciones **I-01 a I-07**:
fundaciones, autenticación, proyectos, insumos, motor de cálculo, APU,
recálculo y presupuesto.

La suite completa registra **438 pruebas**:

- **435 pass**.
- **GM-19 y GM-20:** residuales aceptados y documentados; no se reabre el motor.
- **GM-24:** omitido por fixture upstream de EMELNORTE.
- **0 errores**.

También están verificados Spotless, build sin pruebas, `git diff --check` y
la colección Bruno de presupuesto (**23/23 requests, 83/83 tests**).

## Estado por iteración

| Iteración | Área | Estado |
|---|---|---|
| I-01 | Bootstrap, esquema, autenticación | ✅ DONE |
| I-02 | Motor de cálculo | ✅ DONE — GM-19/GM-20 aceptados; GM-24 omitido |
| I-03 | Proyectos, parámetros y firmantes | ✅ DONE |
| I-04 | Insumos, bases e importación CSV | ✅ DONE |
| I-05 | APU núcleo: filas M/N/O/P y HM | ✅ DONE |
| I-06 | APU avanzado, plantillas, ET, UUIDv7 y decisiones N04 | ✅ DONE / parcial documentado |
| I-07 | Presupuesto, recálculo, versionado y validación | ✅ DONE — Planes 019–025 |
| I-08 | Cronograma base | ⬜ Pendiente de planificación — próximo Plan 026 |
| I-09 | Cronograma visual y sincronía | ⬜ Pendiente |
| I-10 | Exportación SERCOP `.xlsx` / `.pdf` | ⬜ Pendiente |
| I-11 | Panel Super-Admin y piloto SUS | ⬜ Pendiente |
| I-12 | Validación final, hardening y mediciones | ⬜ Pendiente |

## Funcionalidad backend actualmente disponible

- Autenticación, perfil y recuperación de acceso.
- CRUD de proyectos, firmantes, parámetros e insumos.
- Bases de insumos, copia de bases e importación CSV.
- CRUD de APUs, filas M/N/O/P, fila HM y cálculo.
- Plantillas de APU y de proyecto.
- Recálculo por versión, APU e insumo.
- Presupuestos con capítulos jerárquicos y rubros 1:1 con APU.
- Totales, resumen por componentes y validación de integridad.
- Versiones de presupuesto: deep copy, vigente, comparación y eliminación protegida.
- Fronteras REST con UUIDv7 y aislamiento por propietario.

## Lo que falta en el backend

1. **I-08 — Cronograma base:** entidades, configuración y actividades.
2. **I-09 — Cronograma visual y sincronía:** períodos, avances, Gantt y
   detección de desactualización.
3. **I-10 — Exportación SERCOP:** entregables `.xlsx` y `.pdf`, validaciones
   bloqueantes y parse-back.
4. **I-11 — Panel Super-Admin:** usuarios, bases centrales, plantillas,
   parámetros y logs.
5. **I-12 — Cierre:** hardening, mediciones de tesis, SUS y desempeño.
6. **CI/CD:** el repositorio todavía no tiene un pipeline `.github/` activo.

## Pendientes conocidos y aceptados

- GM-19/GM-20 mantienen los residuales documentados por decisión del autor; no
  son un bloqueo para continuar.
- GM-24 permanece omitido porque el fixture upstream está incompleto.
- Los planes de I-08 a I-12 todavía deben redactarse. La siguiente tarea es
  **planificar el Plan 026 para I-08**.

## Fuentes de detalle

- Estado y planes ejecutados: [`plans/README.md`](../plans/README.md).
- Módulo presupuesto: [`docs/modulos/05-presupuesto/00.md`](modulos/05-presupuesto/00.md).
- Decisiones y contrato: `../thesis-docs/plan/`.
