# Revisión inicial: riesgos de escalado del backend

**Resultado: seis riesgos estáticos; ninguno medido ni corregido.** Las
prioridades son provisionales y no equivalen a optimizaciones necesarias.
Solo una investigación posterior puede promover candidatos a prioridad alta.

- Fecha de documentación, obtenida del host: **2026-10-13**.
- Base revisada: `0757d9acd1adc6f60127466070f5a2bdc714ec76`.
- Rama de documentación: `feat/backend-optimization-audit`.
- Procedencia: revisión inicial conservada en la observación Engram 2781.
- Objetivo posterior: **300–1000 APUs**, no volumen observado de Tulcán.

## Alcance y método

Inspección estática de consultas explícitas, recorridos, límites y fronteras
transaccionales. La revisión original partió de la base limpia indicada.
No se ejecutaron pruebas, compilaciones, benchmarks, perfiles, llamadas API ni
consultas de runtime o de base de datos. No hubo escrituras de producto o datos.
Este documento conserva esa investigación; no es una segunda medición.

Las expresiones de crecimiento describen código, no latencia ni planes SQL.
Usamos C = capítulos, A = APUs, S = secciones y D = detalles procesados.
Cardinalidad real, caché, selectividad, concurrencia y hardware quedan pendientes.

## Mapa de candidatos

| #   | Riesgo estático                                        | Prioridad provisional | Corroboración necesaria                                             |
| --- | ------------------------------------------------------ | --------------------- | ------------------------------------------------------------------- |
| 1   | Consultas repetidas y consolidación por capítulo       | Alta candidata        | SQL efectivo, cardinalidades y costo del recálculo completo         |
| 2   | Lecturas duplicadas al calcular APUs afectados         | Alta candidata        | Reutilización real, caché y peso sobre una operación representativa |
| 3   | Copia dentro del bloqueo de proyecto                   | Alta candidata        | Duración de la transacción, espera concurrente y costo de copia     |
| 4   | Filtrados completos repetidos en proyección documental | Alta candidata        | CPU y asignaciones según A, S, D y C                                |
| 5   | Materialización XLSX en memoria                        | Media candidata       | Pico de heap, tamaño del archivo y concurrencia                     |
| 6   | Búsquedas parciales y paginación                       | Media candidata       | Planes reales, selectividad, conteo y límites efectivos             |

La prioridad expresa interés inicial de investigación, no una clasificación
confirmada para el informe posterior de prioridades altas.

## 1. Recálculo completo: consultas y consolidación

