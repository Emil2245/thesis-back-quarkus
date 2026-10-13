# Investigaciones de optimización del backend

La revisión inicial identifica **riesgos estáticos, no problemas de rendimiento
medidos**. No aplica correcciones ni confirma prioridades para producción.

## Lectura

- [01 — Revisión inicial](01-revision-inicial.md): patrones, contraevidencia y
  condiciones necesarias para corroborarlos.
- Pendiente: `02-prioridad-alta-proyectos-grandes.md`, investigación separada
  para decidir únicamente prioridades altas con evidencia suficiente.

## Límites

El objetivo posterior es evaluar proyectos de **300–1000 APUs**; no representa
un volumen observado en Centro Médico Tulcán. Las mediciones de esa referencia
pertenecen al informe posterior, no a esta revisión.

No hay cambios de código, configuración o datos. Las propuestas no autorizan
alterar cálculos, consistencia, bloqueos ni contratos documentales. No se
prometen latencias, porcentajes de mejora o umbrales sin pruebas.
