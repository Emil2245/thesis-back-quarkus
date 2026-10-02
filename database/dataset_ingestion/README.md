# Daule V1: preparación durable y carga local controlada

Operador **standalone Python stdlib**, sin API, cambios Java, migraciones ni dependencias. **COPY-02: reconstrucción limpia y carga local verificadas; adopción funcional de aplicación pendiente.** Flyway ejecutó las 17 migraciones/seeds reales; startup temporal en 8091 y `/q/health` HTTP 200. Apply insertó 1 CENTRAL/1372 insumos/937 plantillas; replay 0/0/0 y dos verify exactos PASS. Revisión independiente de esta reconstrucción pendiente; no se reclama PASS funcional integral. [Registro y recibos nuevos e históricos](../../docs/ingreso-datasets/SEGUIMIENTO.md).

La fuente predeterminada es `sources/dataset_maestro_daule_v1`, incluida íntegramente
junto al operador. No requiere `thesis-docs` ni otro repositorio hermano.

## Ruta rápida (desde la raíz del workspace)

```bash
python3 -B -m unittest discover -s thesis-back-quarkus/database/dataset_ingestion -p 'test_*.py'
python3 -B thesis-back-quarkus/database/dataset_ingestion/operator_daule.py prepare
python3 -B thesis-back-quarkus/database/dataset_ingestion/operator_daule.py dry-run
# SOLO después de verificación independiente y aceptación del ensayo:
python3 -B thesis-back-quarkus/database/dataset_ingestion/operator_daule.py apply --confirm-local-apply
python3 -B thesis-back-quarkus/database/dataset_ingestion/operator_daule.py verify
```

`prepare` no conecta a BD. `dry-run` ejecuta inserciones y readback en una única transacción terminada en ROLLBACK. PostgreSQL puede consumir valores identity incluso tras rollback: no se reparan secuencias ni se promete ausencia de huecos. `verify` no ejecuta INSERT y termina ROLLBACK; requiere que toda la release ya exista. `sql` imprime el ensayo para revisión, no lo ejecuta. No ejecutar `load.sql`/`insert.sql` directamente: son plantillas, no entradas autónomas.

## Artefacto único y política

`releases/daule-v1/release.json` contiene payload completo, mapa UUIDv7, selección original por insumo, hashes SHA256 de los **15 archivos originales**, política y hashes de operador/SQL. Excluye la nueva nota Markdown junto al dataset; no modifica ni regenera el ZIP original. Código `DV1-<insumo_v1_id>` (≤50), descripción/tipo/unidad canónicos exactos, base nueva CENTRAL «Daule V1», sin dueño ni proyecto. Todas las 937 variantes SISTEMA tienen dueño nulo y ET nula (no hay ET inventada).

Cada uno de los 1372 insumos no-HM usa **una observación original positiva** mínima por `(source_id,dataset_origen,precio_observado_id)`. Las 38 identidades con varias observaciones conservan su evidencia externa, sin media/mediana. No se fusionan especificaciones ni candidatos fuzzy; E53/E54 originales no son claves backend. Dos HM ordinarios se excluyen. Snapshot con cuatro secciones en orden EQUIPO/MANO_OBRA/MATERIAL/TRANSPORTE y orden exportado de filas: código/cantidad y rendimiento solo EQ/MO; HM únicamente `esHerramientaMenor:true`.

La creación exclusiva y `fsync` del archivo y directorio impiden sobrescribir una preparación concurrente. **No es un reemplazo atómico multiparchivo**: si se interrumpe una creación puede quedar JSON incompleto; STOP y revisión humana, nunca regenerar identidades silenciosamente. Una release existente debe coincidir con todos los hashes/payload/UUIDv7. No editar el operador tras sellar una release sin revisión explícita: el cambio invalida su sello. El SHA no firma autenticidad; revisión independiente y custodia del archivo siguen necesarias.

## Transacción y recuperación

Destino fijo: `docker compose --project-directory <backend> -f <backend>/docker-compose.yml exec -T postgres psql -X -q -A -t -v ON_ERROR_STOP=1 -U postgres -d propuestas`. No configuración remota ni credenciales en manifests/logs.

