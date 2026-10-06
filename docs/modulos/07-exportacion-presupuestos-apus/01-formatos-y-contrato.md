# 01 — Cerrar formatos, contrato común y compatibilidad de cronograma

**Estado: CERRADO en su alcance (EXP-01–05), sin commit.** Contrato, preflight,
captura inmutable consistente y A3 verificados independientemente; EXP-05 es
cierre documental estructural, no suite global verde. Presupuesto plan02 cerrado;
APUs plan03 implementados: APU-01–06 cerrados en alcance verificado.
Frontend04 no autorizado/iniciado. El contrato final observado siguiente reemplaza
la propuesta inicial; las secciones EXP conservan su evidencia histórica.

## Objetivo y entrada

Producir una proyección read-only coherente de la versión seleccionada, preflight
único por solicitud, errores sin fuga y opciones explícitas. Entrada: decisiones
recogidas en [la tarea](../../../odd/tasks/budget-apu-export-plans.md),
[requerimientos v1.3 §2.8](../../../../thesis-docs/DOCUMENTOS/requerimientos/v1.3-functional-requirements.md)
y [diseño de exportación](../../../../thesis-docs/plan/design/04-export-sercop-spec.md).
El diseño es evidencia de campos/layout, no autoridad que reemplace v1.3 o las
decisiones del usuario: no carátula/logo nuevo; presupuesto A4 vertical default;
APU largo puede continuar, no compresión obligatoria a una página.

## Inventario real de referencias

Base de rutas: [thesis-docs](../../../../thesis-docs/).
Todas las rutas de esta tabla son relativas a esa base, preservadas sin cambios.
Inventario recibido del audit read-only: **9 PDF, 5 XLSX, 0 XLS/XLSM, 3 ZIP**.

| Grupo | Archivos exactos | Uso |
|---|---|---|
| `res/Presupuestos IESS/` | `APUS CETRO MEDICO TULCAN.xlsx`; `PRESUPUESTO.pdf`; `APU EJEMPLO.pdf` | referencia principal presupuesto/APU |
| `res/Presupuestos EMELNORTE/` | `EXPANSIÓN DE ALUMBRADO PUBLICO.xlsx`; `PRESUPUESTO 1.pdf` | jerarquía complementaria |
| `res/HIDROELECTRICA MIRA/` | `Analisis de Precios Unitarios incremento a 2MV.xlsx` | variantes históricas HM/transporte |
| `res/ESTANCIA-ACADEMICA/` | `CRONOGRAMA VALORADO-signed.pdf`; `ESPECIFICACIONES TECNICAS TOTALES-signed.pdf`; `APU VAE-signed.pdf` | cronograma/estructura; ET y VAE fuera de esta entrega |
| `res/` | `SALARIOS-MINIMOS-POR-LEY-2025-CAMICON.pdf` | referencia salarial, no cálculo/export adicional |
| `res/datasets_presupuestos/malecon/` | `ANALISIS-DE-PRECIOS-UNITARIOS-1-1000-EXCEL.xlsx`; `ANALISIS-DE-PRECIOS-UNITARIOS-1001-1053-EXCEL.xlsx`; `dataset_daule_2025.zip` | evidencia de APU apilado; dataset derivado |
| `res/datasets_presupuestos/regeneracion/` | `dataset_daule_regeneracion_2025_01.zip` | derivado, original no disponible |
| `res/datasets_presupuestos/consolidado_v1/` | `dataset_maestro_daule_v1.zip` | derivado consolidado |
| `DOCUMENTOS/tesis-documento/` | `main.pdf` | documento académico, no plantilla |
| `DOCUMENTOS/entrevistas/01/` | `appendix-a.pdf` | antecedente, no contrato activo |

ZIPs contienen respectivamente 10/10/15 entradas CSV/JSON/README, no originales
XLSX/PDF. Falta el original de regeneración: registrar la limitación, no bloquear
Tulcán disponible ni fabricar una plantilla desde el ZIP.

### Evidencia de formato, no inspección visual completa

El audit fue XML de workbook/texto PDF; no demuestra colores, logos o todas las
páginas renderizadas. La compatibilidad visual queda como gate futuro.

