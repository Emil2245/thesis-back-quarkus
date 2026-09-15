# Índice de documentación del backend

Punto de entrada para conocer el estado actual y continuar la implementación del
backend `thesis-back-quarkus`.

## 1. Estado actual

- [`00-ESTADO-ACTUAL.md`](00-ESTADO-ACTUAL.md) — resumen corto del backend:
  qué está implementado, qué falta y cuál es el siguiente paso.
- [`modulos/README.md`](modulos/README.md) — estado general por módulo.
- [`../plans/README.md`](../plans/README.md) — estado detallado de los planes
  ejecutados y pendientes.

## 2. Arquitectura y base técnica

- [`01-ARQUITECTURA.md`](01-ARQUITECTURA.md) — arquitectura del backend.
- [`02-TECNOLOGIAS.md`](02-TECNOLOGIAS.md) — tecnologías y decisiones técnicas.
- [`03-BASE-DATOS.md`](03-BASE-DATOS.md) — base de datos y migraciones.
- [`04-SEED-ESCENARIOS.md`](04-SEED-ESCENARIOS.md) — escenarios de prueba y
  datos semilla.
- [`007-migracion-gradle.md`](007-migracion-gradle.md) — migración a Gradle.
- [`012-format-lint.md`](012-format-lint.md) — formato y lint.

## 3. Módulos implementados o en progreso

- [`modulos/01-proyecto.md`](modulos/01-proyecto.md) — proyectos.
- [`modulos/02-insumo.md`](modulos/02-insumo.md) — insumos y bases.
- [`modulos/03-apu.md`](modulos/03-apu.md) — APU núcleo.
- [`modulos/04-apu-avanzado.md`](modulos/04-apu-avanzado.md) — APU avanzado.
- [`modulos/05-presupuesto/00.md`](modulos/05-presupuesto/00.md) — presupuesto,
  recálculo, versionado y validación. I-07 está DONE.
- [`modulos/06-cronograma/00.md`](modulos/06-cronograma/00.md) — cronograma,
  vistas y exportación. I-08/I-09/I-10 están DONE.
- [`modulos/panel-admin/00-acta-reconciliacion.md`](modulos/panel-admin/00-acta-reconciliacion.md)
  + [`00-inventario-trabajo.md`](modulos/panel-admin/00-inventario-trabajo.md) —
  panel Super-Admin. I-11 está DONE técnico (piloto SUS 1–2 pendiente).
- [`../plans/plans_busquedas_plantilla/README.md`](../plans/plans_busquedas_plantilla/README.md)
  — búsqueda FTS y creación de APUs desde plantillas. Plans 001–005
  DONE 2026-09-10/11.

## 4. Trabajo pendiente del backend

I-01 a I-11 (técnico) están implementadas y verificadas; el piloto SUS 1–2
queda pendiente de frontend y participantes humanos. Lo único pendiente
de planificar/ejecutar es:

1. I-12 — hardening, mediciones finales, SUS n ≥ 5 y paquete de evidencias
   de tesis.
2. El piloto SUS 1–2 cuando estén disponibles el frontend y 1–2
   participantes, registrando evidencia real (no se fabrican resultados).
3. Resolver y documentar el contrato funcional de bases PERSONAL: el backend
   gestiona el contenedor y resuelve insumos PERSONAL internamente, pero no
   expone todavía el flujo completo para alimentarlo y ofrecerlo como origen
   del selector; el frontend mantiene el Plan 058 bloqueado.
4. Probar el pipeline CI/CD en el proveedor remoto; la evidencia local no
   sustituye la primera ejecución real del workflow.

**Planes ya cerrados como evidencia viva** (no se reabren):

- I-08/I-09/I-10 — `plans/026`–`plans/031` (cronograma + export), todos
  DONE; detalle en [`docs/modulos/06-cronograma/00.md`](modulos/06-cronograma/00.md).
- I-11 — `plans/panel-admin/032`–`040`, todos DONE técnico; piloto SUS
  1–2 pendiente.
- Paquete de búsqueda de plantillas (Plans 001–005 bajo
  [`plans/plans_busquedas_plantilla/`](../plans/plans_busquedas_plantilla/README.md))
  DONE 2026-09-10/11.

## 5. Documentación histórica

- [`modulos/planes-para-estar-al-dia/00.md`](modulos/planes-para-estar-al-dia/00.md)
  — índice histórico de I-06. No reabrir ni renumerar.
- [`modulos/planes-para-estar-al-dia/`](modulos/planes-para-estar-al-dia/)
  — planes históricos de cierre de I-06.

## Regla de organización

No mover ni renombrar documentos sin actualizar primero sus referencias relativas,
especialmente las referencias a `../thesis-docs/` y a `../plans/`.
