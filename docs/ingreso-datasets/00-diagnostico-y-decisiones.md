# 00 — Diagnóstico y decisiones de compatibilidad

## Diagnóstico inicial histórico y actualización operativa

Este documento conserva la auditoría inicial, **no el estado actual de la carga**.
La propuesta de mediana opcional quedó supersedida: precio aprobado = UNA
observación positiva original mínima por `(source_id,dataset_origen,precio_observado_id)`.
Carga local verificada en BD (1/1372/937), replay NOOP y revisión de datos PASS;
resultado global PARCIAL, validación funcional de aplicación pendiente.
[SEGUIMIENTO](SEGUIMIENTO.md) distingue nueve tablas MD5 confirmadas de la
preservación SHA de 27 tablas reportada por worker, no reproducida independientemente.

## Objetivo y contexto histórico

Conservar los hallazgos verificados de la auditoría remitida y corregir la
recomendación anterior: **el catálogo separado está sustituido**. No se requiere
rediseño de BD ni API nueva para añadir estos datos. En aquel diagnóstico la instancia de BD aún no se había
inspeccionado; los conteos siguientes describen archivos, no filas cargadas.

Dependencia: [tarea documental](../../odd/tasks/dataset-daule-data-only-plans.md).
Este diagnóstico no autoriza acciones operativas; su salida alimenta [01](01-preflight-y-aprobaciones.md).

## Entradas y trazabilidad

- [README del export](../../../thesis-docs/res/datasets_presupuestos/exports/dataset_maestro_daule_v1/README.md)
  y [manifiesto](../../../thesis-docs/res/datasets_presupuestos/exports/dataset_maestro_daule_v1/manifiesto.json).
- [SnapshotApuMapper](../../src/main/java/ec/uce/propuestas/plantilla/service/SnapshotApuMapper.java).
- [ResolverInsumoPlantillaService](../../src/main/java/ec/uce/propuestas/plantilla/service/ResolverInsumoPlantillaService.java).
- [V001](../../src/main/resources/db/migration/V001__baseline.sql),
  [V003](../../src/main/resources/db/migration/V003__seed_insumos.sql),
  [V012](../../src/main/resources/db/migration/V012__seed_catalogo_plantillas_apu.sql).
- [Base de datos](../03-BASE-DATOS.md) y [escenarios semilla](../04-SEED-ESCENARIOS.md).

## Inventario verificado remitido

El export contiene 15 archivos incluyendo README y manifiesto. IDs y referencias
entre CSV se comprobaron sin incidencias; el ZIP resultó idéntico byte a byte.

| Conjunto | Filas |
|---|---:|
| fuentes / archivos fuente | 2 / 3 |
| insumos / aliases / precios observados | 1374 / 1412 / 1410 |
| conceptos / variantes / componentes | 934 / 937 / 7742 |
| orígenes APU / detalles de origen | 1575 / 13143 |
| candidatos insumo / APU | 734 / 571 |
| validaciones de consolidación | 0 |

Los 571 candidatos APU son **568 pares de conceptos + 3 pares de variantes**;
no son todos pares de variantes. Los 1412 aliases tienen principal=false.
Candidatos fuzzy no equivalen a duplicados aprobados ni se fusionan.

## Algoritmos auditados, no instrucciones de ejecución

La celda 9 del notebook de consolidación define identidad de insumo por tipo,
unidad normalizada, HM y descripción normalizada; excluye códigos y precios.
La huella completa APU usa descripción, unidad, rendimiento general y multiset
ordenado de componentes; excluye orden de filas y precios. La preparación para
el backend debe conservar el orden exportado por sección, aunque la huella no
lo considere. Normalización numérica: seis decimales HALF_UP en el dataset,
no una orden de cambiar la precisión del motor.

Se reconstruyeron 1575 huellas de origen coincidentes y 1374 medianas ponderadas
coincidentes, con punto medio inferior. `tarifa_original` en las fórmulas fuente
no significa universalmente `costo_hora`: documentar su interpretación por tipo.

## Huecos que siguen abiertos

- Las 61 anotaciones INFO intermedias no están en la validación final.
  Cero filas de validación final no demuestra ausencia de decisiones pendientes.
