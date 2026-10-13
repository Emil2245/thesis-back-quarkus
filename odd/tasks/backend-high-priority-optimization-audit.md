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
- [x] O02 — CERRADO: Tulcán medido con consultas de solo lectura; informe
      publicado y verificado con una prioridad alta preventiva de escalabilidad.
      Commit local `063d10ee3d4fbc7885333d97b1ef46a85d73312c`.
- [x] O03 — CERRADO: formato, 30 enlaces locales, 27 anclas y 25 rangos
      verificados; solo documentación y seguimiento, sin cambios de producto.
      Commit de verificación `cba2d4028b019b747acab8d422d70e13fd71bedd`.
      Investigación cerrada en su rama; `main` conserva `0757d9a`.

## Evidencia y límites de la investigación

- Tulcán v1: 298 APUs, 1192 secciones, 2583 detalles y 33 capítulos.
- Reproducción SQL estrecha: detalles, 1192 consultas / 95,999 ms frente a
  un lote / 3,291 ms; las 2583 filas son equivalentes. Secciones, 298 consultas /
  32,692 ms frente a un lote / 1,660 ms; equivalencia no verificada por error
  del parser de enum. No son tiempos de ORM, recálculo ni endpoint completo.
- Primer script SQL: exit 3 por error del arnés después del rollback. Corrida
  corregida: exit 0, READ ONLY, Repeatable Read, límites 15 s / 1 s y rollback.
- Ensayo CPU sintético: resultados impresos, pero fallo de preferencias y
  timeout al cerrar JShell. Evidencia provisional, no corrida completa PASS.
- Los restantes candidatos no justifican prioridad alta con estos datos.
  Rendimiento end-to-end, frecuencia y concurrencia siguen sin medirse.
- Formato, enlaces, anclas, integridad y alcance documental: verificados.
  No se ejecutaron suites, builds ni servicios mutantes; sin cambios de código,
  configuración o datos. Optimizar requiere una nueva autorización del usuario.
- Sin merge, push ni PR: la autorización anterior de integración de plan04 no
  se extiende a esta investigación.

## Criterio de prioridad

Alta requiere un camino real relevante, crecimiento evitable demostrado y
magnitud suficiente para el tamaño objetivo. Las recomendaciones genéricas,
microoptimizaciones y riesgos sin evidencia permanecen fuera del informe alto.
Documentación pasiva: no hay RED de comportamiento; se aplican comprobaciones
estructurales y readback, sin inventar pruebas funcionales ni resultados verdes.