1. `lock_timeout=5s`, `statement_timeout=120s`; clave advisory exclusiva **724619238501**, igual para toda la familia de importadores. Locks SHARE ROW EXCLUSIVE sobre las tres tablas impiden carreras de escritores no cooperantes, incluidas bases archivadas.
2. COPY stdin JSONB a TEMP; compara identidad, dueño, archivo/estado y todos los campos authored. Admite ausencia completa o replay completo exacto. **Parcial o discordancia = STOP**; no recuperación insertando faltantes. Colisión global CENTRAL por código también STOP, incluida base propietaria incorrecta.
3. INSERT/SELECT sin BIGINT manual, UPDATE, DELETE ni ON CONFLICT. Readback de todo el payload dentro de la transacción y hashes antes/después de filas ajenas y parámetros/proyectos/presupuestos/APUs/rubros. Cambios externos concurrentes detectados pueden abortar el ensayo/carga.
4. Tras apply confirmado por psql, ejecuta otra sesión `verify` sin inserciones para evidencia posterior al commit. Recibos exclusivos `receipt-<acción>-<timestamp>.json`: insert/noop, hashes fuente/preservación, conteos y digest readback, sin PII.

Si hay timeout, retorno fallido, recibo ausente o fallo al guardarlo después del commit: **NO repetir apply**. Ejecutar `verify` por las identidades durables. Si pasa, el conjunto completo está reconciliado; el recibo verify recupera evidencia del estado actual, no inventa hora de commit ni baseline perdido. Si falla, inspección humana SELECT-only: puede estar ausente/parcial/discordante. Sin reintentos automáticos ni reparaciones destructivas.

## Reuso acotado y límites

`--source <paquete-compatible> --release <release.json> --prefix <MAYUSCULAS> --base-name <nombre>` permiten otra preparación con el mismo contrato de 15 archivos/headers, no un framework general. Cada versión requiere archivo nuevo y autorización de superficies/destino; una misma release con fuente distinta falla. No usar rutas sensibles. El destino sigue exclusivamente Compose local.

Evidencia histórica de la carga anterior al reset: **10 tests unittest PASS**, dry-run ROLLBACK PASS, apply/replay y dos verify PostgreSQL PASS. Revisor confirmó payload completo 1372/937, precios originales seleccionados, UUIDv7, recibos y 15 hashes intactos. Totales históricos locales: **6 bases/2140 insumos/950 plantillas**. Nueve tablas MD5 de preservación confirmadas independientemente; SHA worker de 27 tablas reportado, no reproducido por revisor. Inventario histórico **27 tablas/245 columnas/Flyway 17**; fingerprint DDL previo completo no reproducido independientemente. No reset ni cambios schema/Java/seeds/commits. **Runtime/API/roundtrip con proyecto y concurrencia no probados**; suite Quarkus no ejecutada. Resultado global PARCIAL. Las tres unidades de implementación exceden conjuntamente la guía 400 líneas para mantener validaciones, recuperación y pruebas legibles; no se minifican ni omiten tests.

Semántica aprobada, excepciones y procedencia: [NOTAS_INTEGRACION_BACKEND](sources/dataset_maestro_daule_v1/NOTAS_INTEGRACION_BACKEND.md). Seguimiento humano: [docs/ingreso-datasets](../../docs/ingreso-datasets/README.md).

## COPY-01: fuente local y migración del sello

Copia completa, no movimiento: **16 archivos / 9 550 677 bytes**, sin symlinks,
idénticos al original `../thesis-docs/res/datasets_presupuestos/exports/dataset_maestro_daule_v1`.
Original preservado. README y NOTAS copiados sin edición; sus enlaces entre
repositorios son contexto archivado, no dependencias del operador.

Cambio aprobado exclusivamente de `operator_hashes` y `release_sha256`; no se
regeneró la release. Payload, política, 15 hashes fuente y **2310 UUIDv7** intactos.
`load.sql` e `insert.sql` conservan sus hashes. Sellos SHA256:

| Sello | Antes | Después |
|---|---|---|
| Release (campo `release_sha256`, JSON canónico sin ese campo) | `e2e19accb4e723b3c7920f4344cead6a3aadc570a009014a8986bd285fcdff35` | `111a736b9f60ec6ba8ccfe35ce3686f239f0db9be79fa2e7c3a9c8ec66b8c45e` |
| `operator_daule.py` | `9dbfbb6e8e5005fb4ef51221f735b1381b859b93f4e8427d53d0a508f4d6e97b` | `a5800ca858e0a82068f59aa6bbd4735f12ab6d5c162fc7e2f78af3bb5fbd5ab9` |