- Tulcán: 306 hojas = carátula + presupuesto + 304 restantes APU; la hoja se
  llama exactamente `PRESÚESTO ` (**espacio final**). A2:A5 institución/proyecto,
  A7:B7 año, A10:G10 `Ítem · Código · Descripción · Unidad · Cantidad ·
  P.Unitario · P.Total`; G342 total 395115.32; A344 nota sin IVA.
  D360 indirecto 18 % es dato histórico, no regla. H:I y L:P comparan ofertas:
  fuera de salida. PDF presupuesto: seis páginas Letter; A4 decidido por usuario
  prevalece. No exigir igualar el total de muestra cambiando el motor.
- `501BM6`: print area A1:F30, A4 vertical. Equipos A7:F10, MO A11:F15,
  materiales A16:F19, transporte A20:F23; directo F24, indirecto E26/F26,
  total F27/F28. HM 5 % MO es muestra, no constante de exportación.
- PDF APU `501062`: una página A4, rendimiento cinco decimales, costo hora tres,
  costo dos. Entradas ya redondeadas no reproducen todos los costos (pegamento
  .10 × 61.49 frente a 5.84): jamás construir un segundo calculador desde PDF.
- Malecón: una hoja con APUs apilados; segundo workbook, bloques de 85 filas y
  52 saltos. Contiene VAE/CPC y enlaces externos `[1]`: no copiar esos campos,
  enlaces ni fórmulas. Mira incluye distancia/transporte y HM por tarifa/25 %:
  variante histórica no normativa para nuestro modelo.
- Escala representativa del dominio Tulcán: **288 APUs / 298 rubros**. No confundir
  con 304 pestañas fuente; no existe correspondencia 1:1 garantizada.

## Contrato HTTP observado al reconciliar plan 03

Fuentes read-only: [resource real](../../../src/main/java/ec/uce/propuestas/documento/PresupuestoApuDocumentoResource.java),
[OpcionesDocumento.parsear](../../../src/main/java/ec/uce/propuestas/documento/exportacion/OpcionesDocumento.java),
[descarga presupuesto](../../../src/main/java/ec/uce/propuestas/documento/PresupuestoDescargaService.java),
[descarga APUs](../../../src/main/java/ec/uce/propuestas/documento/ApuDescargaService.java),
[HTTP presupuesto](../../../src/test/java/ec/uce/propuestas/documento/PresupuestoExportResourceIT.java)
y [HTTP APUs](../../../src/test/java/ec/uce/propuestas/documento/ApuExportResourceIT.java).
Rutas efectivas con prefijo `/api/v1`; el UUID es de presupuesto/version, no APU.

| GET real (descarga; agregar `/preflight` para JSON previo) | Opciones aceptadas |
|---|---|
| `/api/v1/documentos/presupuesto/{budgetUuid}` | `formato=xlsx`; `formato=pdf` con `orientacion=vertical` por defecto o `horizontal` |
| `/api/v1/documentos/apus/{budgetUuid}` | `formato=xlsx` con `layout=pestanas` por defecto o `apilado`; `formato=pdf` sin opciones, A4 vertical |

`formato` es **obligatorio**, no tiene default. Parser case-sensitive: valores
exactos en minúsculas, sin trim/fallback. Rechaza400 `validacion` ante ausente,
blanco, duplicado (aunque igual), desconocido o combinación incompatible. Solo
conoce `formato`, `orientacion`, `layout`; `papel` no existe para estas rutas.
Orientación solo presupuestoPDF; layout solo APUXLSX. PDF+layout, APU+orientación,
XLSX+orientación y presupuesto+layout son inválidos. MSPDI/ZIP/PDF tabs/PDF
individual por APU no forman este contrato. El enum cronograma no se reutiliza.
Cronograma existente sí admite PDF `papel=a4` default o `a3`, horizontal;
la paginación por slices autorizada de03 no añade parámetros HTTP.

### Ejemplos sanitizados (plantillas, no ejecutar con placeholders)

`<budgetUuid>` representa el UUIDv7 elegido; `<token>` el bearer propio. No se
publican identidades de seed, tokens, responsables ni registros financieros reales.

```bash
curl -H 'Authorization: Bearer <token>' 'https://<host>/api/v1/documentos/apus/<budgetUuid>/preflight?formato=xlsx&layout=apilado'
curl -H 'Authorization: Bearer <token>' 'https://<host>/api/v1/documentos/apus/<budgetUuid>?formato=xlsx'
curl -H 'Authorization: Bearer <token>' 'https://<host>/api/v1/documentos/apus/<budgetUuid>?formato=pdf'
curl -H 'Authorization: Bearer <token>' 'https://<host>/api/v1/documentos/presupuesto/<budgetUuid>?formato=pdf&orientacion=horizontal'
```

