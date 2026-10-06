# 02 — Exportar la versión seleccionada del presupuesto en XLSX y PDF

**Estado: CERRADO en el alcance verificado.** Writers y descarga de presupuesto
implementados; paridad, seguridad, captura consistente y revisión visual de los
fixtures comprobadas independientemente. No habilita APUs ni frontend (03/04),
ni certifica suite global, Excel interactivo o conformidad SERCOP universal.

## Contrato entregado

Se conserva el contrato de [01](01-formatos-y-contrato.md),
[diseño §3](../../../../thesis-docs/plan/design/04-export-sercop-spec.md) y
[v1.3 §2.6/2.8](../../../../thesis-docs/DOCUMENTOS/requerimientos/v1.3-functional-requirements.md),
con las decisiones del usuario: A4 vertical por defecto, horizontal explícito,
sin CPC, carátula, logo nuevo ni firma digital.

| Bloque | Fuente y comportamiento |
|---|---|
| Identificación | Institución/subdirección existentes, proyecto, año y versión seleccionada del snapshot |
| Tabla | Ítem, Código, Descripción, Unidad, Cantidad, P.Unitario, P.Total; árbol completo, orden natural y desempate UUID |
| Capítulo/cierre | Totales persistidos, nota sin IVA y responsables existentes; sin sumar ni recalcular |
| Rubro | Cantidad, PU y PT persistidos; PU canónica DOWN, no CT recalculado del APU |
| XLSX | Una hoja, números con BigDecimal exacto en CTCell/OOXML, texto hostil inerte, sin fórmulas; impresión dinámica y encabezado repetible |
| PDF | A4 vertical/horizontal, fuente embebida, siete columnas, encabezados repetidos y Página n de m |
| Continuación | Chunks Unicode acotados compartidos; identidad/importes solo en la primera fila física, sin cambiar conteos financieros del dominio |

Display HALF_UP separado del valor persistido. OOXML exacto no elimina el límite
nativo de 15 dígitos de Excel. La prueba helper de texto >32767 caracteres no
constituye lectura real de un artefacto de ese tamaño.

La descarga GET de presupuesto XLSX/PDF usa `@Blocking`, una sola captura de
preflight/snapshot, gates antes del writer y render `NOT_SUPPORTED`; nombre
seguro basado en UUID/versión. Preflight se conserva; descarga APU sigue 404.
El usuario aprobó cambiar únicamente el legado de presupuesto vacío 404 → 409.

## Aceptación observada

- [x] Writers puros: versión seleccionada, jerarquía, cantidades fraccionarias y valores persistidos distintos del CT APU; parse-back XLSX/PDF.
- [x] Descarga real: 200/400/401/404/409, MIME, opciones, ownership, vacío y gates de preflight; sin bytes generados cuando bloquea.
- [x] XLSX: tipos numéricos/XML exacto, precisiones, texto hostil inerte y ausencia de fórmulas; PDF: orientaciones, fuente, contenido y paginación.
- [x] Captura coherente con commits de cantidad, PU, proyecto y firmante: primera salida antigua, siguiente nueva. Prueba del servicio con alternativa CDI equivalente REQUIRES_NEW y latches; **no** concurrencia HTTP productiva.
- [x] Revisión visual real de fixtures: 84 imágenes frescas de Calc/PDF, ambas orientaciones, continuaciones y texto hostil; comparación estructural acotada con Tulcán y EMELNORTE.
- [x] Medición de 298 rubros: consultas, bytes, tiempos y heap observados sin umbral inventado ni cambios financieros.
- [x] Regresión focal independiente y verificaciones Spotless/diff aprobadas; recursos ajenos y muestras preservados.

### Evidencia test-first y regresión