Baseline previo verificado después de copiar:
- SHA256 del JSON canónico de todos los campos excepto los dos sellos:
  `5ee1e2760c6e94b5dbc3d06a2ef15df1ac6ee793e620abbb288d77c8f1f70176`.
- SHA256 del inventario `{ruta relativa: SHA256 bytes}` (JSON ordenado compacto):
  `2e075ba9b71592227a92552f7a84014b62073b81739046d994c23fc25facf315`.
  Incluye NOTAS; original y copia coinciden con el baseline previo.

**Los recibos históricos siguen siendo evidencia de la carga anterior**, realizada
con la ubicación original y el sello anterior. No deben coincidir con el nuevo
checksum ni se reescriben. COPY-01 no ejecuta BD ni genera recibos: 12 tests locales
PASS (10 previos + 2 regresiones de ruta, invariantes y rechazo de manipulación).
La validación funcional de aplicación continúa pendiente.

## COPY-02: reconstrucción limpia desde el backend (2026-10-02)

Reset `down -v` del proyecto canónico `thesis-backend` ejecutado externamente
por el padre con autorización explícita. Este executor ejecutó `up -d postgres`
y readiness acotada. SELECT inicial: `propuestas`, cero tablas públicas.
Java 25.0.3 / Gradle 9.5.1; `./gradlew --offline --console=plain build -x test`
PASS. El fast-jar ejecutó realmente las **17 migraciones Flyway** sobre BD vacía;
falló después por 8090 ocupado. Se preservó el backend previo y se arrancó otra
instancia temporal en **8091**, startup PASS y `/q/health` **HTTP 200**.

Desde el cwd backend, sin leer repositorios hermanos:
`python3 -B -m unittest discover -s database/dataset_ingestion -p 'test_*.py'`
**12/12 PASS**; operador `dry-run` ROLLBACK, `apply --confirm-local-apply`,
`verify`, replay apply y verify final PASS. Primer commit confirmado con
readback posterior antes del replay; inserciones **1/1372/937**, replay **0/0/0**.

Baseline limpio observado: **5 bases / 768 insumos / 13 plantillas**;
totales actuales: **6 / 2140 / 950**. Proyectos seed **3**, presupuestos **2**,
APUs **311**, rubros **310**; no son proyectos nuevos creados por la carga.
SELECT por UUID durable confirmó **1 CENTRAL / 1372 insumos / 937 plantillas**.
Conteos y MD5 de las filas seed de **27 tablas**, excluyendo únicamente los UUID
Daule de las tres tablas de destino, idénticos antes/después. Flyway SELECT:
**17 / versión 017 / todas success**. Evidencia local en
`build/copy02-baseline.json`, `build/copy02-final.json` y logs de startup;
reproducción independiente pendiente.

Release actual **111a736b9f60ec6ba8ccfe35ce3686f239f0db9be79fa2e7c3a9c8ec66b8c45e**;
operador **a5800ca858e0a82068f59aa6bbd4735f12ab6d5c162fc7e2f78af3bb5fbd5ab9**.
Payload, UUID y fuentes sin cambios; recibos anteriores conservan su sello histórico.
Recibos nuevos en `database/dataset_ingestion/releases/daule-v1/`:

- `receipt-dry-run-1790917461660039940.json`
- `receipt-apply-1790917471114804611.json`
- `receipt-verify-1790917481410029501.json`
- `receipt-apply-1790917483867238234.json` (replay)
- `receipt-verify-1790917485096547019.json`

No cambios Java/schema/seeds ni commits. Startup/health no prueban adopción
API, roundtrip de proyecto, exportación ni concurrencia; esos checks siguen
pendientes. COPY-02 DB/startup PASS, no PASS funcional integral.

Instancia temporal propia PID **884660** detenida con SIGTERM después de los
checks; backend preexistente preservado y PostgreSQL sigue levantado.
`graphify update .` PASS (5271 nodos/16347 aristas); extracción SQL incompleta
por `tree_sitter_sql` ausente, sin instalar dependencias.
