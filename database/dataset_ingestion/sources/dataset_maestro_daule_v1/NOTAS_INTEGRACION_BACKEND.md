# Daule V1: evidencia original y adaptación operativa al backend

Se autoriza uso **local** del dataset y preparación de carga DATA-ONLY al backend existente. La carga está verificada en BD; validación funcional de aplicación pendiente. Esta nota no certifica totales históricos ni licencia de redistribución. Los 15 archivos originales y su ZIP consolidado permanecen intactos; este Markdown es un complemento separado y **no integra sus hashes originales**.

## Decisiones aprobadas frente a hechos fuente

| Tema | Evidencia original | Adopción autorizada |
|---|---|---|
| Insumos | 1374 canónicos, incluidos dos HM sin precio ordinario | 1372 filas no-HM; identidad exacta, sin fuzzy merge |
| Precios | Observaciones documentales; 38 insumos con varias observaciones | Una observación positiva original por identidad: menor `(source_id,dataset_origen,precio_observado_id)`, jamás promedio/mediana sintetizada |
| Códigos | E53/E54 originales pueden repetirse entre especificaciones | `DV1-<insumo_v1_id>` mantiene cada especificación distinta, ≤50 caracteres |
| Plantillas | 937 variantes estructurales | Todas incluidas como SISTEMA sin usuario, con descripción/unidad canónica y cuatro secciones |
| HM | 935 variantes al 5%, dos al 1% | Placeholder protegido sin porcentaje; adopción actual 5% aprobada para todas |
| CI | Referencia histórica 20% | No viaja en snapshot; aplica CI del proyecto/APU según contrato vigente |
| Rendimiento general | 37 valores cero como metadatos | Permanecen en CSV externo; no se invierten ni pasan a snapshot |

El insumo ordinario conserva tipo, descripción y unidad canónicos exactos. EQ/MO usan `h`; unidades observadas no-HM tienen longitud máxima 10. Snapshot: `insumoCodigo`, `cantidad`, `rendimiento` positivo solo EQ/MO; MAT/TR omiten rendimiento. HM solo `esHerramientaMenor:true`. No contiene precios, IDs internos, links fuente, CI, porcentaje HM ni rendimiento general. Se conserva orden exportado dentro de cada sección, incluidas secciones vacías. Aliases (principal=false), todas las observaciones, orígenes y candidatos quedan en CSV; hashes y referencia UUID/código/observación se guardan fuera de BD. No se altera normalización canónica ni se fusionan equivalencias sugeridas.

## Dos excepciones originales HM al 1% — preservadas, no corregidas

Fuente MALECON, proceso **LPI-GADIMCD-2025-03**, proyecto «Construcción del Malecón parroquia urbana satélite La Aurora». Workbook **ANALISIS-DE-PRECIOS-UNITARIOS-1-1000-EXCEL.xlsx**, sheet **1**:

| APU original | Identidad V1 | Descripción | Fila XLSX |
|---|---|---|---|
| 584 | `APUV1-16c0934d182bb45fdc1966a1` | INSTALACION DE TUBERIA PVC LISA DI=75 mm (DESAGUE CISTERNA) | 49650 |
| 838 | `APUV1-d966969815ab48914ba7aec7` | SUMINISTRO E INSTALACION DE TEE 3/4 A 1/2 IN PP ROSCABLE | 71291 |

El CSV mantiene **0.010000** en estas variantes; no se reescribe a 5%. La inspección local SELECT-only remitida por el padre verificó sistema y los **tres proyectos** con HM **0.0500**. No se actualizan parámetros. El usuario aprobó adoptar 5% ahora también para estas variantes; cambios futuros del porcentaje del proyecto seguirán gobernando las plantillas. Es una adaptación operativa explícita, no una afirmación de equivalencia con el workbook.

## Totales, precios y procedencia: límites que permanecen

Aplicar una plantilla usa precios de la base del proyecto y parámetros vigentes. El precio elegido conserva un dato original, **no garantiza precio actual de mercado**; fechas exactas de precios siguen ausentes. Con HM 5% frente a dos originales 1%, CI del proyecto frente a referencia 20% y reglas de cálculo vigentes, **no se promete reproducción de totales históricos** ni de medianas documentales.

Licencias siguen **NO_VERIFICADA**: autorización local no permite afirmar derechos de redistribución. Falta XLSX original y notebook ETL de Regeneración; dependencias fuente no fijadas. Estos huecos permanecen visibles, no se inventa procedencia ni se ejecutan notebooks para suplirla.

## Estado de carga local observado

Primera apply: **1 CENTRAL/1372 insumos/937 plantillas**; replay **0/0/0** y dos
verify de payload exacto PASS. Totales locales **6/2140/950**. Revisión independiente
final de datos, recibos, precios seleccionados, UUIDv7 y 15 hashes: PASS; **resultado
global PARCIAL**. Nueve tablas MD5 preservadas confirmadas independientemente;
SHA de 27 tablas reportado por worker, no reproducido por revisor. Inventario actual
27 tablas/245 columnas/Flyway 17; fingerprint DDL previo completo no reproducido.
No reset ni cambios Java/esquema/seeds/commits. Runtime/API y concurrencia no probados.
Los límites de licencia/procedencia y las dos excepciones fuente 1% siguen intactos.

[Recibos y alcance de verificación](../../../../../thesis-back-quarkus/docs/ingreso-datasets/SEGUIMIENTO.md)
y [README operativo](../../../../../thesis-back-quarkus/database/dataset_ingestion/README.md).

## Integración y siguiente puerta

Base nueva CENTRAL «Daule V1», aislada de IESS, sin usuario ni proyecto. PK/FK internas BIGINT por identity/default; UUIDv7 públicos verdaderos aleatorios generados una vez antes de BD. Ni esquema, motor, Java, seeds, parámetros ni datos de usuarios se modifican. Replay completo exacto = NOOP; diferencias/colisiones/estado parcial = STOP. Ensayo ROLLBACK y revisión independiente de datos ya ejecutados; validación funcional de aplicación pendiente.

- [Operador, manifest, tests y comandos](../../../../../thesis-back-quarkus/database/dataset_ingestion/README.md).
- [Planes y seguimiento backend](../../../../../thesis-back-quarkus/docs/ingreso-datasets/README.md).
- [README original del export](README.md) y [manifiesto original](manifiesto.json), sin modificaciones.