| Etapa | Evidencia observada |
|---|---|
| BUD-01 | RED: 6 fallos por salida vacía; GREEN independiente: 8 tests/2 suites |
| BUD-02 | RED: 354 tests, 1 fallo (404 esperado 200); GREEN independiente: 359 tests/39 suites, 6m32, cero fallos/errores/omitidos |
| BUD-03 | Inicial: 363 tests verdes, pero Calc recortaba descripción gigante y PDF vertical perdía headers; no se aceptó ese resultado |
| Corrección layout | RED: 12 tests, 1 fallo de altura; GREEN: 13 tests; continuación de presentación sin duplicar importes |
| Final independiente | 364 tests/40 suites, cero fallos/errores/omitidos, BUILD SUCCESSFUL, 7m11; `build/bud03-layout-independent.log` |
| BUD-04 | Documentación pasiva; sin RED conductual significativo. Verificación estructural, no reejecución de builds |

Comando final ejecutado desde backend:

```bash
./gradlew --offline --console=plain test --rerun-tasks --tests 'ec.uce.propuestas.documento.*' --tests 'ec.uce.propuestas.presupuesto.*' --tests 'ec.uce.propuestas.cronograma.*'
```

### Lectores y comparación acotada

LibreOffice 26.2.6.3 headless con perfil aislado y Poppler a 110 dpi;
imágenes en `build/bud03-evidence/layout-render`. Inspección: seed vertical 1–9,
horizontal 01–12 y Calc 01–10; Calc extremo p0/p2 1–9 cada uno; extremo vertical
1–6, horizontal/p0 1–7 cada uno; hostil 01–15 (84 páginas). FINGIGANTE visible
en últimas páginas Calc y siete headers en todas las continuaciones verticales;
sin nuevos recortes, solapamientos, ##### ni importes duplicados observados.

Tulcán: primera página `build/bud03-evidence/render/reference-tulcan-1.png`,
siete columnas y jerarquía. EMELNORTE: original
`../thesis-docs/res/Presupuestos EMELNORTE/EXPANSIÓN DE ALUMBRADO PUBLICO.xlsx`,
11 hojas incluida PRESUP.1P.Chico; conversión headless de 462 páginas Letter,
primera de presupuesto 365 (`reference-emelnorte/budgetpage-365.png`). Se comparó
con primera/última página del seed vertical y Calc: lista de materiales,
código/descripción/CPC/unidad/cantidad, líneas y banda gris; sin jerarquía de
ítems ni columnas de precios en esa página. Es complemento estructural, no copia
normativa ni adopción de CPC/tasas. No se inspeccionaron otros presupuestos,
totales o aprobaciones del original: **no** se declara equivalencia integral.
SHA original intacto: `99a3edb83931436a0b7191d8b8f6a61897ad5c09e33b01970468dcc5c148114f`.

### Medición y límites

298 rubros persistidos/capturados; captura 16 SQL/1473 ms, render puro 0 SQL,
servicio 16 SQL por salida. Bytes XLSX/vertical/horizontal: 25748/74793/77188.
Tiempos puros: 1188/452/109 ms; servicio: 663/519/478 ms, mezcla frío/caliente,
sin presupuesto de latencia. Heap 469900616 → 514257432; picos JMX de vida del
proceso Old 536453056, Eden 227540984, Metaspace 799259856: no son asignaciones,
duración de locks ni presupuesto específico de tarea.

## Gate, límites y rollback

Plan 02 cerrado únicamente para fixtures y lectores descritos. GM19/GM20 son
fallos previos confirmados en HEAD; GM24 omitido y ScheduleIT conserva expectativa
V019 obsoleta: no se modificaron para obtener verde. No hubo suite global,
deployment, UI/frontend ni validación Excel interactiva. RDD nativo apagado/no
evaluable; verificación independiente sí realizada.

Sin cambios a motor, migraciones, BD del usuario o muestras; sin commits/push.
Graphify externo untracked y salida backend ignorada se preservan sin limpieza,
staging ni cambio de .gitignore; las eliminaciones Compunex previas permanecen.

Rollback revisado por el controlador: retirar writers y sus pruebas de forma
independiente; retirar conexión de descarga junto con sus consumidores nuevos,
preservando contrato/captura/preflight 01 y cronograma existente. El seam Runnable
package-private de captura pertenece a 01; servicio/resource BUD-02 no agregan
hooks. No revertir infraestructura 01 por cerrar 02. Planes 03/04 siguen pendientes.
