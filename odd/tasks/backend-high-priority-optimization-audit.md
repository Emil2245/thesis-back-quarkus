# Backend — investigación de optimizaciones prioritarias

## Alcance y autorización

Guardar la revisión inicial en `docs/optimizaciones/` y revisar nuevamente solo
problemas importantes para proyectos de 300–1000 APUs. Usar Centro Médico Tulcán
como referencia si el backend y PostgreSQL disponibles permiten mediciones.
El usuario autoriza pruebas de investigación, no cambios de producto ni datos.

Rama: `feat/backend-optimization-audit`; base
`0757d9acd1adc6f60127466070f5a2bdc714ec76`.
Superficies de escritura: este seguimiento y los documentos nuevos
`docs/optimizaciones/README.md`, `01-revision-inicial.md` y
`02-prioridad-alta-proyectos-grandes.md`.

## Restricciones

- Solo lecturas SQL: transacciones READ ONLY, límites de tiempo y consultas
  acotadas; no INSERT/UPDATE/DELETE, DDL, ANALYZE ni cambios de configuración.
- EXPLAIN ANALYZE únicamente de SELECT no mutante, sin FOR UPDATE ni funciones
  con efectos laterales. No recálculos, copias, imports ni suites contra la DB.
- No consultar usuarios, credenciales, tokens, claves o contenido personal.
- Mediciones observadas, reproducciones aisladas y proyecciones se distinguen;
  no atribuir tiempos simulados al endpoint ni inferir contención sin medirla.
- No optimizaciones de código, migraciones, publicación o nuevos servicios.
- No degradar consistencia, cálculos canónicos, bloqueos o snapshots.

## Tareas recuperables

- [x] O01 — CERRADO: revisión inicial e índice guardados y verificados;
      enlaces, anclas, formato e integridad PASS. Commit local
      `c5f317fa8867b02fa5a435802c3fd032cb847489` (solo documentación).
- [ ] O02 — EN CURSO: medir escenarios de solo lectura sobre Tulcán y evaluar
      escalado a 300–1000 APUs; publicar únicamente prioridades altas justificadas
      y los candidatos descartados o no demostrados. Commit local de informe.
- [ ] O03 — PENDIENTE: verificar evidencia, enlaces, integridad y estado final;
      cerrar seguimiento e integración local conforme a la política autorizada.

## Criterio de prioridad

Alta requiere un camino real relevante, crecimiento evitable demostrado y
magnitud suficiente para el tamaño objetivo. Las recomendaciones genéricas,
microoptimizaciones y riesgos sin evidencia permanecen fuera del informe alto.
Documentación pasiva: no hay RED de comportamiento; se aplican comprobaciones
estructurales y readback, sin inventar pruebas funcionales ni resultados verdes.
