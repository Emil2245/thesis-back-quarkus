# 03 — APUs referenciados: backend verificado y contrato reconciliado

**Estado: APU-01–06 cerrados en alcance verificado; plan03 CERRADO.** Las tres
salidas APU están implementadas. Contrato y evidencia reconciliados y cierre
autorizado tras verificación documental independiente.
[Frontend 04](04-frontend.md) **no autorizado ni iniciado**. No hay suite global
única verde, aprobación RDD nativa, publicación ni commits.

## Ruta de revisión

1. Consultar el [contrato HTTP observado](01-formatos-y-contrato.md#contrato-http-observado-al-reconciliar-plan-03).
2. Revisar selección, importes y paginación descritos aquí.
3. Seguir la [tarea consolidada](../../../odd/tasks/apu-document-export.md) para
   pruebas, correcciones, procedencia y límites del gate.

Los planes [01](01-formatos-y-contrato.md) y
[02](02-backend-presupuesto.md) permanecen cerrados dentro de sus alcances.
Fuentes normativas: [diseño §2](../../../../thesis-docs/plan/design/04-export-sercop-spec.md)
y [v1.3 §2.5/2.8](../../../../thesis-docs/DOCUMENTOS/requerimientos/v1.3-functional-requirements.md).
Las muestras orientan estructura, no autorizan copiar cifras, fórmulas, VAE/CPC,
carátulas, logos o firmas digitales.

## Selección y frontera de lectura

- UUIDv7 público del **presupuesto/version elegida por su owner**; sin sustitución
  silenciosa por vigente ni bypass de ownership para SUPER_ADMIN.
- APUs exclusivamente referenciados por sus rubros; primera aparición en orden
  natural de ítem y desempate estable de identidad. Deduplicación por identidad,
  nunca por código; catálogo y otras versiones quedan fuera.
- [CapturaDocumentoService](../../../src/main/java/ec/uce/propuestas/documento/exportacion/CapturaDocumentoService.java)
  abre `REQUIRES_NEW`, establece `REPEATABLE READ, READ ONLY` antes de ownership
  y preflight, y congela records inmutables sin entidades lazy. Una captura por
  descarga; validación de vínculos/insumos PROYECTO y precarga sin N+1.
- [ApuDescargaService](../../../src/main/java/ec/uce/propuestas/documento/ApuDescargaService.java)
  usa `NOT_SUPPORTED`: captura termina antes del writer; HTTP `@Blocking` evita
  serializar en event loop. No recálculo, revisión, creación de parámetros/versiones
  ni escritura de dominio al exportar. Concurrencia demuestra anterior/posterior,
  no documento híbrido; duración de locks **no medida**.

## Proyección documental, no segundo calculador

[ApuDocumentoProyeccion](../../../src/main/java/ec/uce/propuestas/documento/exportacion/ApuDocumentoProyeccion.java)
separa el original persistido del cálculo canónico capturado, que es diagnóstico.

| Parte | Contrato observado |
|---|---|
| Bloques | M/N/O/P: equipos, mano de obra, materiales, transporte; orden fijo de presentación, sin mutar `orden` de dominio |
| Detalles | Orden persistido dentro del bloque y posición HM; cantidades, costo hora, rendimiento y costos guardados |
| Precios | Efectivos con overrides y base PROYECTO; no consultar CENTRAL/PERSONAL para rellenar |
| HM | Porcentaje efectivo y costo persistido; campos nulos de cantidad/precio/rendimiento no se convierten en campos financieros inventados |
| Subtotales/pie | Subtotales originales por sección, CD/CI/CT persistidos; valor ofertado = CT, no PU del rubro |
| CI | Override individual solo si habilitado; en otro caso porcentaje global capturado; importe persistido no recalculado |
| Parámetros | Vacíos, sufijos, subtotales de bloque/pie, proyecto, enumeración, mensaje y responsables existentes |

Dinero APU usa precisión de display capturada (incluidos p0/p2), PDF `HALF_UP`;
porcentaje tiene precisión independiente. Cantidad/rendimiento conservan su valor.
XLSX escribe BigDecimal exacto como número OOXML, sin fórmulas, macros,
relaciones externas ni hyperlinks. No aplicar p2 indiscriminadamente al cronograma:
su writer conserva strings decimales de la proyección canónica.

## Paginación de las tres salidas

| Salida | Comportamiento verificado |
|---|---|
| [XLSX pestañas](../../../src/main/java/ec/uce/propuestas/documento/ApuXlsxWriter.java) | Una hoja por identidad, nombres saneados/únicos de hasta 31 caracteres |
| XLSX apilado | Una hoja `APUs`; bloques y saltos dinámicos, sin duplicación financiera |
| Ambos XLSX | Presupuesto manual de altura 650 pt; tres filas acotadas de identidad por página; sección/esquema + primera fila física juntos y esquema activo en continuación |
| [PDF](../../../src/main/java/ec/uce/propuestas/documento/ApuPdfWriter.java) | A4 vertical 595×842; cada APU inicia página nueva, UUID/código y esquema repetidos; LiberationSans embebida |

Textos extensos fluyen completos en el cuerpo, fragmentados en filas físicas;
importes del detalle solo en su primera fila. No se exige una página por APU.
Previews de código/descripción XLSX están acotados a 90 caracteres; PDF puede
mostrar «Código extenso (ver encabezado)» en la banda fija y código completo en
el cuerpo. No se promete encabezado ilimitado. Totales/footer/firmantes mantienen
asociación con su APU, sin arrastrar un esquema de sección ajeno.

## Evidencia financiera: dos oráculos distintos

**Histórico V014: exportación exacta del persistido, no paridad con recálculo.**
La medición real usa **298 APUs / 298 rubros**, distinta del inventario histórico
288 y de las 304 pestañas de la muestra. Ambos layouts pasan 12 407 comparaciones
exactas cada uno sobre 2 583 detalles; PDF de 306 páginas (290 APUs de una página,
8 de dos) pasa 1 490 checks de pie con display capturado, cero diferencias.

El seed conserva 318 diferencias frente al cálculo capturado: 38 CD, 140 CI,
140 CT; 238 coinciden a escala 6 HALF_UP y 80 no. Hay 23 diferencias visibles p2
en 8 APUs. No se corrigen datos/motor ni se llama a esto paridad canónica.

**Fixture nuevo calculado y persistido:**
[ApuCalculoPersistidoEvidenceIT](../../../src/test/java/ec/uce/propuestas/documento/ApuCalculoPersistidoEvidenceIT.java)
crea owner/proyecto/v1 aislados, usa CRUD APU, flujo CI y `recalcular`, compromete,
limpia/recarga en otra transacción y valida cuatro secciones/detalles, HM e
identidades. Rubros/consolidación y cronograma MES 1 con actividades/pesos normales
hacen P-32 elegible incluso BORRADOR, sin programa ficticio. Dos casos (1/32 APUs)
pasan: 49/1 568 checks de oráculo/recarga/captura y 87/2 784 checks exactos totales,
incluyendo OOXML de ambos layouts. Ejemplo **sintético**, no registro empresarial:
CD 0.465000, CI 0.046500, CT 0.511500.

## Escala y lectores: alcance observado

| Evidencia | Resultado y límite |
|---|---|
| Histórico 298 | Captura 16 consultas, servicio 16 por formato, writer 0 |
| Fixture nuevo 1/32 | Servicio 17/17 por formato, con freshness de cronograma; no sustituye la medición histórica |
| EXP-04 | Captura 1/32/298 = 16; no era medición de descarga APU |
| Tiempos/heap/JMX/bytes | Observaciones de artefactos, no asserts de allocations ni duración de locks |
| Calc 26.2.6.3 | Representativos: 12 imágenes (6 páginas por layout); extremos: 45 imágenes seleccionadas de cuatro PDF de 177 páginas |
| Extremos XLSX | 708 identidades textuales, esquema en 27 páginas de cuerpo por PDF; no se leyeron 708 imágenes |
| PDF APU | Poppler 110 dpi y selecciones 220 dpi; lotes/índices exactos en la tarea y handoffs; geometría de 102 páginas extremas, 24 048 palabras por PDF dentro de página |

La revisión visual acotada de presupuesto del plan02 conserva **84 imágenes**;
no se reinventa equivalencia integral de EMELNORTE ni cambia el A4 aprobado para
igualar el Letter histórico. Calc headless no certifica Excel nativo ni todos los
glifos. La página seed 56, dispersa y de firmante, conserva identidad y no bloquea.
El aviso PDF página13 fue **retractado** tras releer imágenes/coordenadas; no hubo
fix de header PDF ni reapertura de APU-03.

## Extensión autorizada: cronograma PDF con muchos períodos

El usuario aprobó división horizontal de períodos en
[CronogramaPdfWriter](../../../src/main/java/ec/uce/propuestas/cronograma/export/CronogramaPdfWriter.java)
y sus [tests](../../../src/test/java/ec/uce/propuestas/cronograma/export/CronogramaPdfWriterTest.java),
tras constatar ilegibilidad de una tabla de 52 semanas. Sin cambio de API,
fuentes, escalas ni cálculos: columnas fijas repetidas, base 484 pt, período mínimo
52 pt ajustado al ancho real de strings; ancho disponible papel menos 36 pt,
`setHeaderRows(2)` y preview de proyecto de 80 caracteres.

- A4 horizontal default: 18 páginas, 9 slices W1–6 … W49–52, dos páginas
  verticales por slice. A3 horizontal: 5 páginas, W1–12 … W49–52.
- **23 imágenes completas inspeccionadas**; cada página con rango, proyecto y
  footer. 2 600 celdas de actividad + 208 de resumen exactas; cuatro series completas
  se recortan por slice, sin reiniciar acumulado. W52 conserva .6668; final
  100.0000 % / 1000.000000. Más páginas es el tradeoff aprobado de legibilidad.
- Nit no bloqueante: `Unidad` de 32 pt se parte «Unida/d», visible. No promete
  refinamiento estético adicional. Máximo NUMERIC(14,6) 99 999 999.999999 cabe;
  valores sintéticos de 180 dígitos están fuera del dominio, no son garantía real.
- No se reclama stress específico de proyecto de 4 000 caracteres ni fidelidad
  ilimitada de todo metadato por la sola existencia de preview acotado.

## Gate y procedencia final

- [x] Cinco salidas nuevas: presupuesto XLSX/PDF y tres salidas APU; selección,
  MIME/nombres, bloqueos sin bytes y contrato observado reconciliados.
- [x] Cronograma A4/A3 y paginación autorizada verificados sin debilitar gates
  propios BORRADOR/desviación/final/fecha; XLSX/MSPDI preservados.
- [x] Paridad con persistido, fixture nuevo recargado, concurrencia, consultas y
  lectores acotados registrados; no normalización histórica ni claims universales.
- [x] Regresión focal final: **461 casos / 53 suites, cero fallos/errores/skips**;
  Spotless y `build -x test` PASS. Build sin tests no es una suite PASS.
- [x] Inventario reconciliado: 112 clases / 893 casos únicos = 889 PASS,
  **3 fallos baseline / 1 skip / 0 errores**; 53 clases/461 frescos y 59/432
  reutilizados con fuente/XML confiables. Suite única completa no terminó verde.
- [x] APU-06: cierre autorizado; verificación documental independiente PASS,
  87 enlaces locales/4 anchors y26/26 claims.
- [ ] Autorización de frontend04: no concedida, no iniciado.

Procedencia: [FINAL-PROVENANCE](../../../build/apu05-final-verification/FINAL-PROVENANCE.txt),
[inventario](../../../build/apu05-final-verification/inventory/20261006T020712Z/provenance.txt),
[manifest](../../../build/apu05-final-verification/final-artifacts.sha256) y
[checksums](../../../build/apu05-final-verification/final-checksum-verification.log):
38 inputs, 66 archivos de archivo de regresión, 227 archivos finales verificados.
GM19/GM20 y ScheduleIT siguen fallando; GM24 sin datos sigue omitido. Detalles en
la tarea; ninguna corrección de esos baselines autorizada. Warning ignorado
`quarkus.health.extensions.enabled` y parser SQL ausente/64 JSON sin nodos no se
ocultan ni motivan cambios de configuración/dependencias. RDD off; ASSESS no
evaluable por repositorio externo anidado `ingepresupuestos`, sin approval nativo
ni workaround de ignore.

## Reversión y siguiente paso

APU-06 cerrado; cualquier implementación frontend requiere nueva autorización
explícita. No ejecutar Gradle pesado por reconciliación documental ni iniciar frontend,
publicar ni commitear: prohibición explícita del usuario, sin identidades de commit
inventadas. Reversión revisada: proyección/tests, writers/tests y descarga APU con
sus consumidores; corrección PDF cronograma es frontera separada, preservando A4/A3
previos y presupuesto. Para esta unidad documental, solo los cinco documentos
aprobados; preservar cambios ajenos, muestras, datos, eliminaciones Compunex y DB
usuario. Ninguna reversión exige reset de DB ni comandos destructivos.