200download: XLSX `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
o PDF `application/pdf`; `Content-Disposition: attachment; filename="..."`.
Nombre `apus-<proyectoASCII>-<budgetUuid>-v<version>.xlsx|pdf` o
`presupuesto-<proyectoASCII>-<budgetUuid>-v<version>.xlsx|pdf`: allowlist
`A-Za-z0-9_-`, otros caracteres→`_`, colapso de underscores, máximo48 caracteres
de proyecto. Sin sufijo layout, CRLF, separadores de ruta ni IDs BIGINT.
No se inventa header de warnings: estas descargas solo añaden Content-Disposition;
preflight es el canal HTTP de warnings que debe consultar un futuro consumidor.
Stale no obliga aprobación ni modifica revisión.

### Preflight, seguridad y vacíos

200preflight JSON observado: `presupuestoId`, `version`, `documento`, `formato`,
`opciones`, `exportable`, `bloqueos[]`, `warnings[]`. Cada detalle contiene
`codigo`, `mensaje`, `rubros[]` con referencias públicas; no es un array genérico
inventado. Opciones resueltas: `{layout:pestanas}` para APUXLSX sin layout,
`{orientacion:vertical}` para presupuestoPDF sin orientación; resto `{}`.
Download recaptura y reevalúa, no confía en un preflight previo. Si bloqueado,
**409 application/json con el propio PreflightDocumento** (`exportable=false`,
ambos arrays), no wrapper `export-bloqueado`, ni writer, bytes de archivo o
attachment. Inconsistencia de vínculo/scope produce problema409
`export-inconsistente`, distinto de bloqueo P-32. No stacktrace/BIGINT/SQL/datos
ajenos. 401 sin autenticación;403 ante rol no permitido por `@RolesAllowed`;
400UUID inválido/no-v7 u opciones inválidas;404ajeno/inexistente indistinguibles.
Los tests APU ejercitan401/400/404/409 y SUPER_ADMIN sin bypass; no se afirma un
caso APU dedicado403 que esas pruebas no contienen.

1. Autenticación/roles existentes `USUARIO`/`SUPER_ADMIN`, sin bypass de ownership.
   UUID malformado/no-v7 → 400; UUIDv7 ajeno/inexistente → mismo 404. Resolver
   presupuesto → proyecto → caller antes de leer rubros/APUs/parámetros/firmantes.
2. [ValidacionPresupuestoService](../../../src/main/java/ec/uce/propuestas/presupuesto/service/ValidacionPresupuestoService.java)
   prueba **exactamente cero** por `compareTo`, PU/cantidad no nulos; cobertura
   `findRubrosCubiertosPorCronograma`; listas independientes. Sin cronograma todos
   los rubros quedan sin actividad. Orden actual de esas listas es lexicográfico
   item + UUID; no cambiarlo para conseguir orden natural del documento.
3. Presupuesto/APUs bloquean P-32 (PU=0, cantidad=0, sin actividad). No trasladar
   BORRADOR, desviación, final 100 %, fecha MSPDI ni total cero de cronograma como
   gates genéricos. Nulos/negativos no están cubiertos por ese filtro: verificar
   constraints y proyección, no afirmar que P-32 los detecta. Inconsistencia
   ilegible debe fallar sin bytes, sin corregir dominio durante exportación.
4. P-32 devuelve exportable=true para presupuesto vacío; captura agrega bloqueo
   `presupuesto-vacio` y, para APUs vacíos, `apus-vacios`, sin alterar P-32 ni
   rellenar desde catálogo. Bloqueos P-32 reales: `presupuesto-pu-cero`,
   `presupuesto-cantidad-cero`, `presupuesto-sin-actividad`.
5. Detector stale canónico solo como warning `cronograma-desactualizado` si hay
   cronograma, en `warnings[]`; no nuevo gate ni escritura de revisión.
   `X-Cronograma-Desactualizado` pertenece a cronograma existente, no se atribuye
   a las descargas presupuesto/APU que no lo emiten.

### Snapshot y consistencia

Una lectura atómica debe congelar identidad/version, ownership, proyecto,
responsables, parámetros, display config, árbol, APUs/secciones/detalles e insumos
PROYECTO efectivos y warning/bloqueos. Verificar cada vínculo contra la versión y
scope permitido; nunca buscar APU por código ni recorrer CENTRAL/PERSONAL para
resolver precios. Mantener solo DTOs inmutables y BigDecimal/string decimal;
ninguna entidad lazy fuera de transacción. Todos los formatos comparten ese DTO.

El pipeline histórico de [cronograma](../../../src/main/java/ec/uce/propuestas/cronograma/export/CronogramaDescargaService.java)
no exige retener su frontera hasta bytes en APU/presupuesto. Contrato observado:
`CapturaDocumentoService` abre `REQUIRES_NEW` y fija en conexión Hibernate
`REPEATABLE READ, READ ONLY` antes del primer SELECT/ownership/preflight;
`FlushMode.MANUAL` y sesión read-only, sin cambio global de aislamiento. Devuelve
records inmutables. Descargas `NOT_SUPPORTED` suspenden caller y serializan una
sola captura finalizada; HTTP `@Blocking`. Concurrencia verifica anterior/posterior,
no híbrido; ausencia de lazy y precarga eliminan N+1. No se escriben mutaciones
para facilitar exportación. Duración de locks no medida; tiempos/heap/JMX son
observaciones, no asserts de lock ni allocation.

### Valores y seguridad del renderer

- Importes APU, HM/transporte, subtotales/CD/CI/CT y presupuesto PU/PT/totales
  son originales persistidos del snapshot. Cálculo canónico capturado es diagnóstico
  **separado**, no reemplazo ni normalización de históricos; no copiar tasas,
  descuentos o fórmulas fuente. APU usa M/N/O/P fijo de presentación y orden
  persistido dentro del bloque, incluida HM; no muta `orden` de secciones.
- [DisplayConfig](../../../src/main/java/ec/uce/propuestas/common/config/DisplayConfig.java)
  confirma precisión **global**, no propiedad monetaria por proyecto:
  `app.display.precision` (2) / `precision-porcentaje` (4). Capturar configuración
  efectiva; no inventar parámetro de proyecto para satisfacer el diseño.
- [ParametrosProyecto](../../../src/main/java/ec/uce/propuestas/proyecto/entity/ParametrosProyecto.java)
  confirma `mostrarSeccionesVacias`, `sufijosSeccionActivos`,
  `mostrarSubtotalesSeccion`, `mostrarSubtotalesPie`, `mostrarNombreProyectoHeader`,
  `enumerarApus`, `mensajeFooter`, `moneda`, `porcentajeHerramientaMenor`,
  `porcentajeIndirecto`, `ciIndividualHabilitado`. Verificar soporte/mapping de
  cada uno al ejecutar, incluyendo CI efectivo; no agregar flags inexistentes.
- POI/OpenPDF ya están en [build](../../../build.gradle.kts), sin dependencia nueva.
  Reutilizar [ArchivoGenerado](../../../src/main/java/ec/uce/propuestas/documento/ArchivoGenerado.java):
  XLSX `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, PDF
  `application/pdf`. Filename saneado con documento/version/extensión, sin rutas,
  CRLF, nombres reservados ni IDs internos; Content-Disposition consistente.
