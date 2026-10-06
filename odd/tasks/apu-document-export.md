# Exportación APU — recuperación y cierre documental del plan03

**APU-01–06 cerrados dentro del alcance verificado; plan03 CERRADO.**
Descargas APU implementadas; contrato/evidencia reconciliados y cierre autorizado
tras verificación documental independiente. [Plan03](../../docs/modulos/07-exportacion-presupuestos-apus/03-backend-apus.md)
cerrado en ese alcance, no suite global única verde. Frontend04 **no autorizado ni iniciado**.

## Alcance y estado recuperable

Rama observada `feat/document-export-contract`. Entrega: APUs únicos referenciados
por la versión seleccionada, XLSX pestañas/apilado y PDF A4 vertical. Plan01/02
cerrados en sus alcances históricos. El usuario aprobó además corrección acotada
de paginación horizontal del PDF cronograma y sus tests (`approve_schedule_pdf_pagination`).
Sin cambios al frontend, motor/recalculo, entidades/schema, dependencias ni DB del
usuario. Fixtures nuevos aislados y outputs build permitidos. Preservar muestras,
eliminaciones Compunex y todos los cambios ajenos.

**Sin staging/commits/push/publicación por prohibición explícita del usuario.**
No hay identidades de commits ni receipts inventados. RDD apagado; ASSESS nativo
no evaluable por repositorio externo anidado `ingepresupuestos`; revisión
independiente acotada no equivale a approval nativo. No alterar ignores para evadirlo.

## Seis tareas

| Tarea | Estado | Aceptación y evidencia |
|---|---|---|
| APU-01 — Proyección pura | [x] verificada | Identidad/orden natural, bloques M/N/O/P y persistido separado de diagnóstico; independiente 7/7 |
| APU-02 — XLSX ambos layouts | [x] verificada tras dos correcciones | Nombres/OOXML exacto/texto completo, valores una vez, identidad y esquema en continuación; Calc acotado PASS |
| APU-03 — PDF | [x] verificada | A4 vertical, nueva página por APU, UUID/esquemas repetidos, LiberationSans embebida; aviso página13 retractado sin fix |
| APU-04 — Descarga | [x] verificada | Captura única, owner/versión, opciones estrictas, bloqueo sin bytes, `NOT_SUPPORTED` + HTTP `@Blocking`, concurrencia; independiente 455/51 PASS |
| APU-05 — Gate backend | [x] verificado en alcance | Financiero persistido y fixture nuevo, lectores, consultas, cronograma corregido; final focal 461/53 PASS, baseline separado |
| APU-06 — Reconciliación | [x] cerrada en alcance verificado | Contrato/plan03/índice/tarea reconciliados; independiente87 enlaces locales/4 anchors,26/26 claims ydiffcheck PASS; cierre autorizado |

## Contrato que no debe perderse

