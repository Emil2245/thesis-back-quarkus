# Exportación de la versión seleccionada del presupuesto: XLSX y PDF

**Estado: BUD-01…04 cerrados en el alcance del plan 02.** Evidencia y límites en
[02 — Backend presupuesto](../../docs/modulos/07-exportacion-presupuestos-apus/02-backend-presupuesto.md).
No es cierre de backend integral, APUs, frontend ni suite global.

## Autorización y tareas

Usuario autorizó solo plan 02 en `feat/document-export-contract`: sin writers APU,
frontend, migraciones, mutaciones del dominio/BD del usuario, commits o push.
Preservar trabajo sucio previo, eliminaciones Compunex y muestras originales.

- [x] BUD-01: writers puros, RED/GREEN observado, OOXML decimal exacto y paridad persistida.
- [x] BUD-02: descarga de versión seleccionada, captura consistente, gates HTTP y concurrencia del servicio.
- [x] BUD-03: regresión independiente, mediciones de 298 rubros y revisión visual real acotada de Calc/PDF y referencias.
- [x] BUD-04: reconciliar plan 02/índice, registrar límites y rollback; no declarar 03/04 implementados.

## Evidencia resumida

| Unidad | Resultado real |
|---|---|
| BUD-01 | RED 6 fallos por bytes vacíos; GREEN independiente 8 tests/2 suites |
| BUD-02 | RED 354 tests/1 fallo 404 esperado 200; GREEN independiente 359 tests/39 suites, 6m32, cero fallos/errores/omitidos |
| BUD-03 inicial | 363 tests verdes no bastaron: Calc recortaba texto gigante y PDF vertical perdía headers |
| Corrección | RED 12 tests/1 fallo de altura; GREEN 13; chunks Unicode y filas físicas sin duplicar identidad/importes |
| Final | 364 tests/40 suites, cero fallos/errores/omitidos, 7m11; `build/bud03-layout-independent.log`; Spotless/diff PASS |
| Visual | 84 páginas frescas inspeccionadas con LibreOffice 26.2.6.3 headless/Poppler 110 dpi; defectos detectados resueltos en fixtures |
| BUD-04 | Documentación pasiva: no RED conductual significativo; enlaces/checklists/diff, sin reejecutar tests/build |

Comando independiente final:

```bash
./gradlew --offline --console=plain test --rerun-tasks --tests 'ec.uce.propuestas.documento.*' --tests 'ec.uce.propuestas.presupuesto.*' --tests 'ec.uce.propuestas.cronograma.*'
```

## Invariantes y alcance demostrado

Snapshot seleccionado inmutable: totales, cantidad, PU y PT persistidos; nunca
motor, CT APU ni sumas reconstruidas. Display HALF_UP separado de PU canónica
DOWN. BigDecimal exacto en OOXML, con límite nativo Excel de 15 dígitos. Prueba
helper >32767 caracteres no equivale a artefacto leído de ese tamaño.

GET presupuesto real XLSX/PDF: `@Blocking`, captura única preflight/snapshot,
gates antes de generar, render `NOT_SUPPORTED`, filename seguro UUID/versión;
200/400/401/404/409. Vacío 404 → 409 aprobado por usuario; APU descarga sigue 404.
Concurrencia: alternativa CDI equivalente REQUIRES_NEW con commits reales de
cantidad/PU/proyecto/firmante, primera salida antigua y siguiente nueva; no HTTP
productivo concurrente. Seam Runnable de captura preexistente en 01, sin hooks
nuevos en servicio/resource BUD-02.

298 rubros: 16 SQL de captura/1473 ms, render 0 SQL, servicio 16 por salida;
bytes 25748/74793/77188. Tiempos puros 1188/452/109 ms, servicio 663/519/478 ms
(frío/caliente mezclados). Heap 469900616 → 514257432; picos JMX de vida del
proceso, no asignaciones ni duración de locks. Sin umbrales inventados.

Comparación Tulcán primera página y EMELNORTE página 365 del convertido (462
páginas/11 hojas): estructura frente a primera/última página del seed vertical
y Calc, no revisión de todos los presupuestos, totales o aprobaciones. Original
EMELNORTE intacto. A4 sin CPC/carátula/firma y reglas financieras históricas
preservadas. Aceptación limitada a fixtures y lectores headless descritos en 02,
no Excel interactivo ni compatibilidad universal.

## Límites, preservación y rollback

GM19/GM20 previos confirmados en HEAD, GM24 omitido y ScheduleIT con expectativa
V019 obsoleta se reportan sin arreglos ajenos. Sin suite global, deployment ni UI;
RDD nativo apagado/no evaluable, verificación independiente realizada.
Evidencia solo en build. Graphify externo untracked/backend ignorado y Compunex
eliminados se preservan; sin limpieza, staging, .gitignore ni commits.

Rollback revisado: retirar writers/pruebas independientemente; retirar conexión
de descarga con consumidores nuevos, manteniendo infraestructura 01 y cronograma.
Planes 03/04 no implementados; BD del usuario intacta.