- XLSX dinero/cantidades numéricos, no texto formateado; formatos visuales según
  display. Writers nuevos escriben valor BigDecimal exacto en OOXML numérico
  (`STCellType.N`, `toPlainString`), no tolerancia ni promesa por conversión double.
  Auditoría contra persistido en ambos layouts y display PDF descrita en03.
  PDF APU usa HALF_UP/precisión capturada; cronograma mantiene escalas canónicas,
  no un redondeo p2 general.
- Texto no ejecutable; ninguna fórmula externa, macro, hyperlink ejecutable ni
  celda FORMULA proveniente de descripción/código/footer (`= + - @` y controles).
  Sin copia binaria de workbook fuente. Nombres de hoja deterministas: sanear
  `[]:*?/\\`, apóstrofos extremos/vacíos, máximo 31 caracteres; reservar espacio
  para sufijo estable según orden/identidad, resolver colisiones case-insensitive
  después de truncar, códigos iguales y unicode. No confundir nombre con identidad.
- PDF fuente española embebida existente, headers repetidos, descripción larga
  sin recorte, footer/continuación y responsables ya guardados; sin carátula nueva.

## Trabajo acotado, test-first y cronograma A3

- [x] RED observado: ruta preflight 404 frente a 200; captura READ COMMITTED
      mezclaba total anterior/footer posterior. No writer de presupuesto/APUs existe.
