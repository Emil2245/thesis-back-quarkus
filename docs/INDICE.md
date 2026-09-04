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

## 4. Trabajo pendiente del backend

El siguiente trabajo es planificar **Plan 026 — I-08: cronograma base**.
Después siguen:

1. I-08 — cronograma base.
2. I-09 — cronograma visual, períodos y sincronía.
3. I-10 — exportación SERCOP `.xlsx` / `.pdf`.
4. I-11 — panel Super-Admin.
5. I-12 — hardening, mediciones y cierre.
6. CI/CD — crear el pipeline `.github/` pendiente.

Los planes de I-08 a I-12 todavía no están redactados como planes ejecutables.

## 5. Documentación histórica

- [`modulos/planes-para-estar-al-dia/00.md`](modulos/planes-para-estar-al-dia/00.md)
  — índice histórico de I-06. No reabrir ni renumerar.
- [`modulos/planes-para-estar-al-dia/`](modulos/planes-para-estar-al-dia/)
  — planes históricos de cierre de I-06.

## Regla de organización

No mover ni renombrar documentos sin actualizar primero sus referencias relativas,
especialmente las referencias a `../thesis-docs/` y a `../plans/`.