Evidencia: [VersionSnapshotBuilder:92–106](../../src/main/java/ec/uce/propuestas/recalculo/internal/VersionSnapshotBuilder.java#L92)
y [162–258](../../src/main/java/ec/uce/propuestas/recalculo/internal/VersionSnapshotBuilder.java#L162);
[RecalculoService:197–260](../../src/main/java/ec/uce/propuestas/recalculo/RecalculoService.java#L197).

El constructor consulta hijos y rubros por capítulo, proyecto y secciones por
APU, y detalles por sección. Aproximación estructural: **1 + 2C + 2A + S**, más
consultas fijas del cronograma. No es un conteo observado. La consolidación
agrega consultas de rubros por capítulo y recorre totales con crecimiento O(C²).

Contraevidencia: existe deduplicación de consolidación por versión; no todo
cambio dispara trabajo para todas las versiones.
Propuesta mínima: estudiar carga agrupada y agrupación previa por capítulo,
sin cambiar orden, redondeos ni reglas canónicas del motor.

## 2. APUs afectados: lecturas repetidas

Evidencia: [ApuCalculoService:75–94](../../src/main/java/ec/uce/propuestas/apu/service/ApuCalculoService.java#L75)
y [128–174](../../src/main/java/ec/uce/propuestas/apu/service/ApuCalculoService.java#L128);
[RecalculoService:90–109](../../src/main/java/ec/uce/propuestas/recalculo/RecalculoService.java#L90)
y [145–181](../../src/main/java/ec/uce/propuestas/recalculo/RecalculoService.java#L145).

Cálculo y persistencia posterior vuelven a cargar secciones/detalles y consultan
el proyecto dos veces; el recálculo busca además el vínculo con rubros por APU.
Propuesta mínima: evaluar reutilización de lecturas dentro de la misma operación.

Contraevidencia: ya se filtran APUs afectados. `Insumo.findById` por detalle
puede resolver desde caché de primer nivel; **no garantiza SQL por detalle**.
Una FK escalar tampoco demuestra por sí sola un N+1 de entidades.

## 3. Versionado: trabajo bajo bloqueo

Evidencia: [VersionadoService:162–207](../../src/main/java/ec/uce/propuestas/presupuesto/service/VersionadoService.java#L162)
y [436–489](../../src/main/java/ec/uce/propuestas/presupuesto/service/VersionadoService.java#L436).

El `FOR UPDATE` del proyecto abarca copia y recálculo final. La copia busca el
rubro destino con SELECT nativo por actividad y realiza INSERT individuales.
Es una exposición potencial a contención, **no contención observada**.
Propuesta mínima: investigar reutilización del mapeo de rubros para reducir
trabajo dentro de la sección crítica, conservando atomicidad.

Contraevidencia: el bloqueo protege identidad y máximo de versión; no retirarlo
sin demostrar equivalencia. `flush` no es commit, y generación IDENTITY/FKs
puede exigirlo; no recomendar su eliminación generalizada.

## 4. Exportación: consultas agrupadas, recorridos repetidos

Evidencia: [ProyeccionDocumentoService:139–195](../../src/main/java/ec/uce/propuestas/documento/exportacion/ProyeccionDocumentoService.java#L139)
y [253–264](../../src/main/java/ec/uce/propuestas/documento/exportacion/ProyeccionDocumentoService.java#L253).

Aunque carga por lotes, filtra todas las secciones por APU y todos los detalles
por sección: O(A·S + S·D). Ya existe `detailsPorSeccion`, no aprovechado para
esas filas. La recursión vuelve a examinar capítulos completos: O(C²).
Propuesta mínima: reutilizar agrupaciones y construir adyacencia de capítulos.

Contraevidencia: [DocumentoSnapshotIT:386–432](../../src/test/java/ec/uce/propuestas/documento/DocumentoSnapshotIT.java#L386)
define comprobaciones de cantidad SQL; no se ejecutaron aquí ni miden CPU.
No confundir estos recorridos con N+1 SQL. Presupuesto/APUs capturan snapshot
en transacción separada de solo lectura y renderizan después; cronograma tiene
su propio contrato de bloqueo del propietario y render transaccional.

## 5. XLSX: materialización y copias

Evidencia: [PresupuestoXlsxWriter:30–48](../../src/main/java/ec/uce/propuestas/documento/PresupuestoXlsxWriter.java#L30)
y [94–95](../../src/main/java/ec/uce/propuestas/documento/PresupuestoXlsxWriter.java#L94).

`XSSFWorkbook`, filas de presentación, `ByteArrayOutputStream` y `toByteArray`
pueden coexistir en memoria. Es riesgo de pico de heap, **no OOM demostrado**.
Propuesta mínima: medir primero retención y copias antes de considerar streaming,
que debe preservar el formato y el contrato de salida.

Contraevidencia: se reutilizan estilos. [ApuXlsxWriter:292](../../src/main/java/ec/uce/propuestas/documento/ApuXlsxWriter.java#L292)
rechaza filas desde 1_048_576; ese límite de formato no prueba suficiencia del
heap ni convierte 300–1000 APUs en un número fijo de filas.

## 6. Catálogos: búsqueda parcial y conteo

Evidencia: [ApuRepository:36–54](../../src/main/java/ec/uce/propuestas/apu/repository/ApuRepository.java#L36),
[InsumoRepository:119–155](../../src/main/java/ec/uce/propuestas/insumo/repository/InsumoRepository.java#L119)
y [201–216](../../src/main/java/ec/uce/propuestas/insumo/repository/InsumoRepository.java#L201).

Usan `lower` con `LIKE '%q%'` y conteo. Se observó tamaño predeterminado 25,
no un máximo efectivo. Propuesta mínima: obtener planes representativos y
confirmar límites antes de proponer cambios de búsqueda o índices.

Contraevidencia: la búsqueda principal de plantillas usa FTS/GIN y tope 200:
[PlantillaApuRepository:88–129](../../src/main/java/ec/uce/propuestas/plantilla/repository/PlantillaApuRepository.java#L88),
[V011:24](../../src/main/resources/db/migration/V011__plantilla_apu_busqueda_fts.sql#L24).
Hay índices de FK en [V001:385–400](../../src/main/resources/db/migration/V001__baseline.sql#L385).
No se observaron planes secuenciales ni una carencia general de índices.

## Siguiente decisión

El informe pendiente `02-prioridad-alta-proyectos-grandes.md` debe separar
mediciones reales, reproducciones aisladas y proyecciones. Solo promoverá
caminos relevantes con crecimiento evitable y magnitud suficiente demostrados.
No se prometen umbrales, latencias ni porcentajes; no se autorizan cambios de
comportamiento ni se incorporan aquí resultados de la investigación posterior.