- Faltan XLSX original de Regeneración y notebook ETL correspondiente.
- Licencias de las fuentes: NO_VERIFICADA; fechas exactas de precio ausentes.
- pandas y rapidfuzz no fijados; reproducibilidad futura pendiente.
- Los hashes de los dos XLSX de Malecón sí coincidieron.

Mantener estos límites en la evidencia externa; no inventar permisos, fechas,
archivos faltantes ni resultados de reejecución de notebooks.

## Compatibilidad y decisiones de datos

| Tema | Tratamiento compatible | Puerta pendiente |
|---|---|---|
| E53/E54 originales repetidos | Conservar ambos IDs canónicos con códigos DV1 distintos | Verificar mapa y colisiones reales; no cuarentena solo por alias |
| HM | Excluir dos recursos canónicos del insumo ordinario; placeholder en cada variante | Aprobar adaptación o cuarentena de variantes |
| HM histórico | 935 componentes de variantes al 5%, dos al 1% aunque el nombre diga 5% | No cambiar parámetro de proyecto ni crear override |
| %CI original 20% | No pertenece al snapshot de plantilla | Aceptar semántica actual del proyecto |
| Rendimiento general | 37 variantes con cero: metadato externo no usado por snapshot | No invertir ni rechazar por ese cero aislado |
| Unidades | 24 cadenas; `unidad` VARCHAR(10), sin FK a `unidad_catalogo`; EQ/MO `h` | Medir longitudes y soporte real de consumidores |
| Precio | Fuera del snapshot; propuesta histórica de mediana descartada | Aprobada observación original positiva individual por orden fuente/dataset/ID |

No se presume incompatibilidad de las 24 unidades ni se exige sembrar unidades
nuevas. Las cantidades y rendimientos de líneas no-HM EQ/MO sí deben ser positivos;
MAT/TR pueden no tener rendimiento. No confundir este control con el metadato general.

## Contrato actual, no propuesta de backend

El mapper escribe `secciones` con `tipo` y `lineas`; cada línea ordinaria admite
`insumoCodigo`, `cantidad`, `rendimiento`, y HM solo `esHerramientaMenor:true`.
No precios ni IDs. Preparar las cuatro secciones, preservando el orden por sección.
El resolver prioriza PROYECTO, después CENTRAL visible por código/tipo y luego
PERSONAL del dueño. Si hay varios CENTRAL, elige por ID interno: la base separada
no basta para evitar ambigüedad; los códigos deben ser globalmente distintos.

La API admin crea SISTEMA **desde un APU existente**, no desde JSON crudo.
Un futuro script de operador puede insertar el JSONB existente como V012, sin
endpoint ni APU ficticio. No se modifica el mapper, el resolver ni las migraciones.
V003 contiene 93 insumos (16 MO/11 EQ/66 MA); V012 contiene 11 plantillas con 51
referencias de código resueltas. Se usan identities autogeneradas sin `setval`
ni sobreescritura; los UUID fijos de seeds no se reutilizan.

## Comprobaciones y STOP

- [ ] Atribuir estos hallazgos a la auditoría histórica de archivos; consultar
  SEGUIMIENTO para la instancia ya cargada/verificada.
- [ ] Registrar aceptación de pérdidas semánticas antes de preparar la release.
- [ ] Contrastar contrato vigente durante el preflight futuro, solo lectura.

**STOP:** contradicción con archivos o contrato vigente, exigencia de exactitud
histórica sin soporte, propuesta de catálogo nuevo o necesidad de alterar backend.
Registrar discrepancia y decisión humana; no resolverla introduciendo campos.

## Superficies, prohibiciones y evidencia

Alcance del diagnóstico inicial: documentación únicamente. En revisión futura: lectura de export, código y
contratos; registro externo de discrepancias aprobado. Prohibido ejecutar ETL,
carga, SQL, migraciones o modificación de fuentes como parte del diagnóstico.
Evidencia a registrar: revisión, fecha, responsable, hashes consultados y decisiones
no resueltas en [SEGUIMIENTO](SEGUIMIENTO.md), sin marcar carga completa.

Siguiente: [01 — Preflight y aprobaciones](01-preflight-y-aprobaciones.md).
