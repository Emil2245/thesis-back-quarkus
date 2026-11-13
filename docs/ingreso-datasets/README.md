# Ingreso de datasets: planes de datos, no rediseño

**Carga verificada en BD; validación funcional de aplicación pendiente.** El usuario
aprobó precios originales individuales, especificaciones distintas y HM 5% con
las dos excepciones fuente 1% documentadas. [Operador y tests](../../database/dataset_ingestion/README.md)
con ensayo ROLLBACK PASS, apply 1/1372/937, replay 0/0/0 y dos verify exactos PASS.
Totales locales: bases **6**, insumos **2140**, plantillas **950**. Baseline previo
confirmado independientemente por MD5 en nueve tablas. Preservación SHA de 27
reportada por worker, no reproducida independientemente. Inventario actual:
27 tablas/245 columnas/Flyway 17 (al momento de la carga; hoy V001–V026); fingerprint DDL previo completo no reproducido.
Revisión final datos/recibos PASS; global PARCIAL por esos límites y runtime/API/
concurrencia no probados.

## Estado

Los documentos por fase (00–06) se retiraron al cerrar la carga; su evidencia queda en los
recibos JSON versionados, en [SEGUIMIENTO](SEGUIMIENTO.md) y en el historial git. Hoy: carga
**ejecutada** (replay NOOP); verificación de datos PASS; validación funcional de la aplicación
**pendiente**; futuros datasets **no ejecutados** (requieren nueva autorización). Una
aprobación de precio no aprueba HM ni la carga.

## Alcance cerrado

- Una base CENTRAL adicional para Daule V1, separada de IESS; insumos ordinarios
  no-HM y plantillas APU SISTEMA separadas de las existentes.
- Transformación probada: 1372 insumos ordinarios y 937 variantes admitidas,
  sin cuarentena ni fusión de especificaciones.
- Códigos operativos únicos por ID canónico, con prefijo fijo de release
  `DV1-<insumo_v1_id>` y longitud máxima 50. El código original queda fuera de BD.
- Evidencia externa versionada: aliases, precios, orígenes, candidatos, hashes,
  mapeos de identidad, cuarentenas y recibos. No nuevos campos de procedencia.
- Inserción ejecutada de JSONB en `plantilla_apu` existente, como patrón de V012;
  no crear un APU ficticio para usar la API administrativa.

## Limitaciones que deben aceptarse

El snapshot no conserva precio, %CI histórico ni porcentaje HM por variante.
La adopción usa precios y parámetros del proyecto existente; no reproduce
necesariamente los totales históricos. Las medianas documentales **no se usan**: se aprobó una observación original
positiva individual mínima por fuente/dataset/ID de observación.

No hay deduplicación semántica global entre releases, historia nativa de precios
ni procedencia nativa. V2 coexistirá con V1; precios nuevos requieren otra acción
explícita y nunca sincronizan proyectos existentes automáticamente.

## Fuera de alcance

No tablas `catalogo_*`, esquema versionado, migraciones ni cambios de backend.
No snapshot v2, IDs de catálogo en snapshots, campos nuevos ni cambios de resolver.
No nuevos endpoints, módulos, CLI Go/pgx/sqlc/Quarkus, dependencias ni notebooks
Ejecutados. No plantillas de proyecto ni proyectos, APUs o presupuestos ficticios.
No sobrescribir o borrar seeds ni datos de usuarios; V001–V017 quedan intactas.
No ejecución automática de migraciones al arrancar como paso de carga.

## Referencias y autorización

- [Guía del backend](../../CLAUDE.md) y [planes vigentes](../../plans/README.md).
- [Dataset exportado](../../database/dataset_ingestion/sources/dataset_maestro_daule_v1/README.md).

La autorización de la carga quedó registrada en el historial git (tarea LOAD, retirada).
Depósito: `database/dataset_ingestion/releases/daule-v1/`; destino fijo Compose
local `propuestas`. [Notas junto al dataset](../../database/dataset_ingestion/sources/dataset_maestro_daule_v1/NOTAS_INTEGRACION_BACKEND.md)
separan hechos originales de adaptaciones aprobadas. Los cuatro recibos de
apply/verify están enlazados en [SEGUIMIENTO](SEGUIMIENTO.md); los 15 hashes fuente
permanecen iguales. Los recibos conservan el sello histórico, no el checksum
actual tras COPY-01; véase [relocación y sellos](../../database/dataset_ingestion/README.md#copy-01-fuente-local-y-migración-del-sello).
La fuente operativa actual está incluida en el backend; la carga histórica usó
`../thesis-docs/res/datasets_presupuestos/exports/dataset_maestro_daule_v1`.

## Siguiente paso

Resultado independiente: datos PASS, global PARCIAL. Autorizar separadamente validación funcional; runtime/API
y concurrencia siguen no probados;
licencia NO_VERIFICADA limita la autorización al uso local.