[Contrato HTTP observado y ejemplos sanitizados](../../docs/modulos/07-exportacion-presupuestos-apus/01-formatos-y-contrato.md#contrato-http-observado-al-reconciliar-plan-03).
UUIDv7 de presupuesto/version owner-selected, no vigente silenciosa; rubros en
orden natural, APUs únicos por identidad y no código/catálogo. Captura inmutable
`REQUIRES_NEW`, `REPEATABLE READ, READ ONLY` antes de ownership/preflight. Una
captura antes de writers puros, sin lazy/N+1/escrituras de dominio.

Bloques fijos M/N/O/P; orden/posición HM dentro de detalle persistidos. Precio
efectivo PROYECTO/override; cantidades, costo hora, rendimiento, subtotales y
CD/CI/CT originales. Cálculo capturado es **diagnóstico separado**, no oráculo
financiero del export. CI individual solo habilitado; toggles existentes, sin
nuevos campos históricos. HM nulo no se rellena con valores calculados ficticios.
BigDecimal numérico OOXML exacto; PDF APU display capturado HALF_UP, dinero p0/p2
sin imponer p2 a cronograma. Sin fórmulas/macros/links/relaciones externas.

## Cronología mínima y decisiones verificadas

| Hito | Evidencia / decisión |
|---|---|
| Proyección | RED 5 contra stub → GREEN 7; `build/apu01-{red,green,independent}.log`. Fixture inconsistente prueba separación persistido/calculado, no paridad motor |
| XLSX inicial | RED stub → GREEN4; nombre whitespace/apóstrofos RED22/1 → GREEN24; independiente24. `build/apu02-coverage.log`, `build/apu02-independent.log` |
| PDF | RED8 → GREEN9; independiente39/39. `build/apu03-{regression,independent}.log`; parse-back no equivalía todavía a imágenes |
| Descarga | Usuario autorizó expectativa legada APU poblada 404→200/PDF y vacía→409. RED: 3 estados HTTP +1 captura/render/concurrencia; URI mal formado no es RED. GREEN9 e independiente455/51; `build/apu04-independent.log` |
| Oráculo histórico | Harness falló al comparar contra recálculo. Se corrigió **oráculo**, no producto/datos: export→persistido exacto; drift canónico conservado. `build/apu05-evidence-correction.log` / `apu05-evidence-regression.log` |
| Full suite | OOM JVM dejó653 casos/74XML incompletos; retry aislado2g/per-clase terminó sin resultado tras30min, XML heredados no se contaron como frescos. `build/apu05-full-suite.log`, `build/apu05-verification/apu05-full-suite-isolated.log` |
| Recuperación | Slices A/B/C sin excluir aserciones; inventario fuente-backed, no conteo doble. DocumentoSnapshot* vive en carpeta documento y declara package exportacion: no son orphans |
| XLSX reader | Calc detectó footer/firmantes sin identidad. RED12/1→GREEN12; luego sección huérfana, RED13/1→GREEN13; identidad3filas/página, altura650pt, keep-together y esquema activo. `build/apu02-continuation-*`, `build/apu02-section-pagination-*` |
| PDF falso STOP | Página13 supuestamente solapada; crops y full220dpi confirmaron UUID/código separados. Aviso **retractado**, sin sourcefix/reabrirAPU03. SHA preservados y error de coordenada header confundida con body |
| Cronograma | Reader52semanas confirmó tabla estrecha/header ausente. Ampliación explícita writer+test; RED5/1 por rango repetido ausente→GREEN5. Error de compilación intermedio no es RED. `build/apu05-cronograma-correction/pagination-20261006/` |
| Final APU-05 | Todos writers quietos; independiente final focal461/53, calidad/build y23páginas crono PASS; procedencia abajo. APU-06 última unidad documental |

## Financiero y consultas: distinguir fixtures

| Fixture | Evidencia exacta |
|---|---|
| Seed V014 | 298 APUs/298rubros, 2 583detalles, 12 407checks OOXML por layout (24 814total), cero diferencias contra persistido capturado |
| PDF seed | 306páginas: 290APUs una página y8 dos; 1 490checks footer persistido/display sin diferencias |
| Drift histórico | 318:38CD/140CI/140CT;238 coinciden escala6 HALF_UP y80no;23 diferencias displayp2 en8APUs. Máximo CT almacenado735.615304 frente a721.846120 calculado, código500BP2. No paridad canónica ni normalización autorizada |
| Nuevo calculado/persistido | `ApuCalculoPersistidoEvidenceIT.java`446líneas: owner/proyecto/v1 nuevos, CRUD+CI+recalcular, commit y otra Tx clear/reload de cuatro secciones/detalles/HM/identidades; rubros/consolidación+POSTMES1 genera actividades/pesos normales, P32eligibleBORRADOR |
| Nuevo 1/32 | 2casos PASS; oráculo/recarga/captura49/1568 y XML19/608 por layout →87/2784checks totales exactos; sintético CD.465000 CI.046500 CT.511500 |
| Histórico consultas | Capture298=16, descarga por formato16, writer0; EXP04 capture1/32/298=16 no era medición de servicio APU |
| Nuevo consultas | Descarga17/17 por formato1/32, freshness crono presente; orden independiente. No sobrescribir16histórico |

Tiempos/heapJMX/bytes de manifests son observacionales, no allocations, benchmark
ni duración de locks. Locks no medidos. V014 conserva datos fuente explícitamente;
no tocar motor, golden masters, seeds ni precios para que coincidan.

## Lectores y límites

Calc **26.2.6.3**, Poppler **110dpi**, selecciones **220dpi**. XLSX final:
12imágenes representativas (6páginas/layout) y45seleccionadas de extremos p0/p2
pestañas/apilado (4PDF×177páginas); 708/708identidades textuales y27páginas de
cuerpo/esquema por PDF, **no708imágenes leídas**. Evidencia:
[reader XLSX](../../build/apu02-section-pagination-verification/reader-render/).

PDF extremos p0/p2 (51páginas cada uno):
- 24full110dpi: 1/12/13/17/18/29/30/34/35/46/47/51 en ambas precisiones.
- 6crops220dpi: p0 12/13/17, p2 12/13/51; dos full reevaluación13 a220dpi.
- 30full110dpi adicionales: 2/6/9/14/15/19/23/26/31/32/36/40/43/48/49 en ambas.
- Seed: 1/35/55/56/94/96/243/306 y zoom220dpi56; lotes anteriores9/69/70 y
  representativo1–3 registrados en handoffs, no sumar como inspección íntegra.

[Auditoría](../../build/apu05-integral-verification/audit-handoff.md) y
[lector](../../build/apu05-integral-verification/reader-handoff.md) conservan
índices/logs/hash y retractación13. Geometría de102páginas extremas:
24 048wordboxes por PDF inbounds; body69.176…796.68, header18.176/32.176 separado.
Transporte seed288secciones vacías/10no vacías. Página56 dispersa de firmante
mantiene identidad: observación, no blocker. No fix de header PDF ocurrió.

Plan02 conserva84imágenes reales; muestras/SHAs históricos intactos. No nuevo
claim48imágenes ni certificación integral de EMELNORTE. HeadlessCalc no aprobación
Excel nativa/universal de glifos. Preview acotado no implica encabezado ilimitado;
código PDF gigante completo sigue en cuerpo.

## Cronograma autorizado: resultado final

23imágenes leídas: A4horizontal842×595,18páginas/9slices W1–6…49–52 (dos
verticales cada slice); A3horizontal1191×842,5páginas/5slices W1–12…49–52.
Proyecto preview80, rango/esquema/footer por página, fuentes/escalas preservadas.
Base fija484pt, mínimo período52pt ajustado a strings, ancho papel menos36pt.
Cuatro series canónicas completas cortadas, acumulado no reinicia. Decimal
independiente2600actividad+208resumen exactos; W52.6668 y final100.0000/1000.000000.
Más páginas tradeoff aprobado. `Unidad`32pt «Unida/d» es nit visible no bloqueante,
no promesa de estética adicional. Máximo NUMERIC14,6 cabe;180dígitos sintéticos
fuera de dominio no prueban compatibilidad/performance real (625A4/417A3).
Stress específico proyecto4000caracteres no reclamado por reporte final.

## Procedencia final y baselines preservados

[FINAL-PROVENANCE](../../build/apu05-final-verification/FINAL-PROVENANCE.txt),
[manifest227](../../build/apu05-final-verification/final-artifacts.sha256),
[checksums](../../build/apu05-final-verification/final-checksum-verification.log),
[inventario](../../build/apu05-final-verification/inventory/20261006T020712Z/provenance.txt).
38inputs/66archivos de archivo de regresión/227finales checksum-verificados;
fuente/status preservados por verifier. XML frescos53clases/461casos prevalecen;
59clases/432casos reutilizados confiables. Total112clases/893identidades únicas:
889PASS, **3baselineFAIL,1SKIP,0ERROR**. No corrida única full suite verde.

| Baseline fuera de alcance | Resultado preservado |
|---|---|
| GM19 | Esperado395115.32 / actual395108.37, reproducido en archiveHEAD |
| GM20 capítulo1 | Esperado158908.05 / actual158907.21, reproducido en archiveHEAD |
| ScheduleIT | Expectativa anterior V019:0 frente a298 actividades; sin ajuste autorizado |
| GM24 | SKIP por datos ausentes |

Comandos finales ya ejecutados por verifier desde backend (no reejecutados por
APU-06 docs-only):

```bash
./gradlew --offline --console=plain test --rerun-tasks --tests 'ec.uce.propuestas.documento.*' --tests 'ec.uce.propuestas.presupuesto.*' --tests 'ec.uce.propuestas.apu.*' --tests 'ec.uce.propuestas.cronograma.*'
./gradlew --offline --console=plain spotlessCheck
./gradlew --offline --console=plain build -x test
git diff --check
graphify update .
```

Focal461/53 cero fallos/errores/skips; calidad/build PASS (build excluye tests).
SQL25006 es rechazo esperado en SnapshotIT PASS. Warning ignorado
`quarkus.health.extensions.enabled`; parserSQL ausente/64JSON sin nodos son
advertencias AST, no fallos funcionales ni autorización de dependencias/config.

## Próximo paso y reversión

APU-06 cerrado: checker e independiente PASS, 87 enlaces locales/4 anchors,
26/26 claims fuente/docs y `git diff --check`; 3 enlaces externos no comprobados.
Checks Markdown independientes incluyen untracked: `git diff --check` solo no los
inspecciona. Documentación pasiva sin RED conductual significativo. Próximo paso
requiere nueva autorización explícita de frontend; no iniciado. Sin Gradle pesado,
source graph update docs-only, aprobación nativa ni commits.

Fronteras de rollback revisado: proyección/tests; writers/tests; descarga con
consumidores; corrección crono PDF separada conservando presupuesto/A4A3previos.
Esta unidad solo cinco docs autorizados. No comandos destructivos, restauración
Compunex ni reset de DB. Conservar evidencia original build sin sobrescribir.

## Aprendizajes clave

1. Persistido histórico y cálculo diagnóstico requieren oráculos separados.
2. Identidad de continuación protege también páginas solo de pie/firmantes.
3. Encabezado de sección y primera fila juntos no bastan: esquema debe repetirse.
4. Consultas17 de fixture nuevo no invalidan16 históricas; son escenarios distintos.
5. Inventario reconciliado y lectores acotados no son suite global ni approval nativo.