- [x] GREEN: recursos/DTO/proyección/preflight; aislamiento real y captura canónica
      sin escrituras de dominio, verificación independiente descrita abajo.
- [x] TRIANGULAR plan01: versión no vigente, owner/scope, vacíos, identidad,
      cambios compensados/stale, captura inmutable sin lazy y concurrencia.
      Strings hostiles en celdas/hojas/PDF: N/A aquí, pendiente writers02/03.
- [x] RED cronograma (EXP-02): 191 tests ejecutados, dos fallos observados:
      A3 devolvía MediaBox de A4 y papel inválido devolvía 200 en vez de 400.
- [x] GREEN cronograma (EXP-02): 191 tests, 0 fallos/errores/skips según XML
      Gradle (17 del resource). PDFBox confirma MediaBox A4 default/ explícito
      842×595 y A3 1191×842, sin rotación. Preflight y descarga rechazan papel
      vacío, desconocido, mayúsculas, espacios y combinaciones XLSX/MSPDI con
      400 `validacion`; A3 conserva UUID-400, owner-404 y bloqueo-409 sin
      attachment ni PDF. Sobrecargas de service/writer conservan A4 por defecto,
      sin modificar gates ni enum global.
      [Writer actual líneas 60–63](../../../src/main/java/ec/uce/propuestas/cronograma/export/CronogramaPdfWriter.java)
      usa rectángulos explícitos A4/A3; comentario identifica problema de rotate
      en OpenPDF 2.0.3. A3 horizontal verificado por descarga HTTP y PDFBox.
      Comando ejecutado: `./gradlew --offline --console=plain test --rerun-tasks
      --tests 'ec.uce.propuestas.cronograma.*' --tests
      'ec.uce.propuestas.documento.CronogramaExportResourceIT'` (GREEN: 1m15s).
      `./gradlew --offline --console=plain spotlessCheck` y `git diff --check`
      pasan. No se afirma inspección visual, rendimiento a escala ni espionaje
      directo de invocaciones del writer; el retorno previo al render sigue intacto.
      Verificación independiente: 191 tests / 20 suites PASS.
- [x] Captura: consultas y latencia medidas, N+1 eliminado en seam canónico;
      16 consultas para 1/32/298 APUs, equivalencia canónica completa verificada.
- [ ] N/A plan01; pendiente writers02/03: heap, bytes, render, distribución de
      latencia, textos largos, presupuesto integral y serialización fuera del event
      loop/streaming. Captura no retiene lock presupuestario durante render.

### Evidencia acotada EXP-03

Implementadas únicamente las dos rutas `/preflight` de presupuesto/APUs bajo
`/api/v1`, sin descarga ficticia ni writers. Opciones cerradas mediante `UriInfo`:
rechazo de ausencias, blancos, duplicados, parámetros desconocidos y combinaciones
incompatibles. Respuesta inmutable con identidad/version seleccionadas, opciones,
bloqueos P-32 con listas canónicas de referencias públicas y warnings separados.
Vacíos agregan `presupuesto-vacio` y, para APUs, `apus-vacios`.

Resolución owner-scoped previa a rubros/APUs; selección por identidad APU distinta,
no por código ni catálogo. Cada vínculo se resuelve en el presupuesto seleccionado;
una inconsistencia devuelve `409 export-inconsistente` sin bytes. Detector stale
canónico extraído con overload read-only, sin gates de distribución ni revisión.
EXP-03 por sí solo no acreditaba captura atómica multiconsulta; la garantía final
la aporta EXP-04 mediante la frontera descrita abajo.

RED HTTP observado: ruta nueva devolvió 404 frente a 200 esperado (199 tests,
1 fallo). GREEN/triangulación final EXP-03: XML fresco, 24 suites, 212 tests, cero fallos,
errores o skips; incluye 3 tests HTTP nuevos, 10 casos de equivalencia stale y
1 de identidad. Los 11 casos complementarios pasaron al primer intento: no hubo
nuevo fallo de comportamiento ni modificación adicional de producción. Casos cubiertos:
versión no vigente, owner/ajeno/inexistente, SUPER_ADMIN sin bypass, UUID inválido/
no-v7, matriz de opciones, vacíos, P-32 cero PU/cantidad y sin actividad, borrador
no bloqueante, APU no vinculado y stale sin alterar total revisado. Equivalencia
de overloads verificada para marcadores nulos, total distinto, fingerprint distinto,
fingerprint con espacios, total presupuestario nulo y filas con cambios compensados
(11 + 19 conservan total 30). Fecha de revisión nula o distinta no interviene en
el detector canónico; ambos overloads conservan esa conducta. Las pruebas releen
los tres marcadores persistidos y demuestran que el detector no escribe revisión.

