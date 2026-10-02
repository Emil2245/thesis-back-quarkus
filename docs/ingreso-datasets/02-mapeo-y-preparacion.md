# 02 — Mapeo y preparación inmutable

**Estado: release preparada, sellada y cargada localmente.**
[Operador stdlib](../../database/dataset_ingestion/README.md) y manifest durable
`database/dataset_ingestion/releases/daule-v1/release.json` preparados sin conectar
BD durante la preparación. Posteriormente apply confirmó 1372 no-HM/937 SISTEMA
y una CENTRAL, replay 0/0/0 y dos verify exactos PASS. Diez tests y dry-run
ROLLBACK PASS. [Recibos](SEGUIMIENTO.md); revisión independiente de datos/UUIDv7/
precios originales/15 hashes PASS. Global PARCIAL; runtime/API y concurrencia
no probados. Preservación nueve MD5 confirmada; 27 SHA worker y DDL previo completo
no reproducidos independientemente.

## Objetivo y dependencia previa

Preparar una release reproducible, con identidad durable antes de cualquier
escritura de BD. Depende de [01 aprobado](01-preflight-y-aprobaciones.md).

## Entradas

- [Insumos](../../database/dataset_ingestion/sources/dataset_maestro_daule_v1/insumos_v1.csv).
- [Variantes](../../database/dataset_ingestion/sources/dataset_maestro_daule_v1/apus_v1.csv)
  y [componentes](../../database/dataset_ingestion/sources/dataset_maestro_daule_v1/apu_componentes_v1.csv).
- [Precios observados](../../database/dataset_ingestion/sources/dataset_maestro_daule_v1/precios_observados_v1.csv).
- [Mapper actual](../../src/main/java/ec/uce/propuestas/plantilla/service/SnapshotApuMapper.java)
  y [V012](../../src/main/resources/db/migration/V012__seed_catalogo_plantillas_apu.sql).
- Acta de 01 localizable desde [SEGUIMIENTO](SEGUIMIENTO.md).

## Superficies y acciones futuras permitidas

Tras autorización: lectura del export y generación en depósito externo aprobado
de manifiesto de release, mapa, payload, cuarentena y futuro script de operador.
No modificar CSV fuente. Revisar SQL propuesto contra tablas existentes antes de
ensayo; este documento no contiene un artefacto SQL ejecutable.

## Acciones prohibidas

Escribir BD; migraciones o tablas permanentes nuevas; editar V001–V017, backend,
resolver o snapshots. CLI Python stdlib standalone autorizado; no dependencias nuevas. No fusionar candidatos
fuzzy, imponer BIGINT ni generar UUIDv7 falsos a partir de SHA. No insertar HM
como insumo ordinario, ni precios/IDs/CI/rendimiento general en el snapshot.

## Pasos

1. Congelar versión, prefijo DV1 y hashes de los 15 archivos. Separar identidad
   canónica de código original y de identidad pública de backend.
2. Crear una sola identidad de base CENTRAL adicional; no reutilizar nombre
   como identificador porque no es único. Definir nombre documental de release.
3. Excluir los dos recursos HM: quedan 1372 insumos antes de cuarentena.
   Mapear cada ID admitido a `DV1-<insumo_v1_id>` sin dependencia de fuente;
   comprobar unicidad global, tipo compatible y longitud ≤50.
4. Conservar `codigo_original`, aliases (todos principal=false), precios,
   orígenes y candidatos externamente; E53/E54 repetidos conservan ambos IDs
   válidos sin fusión ni cuarentena innecesaria.
5. Aplicar solo política de precios/unidades aprobada en 01 a columnas existentes.
   Conservar valor original y regla por fila. No asumir tarifa=costo_hora.
6. Preparar una plantilla SISTEMA por variante admitida, hasta 937. Nombre puede
   incluir release/ID para lectura humana, pero nunca será clave de reconciliación.
7. Construir `secciones` en EQUIPO, MANO_OBRA, MATERIAL, TRANSPORTE, incluyendo
   vacías. Conservar secuencia exportada dentro de cada sección, incluida HM.
   Línea ordinaria: `insumoCodigo`, `cantidad`, `rendimiento` cuando corresponde;
   HM: solamente `esHerramientaMenor:true`. Cada variante tiene su placeholder.
8. Exigir cantidad/rendimiento positivos en EQ/MO no-HM. MAT/TR admiten
   rendimiento ausente. Guardar rendimiento general externamente; los 37 ceros
   no se invierten ni se rechazan solo por ser metadatos no consumidos.
9. Aplicar decisión HM a variantes concretas; registrar las dos al 1% separadas
   de las 935 al 5%. Una cuarentena reduce plantillas, no cambia parámetros.
10. Generar UNA VEZ UUIDv7 válidos para base, cada insumo y cada plantilla;
    almacenar `public_id` junto con ID canónico, código, tipo y contenido esperado.
    El hash verifica contenido, no produce la identidad pública.
11. Guardar durablemente el archivo de identidades preparado antes de DB write.
    Sellar hashes del mapa/payload y versión de transformador; cualquier cambio
    posterior obliga a detenerse y preparar revisión aprobada, no mutar el sellado.
12. Diseñar operador data-only: `psql COPY` por stdin hacia TEMP staging y
    `INSERT/SELECT` en tablas actuales. Omitir BIGINT para usar identity/defaults;
    resolver base interna por su UUID durable en INSERT/SELECT y verificar todo
    el readback en la misma transacción. No `setval`.
13. Especificar comparación por UUID y contenido completo normalizado: match
    exacto=NOOP, discordancia=STOP, ausente=insertar. No `DO UPDATE` ni comparación
    solo por nombre. Revisar vínculos de insumos a base y códigos además de JSONB.

## Comprobaciones de salida

- [ ] Identidades UUIDv7 únicas y persistidas; mapa sellado verificable.
- [ ] Todos los códigos no-HM resuelven exactamente al tipo previsto.
- [ ] Cuatro secciones, orden por sección, HM y whitelist de campos comprobados.
- [ ] Conteos esperados de base=1, insumos≤1372, plantillas≤937 explicados.
- [ ] Cuarentena incluye IDs/motivo/decisión; fuzzy no fusionado.
- [ ] Script futuro no depende de APU ficticio ni API raw inexistente.

## Condiciones STOP y evidencia

El precio aprobado es UNA observación positiva original mínima por fuente,
dataset e ID; 38 insumos tienen múltiples observaciones. Snapshot omite rendimiento
MAT/TR. CI histórico 20% y 37 ceros generales son evidencia externa. HM 5% aprobado
para todas las variantes, conservando dos originales 1% en CSV. Release sella los
15 originales, excluyendo NOTAS_INTEGRACION_BACKEND.md añadido separadamente.

STOP por falta de identidad durable, UUID inválido, referencia ausente, campo
extra, colisión operativa o diferencia no aprobada. Guardar hashes, versión,
conteos por tipo/sección, mapa de identidades, cuarentenas y revisión del operador
en depósito externo y enlazarlos en [SEGUIMIENTO](SEGUIMIENTO.md).

## Siguiente paso

[03 — Ensayo](03-ensayo-de-carga.md), con autorización específica del entorno.