Prueba HTTP dedicada: SQL de fixture aislado vincula un rubro seleccionado a un
APU no vinculado de otro presupuesto/proyecto/owner sin desactivar constraints.
La FK existente acepta la referencia porque comprueba existencia, no scope;
ambos preflights rechazan 409 con exactamente `codigo` y mensaje estable, sin
referencias/identidad/código del APU ajeno ni Content-Disposition. EXP-03 cerrado;
la garantía adicional de captura se verifica por separado en EXP-04.

Comando ejecutado: `./gradlew --offline --console=plain test --rerun-tasks
--tests 'ec.uce.propuestas.documento.*' --tests 'ec.uce.propuestas.cronograma.*'`.
`./gradlew --offline --console=plain spotlessCheck` y `git diff --check`: PASS.
Unidad mayor al presupuesto orientativo de 400 líneas: 788 líneas nuevas Java
(incluidos tests) más 18 líneas añadidas/eliminadas de extracción de helper y esta
evidencia; no se minificaron tests.
Rollback acotado: retirar recurso y subpaquete `documento/exportacion`, sus tests
y extracción stale, junto con su fachada dependiente; preservar DOCX y EXP-02/A3.

### Evidencia acotada EXP-04 — captura, no writers

`CapturaDocumentoService` es una frontera CDI `REQUIRES_NEW` independiente de
la fachada HTTP. La Session se obtiene vía repository, sin inyección eager de
EntityManager incompatible con perfiles ORM-off. Antes de ownership o validación, `Session.doWork`
ejecuta en la conexión de Hibernate `SET TRANSACTION ISOLATION LEVEL REPEATABLE
READ, READ ONLY`. `FlushMode.MANUAL` y `setDefaultReadOnly(true)` evitan autoflush
y dirty checking de la captura; no se cambia aislamiento global, datasource,
locks ni escrituras de dominio. El contexto suspendido no se limpia ni modifica.
Ambos preflights HTTP ahora devuelven únicamente el preflight de esta captura;
no se publica el snapshot interno, ni se agregan rutas de descarga.

Fuentes primarias aportadas por el coordinador tras comprobar que Context7 no
estaba expuesto; no se instalaron herramientas:

- [Quarkus — transacciones](https://quarkus.io/guides/transaction):
  `REQUIRES_NEW` suspende la transacción existente e inicia otra.
- [Hibernate — SharedSessionContract.doWork](https://docs.jboss.org/hibernate/orm/current/javadocs/org/hibernate/SharedSessionContract.html):
  usa la conexión subyacente de la sesión; `Session` hereda esta API.
- [PostgreSQL — SET TRANSACTION](https://www.postgresql.org/docs/current/sql-set-transaction.html):
  aislamiento antes del primer SELECT; snapshot multiconsulta en Repeatable Read;
  READ ONLY rechaza escrituras no temporales y SET TRANSACTION no cambia el default.

El catálogo real fija Quarkus **3.37.4**; el runtime de las pruebas informa
Hibernate **7.4.5.Final**. Las páginas actuales no se presentan como documentación
versionada exacta: compatibilidad de API corroborada por compilación offline y
comportamiento probado en PostgreSQL aislado. `SHOW transaction_isolation` y
`SHOW transaction_read_only` dentro de la captura confirman `repeatable read` y
`on`; un UPDATE de fixture intentado dentro de esa misma frontera se rechaza con
SQLSTATE **25006**, y la siguiente captura conserva el total. Otra IT mantiene
entidades sucias/stale en un caller, confirma distinto backend PID y distinta
entidad gestionada durante la captura, recibe los cambios ya comprometidos del
writer concurrente y recupera el contexto original sin flush al reanudarlo.

Snapshot de records y copias recursivas: UUID/version/vigencia seleccionados,
metadatos de proyecto owner-validado, todos los parámetros existentes y defaults
sin materializarlos, display global, firmantes, árbol natural de capítulos y
rubros, totales BigDecimal persistidos, APUs por identidad, secciones/detalles e
insumos PROYECTO efectivos. Los vínculos APU se validan por presupuesto antes de
leer sus secciones; insumos se resuelven exclusivamente desde la base PROYECTO.
Vínculos ilegibles producen 409 estable sin referencias ajenas. No hay entidades,
lazy, arrays mutables ni FKs BIGINT en el DTO; una prueba recorre todos los records
tras terminar la transacción y verifica estos límites y listas/maps inmutables.

Se conservan `costo`/`costoHora` persistidos de detalle y subtotales/totales del
dominio. Además se congela el resultado puro de `ApuCalculoService.calcular` mediante
sobrecarga canónica con lecturas precargadas y scope validado;
no se invoca `proyectar`, `recalcular`, `obtenerOCrear`, ni un segundo calculador.
Los writers futuros deben distinguir valores persistidos del resultado on-demand
capturado: no corregir ni persistir diferencias durante exportación. Una IT con
parámetros ausentes y APU no vacío prueba cálculo exacto y ausencia de fila creada.
CI, HM y transporte siguen exclusivamente las reglas canónicas actuales.

RED real: **214 tests / 26 suites, 1 fallo, 0 errores/skips**. Tras ownership, un
latch detiene la captura READ COMMITTED; otro hilo compromete juntos cambios de
parámetros, firmante, precio/metadatos de insumo, detalle, APU, rubro y totales.
La captura retenía total anterior pero footer posterior. No fue un fallo de
compilación ni una transacción de juguete. GREEN: sustituyendo únicamente el SET
por Repeatable Read, la misma captura produce todos los valores anteriores y la
próxima produce todos los posteriores. Triangulación final: **220 tests / 27
suites, 0 fallos/errores/skips**, incluyendo el baseline A3/HTTP/P-32/stale, versión
no vigente, vacíos, links ajenos y ausencia de materialización de parámetros.
Se corrigieron dos problemas del harness sin ocultarlos: el seed había sido
truncado por suites legacy (perfil aislado nuevo), y un rollback intencional del
caller era reportado por el runner como error (rollback explícito de fixture).

Corrección N+1: RED observado **1 APU/19 consultas; 32 APUs/205**. Captura fría
previa de 298 APUs: 1804 consultas/2705 ms (otra ejecución: 2761.582 ms).
Sobrecarga canónica precargada, sin cambio matemático en Motor: **16 consultas
para 1/32/298 APUs**. Independiente: **298 identidades APU/298 rubros, 16 consultas,
566.908 ms**, los 298 resultados iguales al cálculo canónico; CI/HM/insumos y
overrides verificados. No confundir con 288 identidades del inventario histórico.
No acredita heap, bytes, render, OOXML, visual ni distribución de latencia de
writers futuros: N/A al gate01, pendiente02/03.

Comandos foreground ejecutados desde backend:

```bash
./gradlew --offline --console=plain test --rerun-tasks --tests 'ec.uce.propuestas.documento.*' --tests 'ec.uce.propuestas.cronograma.*'
./gradlew --offline --console=plain spotlessCheck
git diff --check
```

Ejecución inicial focal: PASS, 4m14s; XML fresco sin skips. Spotless/diff: PASS.
La verificación posterior corregida se detalla abajo; no equivale a suite global verde.
`graphify update .`: PASS; persisten warnings de parser SQL ausente, archivos sin
nodos y nombres de comunidades pendientes de relabel; no se instalaron paquetes.
Warnings preexistentes de compilación/configuración y Testcontainers sin reuse
persisten; SQLSTATE 25006 es el rechazo esperado por la IT. La unidad agrega
**1090 líneas Java nuevas**, más la sustitución de la fachada preflight y esta
evidencia, sobre las 806 anteriores: captura y prueba de su frontera forman una
unidad coherente mayor al presupuesto orientativo, sin minificar ni omitir checks.
Rollback coherente: retirar captura/proyección/snapshot y tests con el seam
precargado, restaurando fachada preflight previa. Retirar preflight/contrato solo
con sus consumidores y extracción stale; A3 es frontera independiente que vuelve
a A4 por defecto. Preservar DOCX, seeds previos, muestras y eliminaciones Compunex;
ninguna reversión requiere reset de DB. Ejecución sin commit/push, despliegue,
migraciones, frontend ni DB de usuario.

### Gate independiente y EXP-05

**Plan01 completo dentro de su alcance; ningún gap propio pendiente.** Captura,
SQLSTATE25006, contexto caller suspendido y concurrencia PASS. Último agregado
independiente: **255 tests / 3 fallos / 1 skip**: documento48 PASS, cronograma174
PASS; schema29/1 fallo, motor4/2 fallos/1 skip. Cuatro clases ORM-off ejecutan tras
la corrección: tres PASS. ScheduleIT líneas31–42 espera 1.0000, pero rebuild
líneas100–114 aplica todas las migraciones, incluida reparación ponderada V019
líneas28–43: expectativa obsoleta, no regresión de exportación, sin fix autorizado.

Motor confirmado preexistente mediante HEAD aislado
`d73bfa5041af20ffe9ec7c4a3695a33ee3407811`: 4 tests/2 fallos/1 skip idénticos al
candidato: GM19 esperado395115.32/actual395108.37; GM20 capítulo1 esperado158908.05/
actual158907.21. GM21 PASS, GM24 SKIP por datos ausentes. **Motor NO corregido.**
Suite completa antes de corrección: 820 tests/2 fallos/1 skip más cuatro fallos
bootstrap; no reejecutada completa después, no se afirma verde global.

Evidencia preservada: [corrección](../../../build/exp04-correction.log),
[independiente](../../../build/exp04-independent-correction.log),
[focal inicial](../../../build/exp04-independent-focused.log),
[baseline HEAD](../../../../motor-head-baseline-vstgqI/build/motor-head-baseline.log).
RDD nativo desactivado/no evaluable; revisión independiente hecha, sin claim de
aprobación nativa. EXP-05 solo verifica estructura/enlaces/checklists y diff:
RED documental no significativo, sin reejecutar tests. OOXML/PDF visual, heap,
bytes, distribución de latencia y rutas de descarga esperan writers02/03;
frontend04 no habilitado, sin claim UI/browser. Cambios sin commit.

## Reconciliación APU-06 y lectura histórica

[Plan03](03-backend-apus.md) documenta resultado observado y límites:298APUs,
318drifts V014 no corregidos, nuevo fixture calculado/recargado1/32 y consultas17,
lectores acotados, corrección crono52períodos y baseline. Focal final461/53 sin
fallos/errores/skips; calidad/build sin tests PASS; inventario112/893 con3fallos
baseline y1skip, **no suite global única verde**. APU-06 cerrado tras verificación
independiente: 87 enlaces locales/4 anchors y26/26 claims PASS; 3 enlaces externos
no comprobados. Diffcheck PASS; checks Markdown incluyen untracked, que
`git diff --check` solo no inspecciona. Frontend04 no autorizado/iniciado.

Las secciones EXP precedentes registran lo ejecutado **en plan01**, no el estado
actual de writers. Sus afirmaciones «futuro», «sin descarga» y conteos históricos
no se reinterpretan como ausencia actual ni se sustituyen por conteos posteriores.
Se preservan EXP-01–05, evidencias/correcciones y plan02 sin reabrirlos. No commits
por prohibición explícita del usuario; RDD off/ASSESS no evaluable, sin approval.
APU-06 pasivo: checks estructurales/enlaces/diff, sin RED conductual significativo,
sin Gradle pesado ni actualización de grafo de código por cambiar solo docs.

## Superficies y verificación históricas de plan01

Durante ejecución autorizada: `src/main/java/ec/uce/propuestas/documento/` para
infraestructura nueva; métodos read-only en repositories `presupuesto/`, `apu/`,
`proyecto/` cuando indispensables; DTOs nuevos de export sin cambiar HTTP existente;
cronograma solo `CronogramaDocumentoResource.java`, `CronogramaDescargaService.java`,
`CronogramaPdfWriter.java` y pruebas en `src/test/java/ec/uce/propuestas/documento/`
y `cronograma/`. No migraciones, motor, recalculo, muestras o dependencias.

Comandos **futuros**, desde backend; ejecutar focales primero y registrar conteos:

```bash
./gradlew test --tests 'ec.uce.propuestas.documento.*'
./gradlew test --tests 'ec.uce.propuestas.cronograma.*'
git diff --check
```

Gate: contrato cerrado, snapshots concurrentes coherentes, bloqueos sin bytes,
A3/A4 comprobados con PDFBox, cronograma XLSX/PDF/MSPDI sin regresión de preflight.
Rollback: deshabilitar nuevas rutas/opción A3 mediante reversión revisada de esta
unidad, manteniendo comportamiento A4 y recursos existentes. No autoriza 04 todavía.
