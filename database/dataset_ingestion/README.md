# Daule V1: preparación durable y carga local controlada

Operador **standalone Python stdlib**, sin API, cambios Java, migraciones ni dependencias. **Carga verificada en BD; validación funcional de aplicación pendiente.** El ensayo local ROLLBACK pasó, apply insertó 1 CENTRAL/1372 insumos/937 plantillas y replay insertó 0/0/0; dos verify exactos PASS. Revisión final independiente datos/recibos PASS; resultado global PARCIAL, no PASS integral. [Registro y cuatro recibos](../../docs/ingreso-datasets/SEGUIMIENTO.md).

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

Evidencia actual: **10 tests unittest PASS**, dry-run ROLLBACK PASS, apply/replay y dos verify PostgreSQL PASS. Revisor confirmó payload completo 1372/937, precios originales seleccionados, UUIDv7, recibos y 15 hashes intactos. Totales locales: **6 bases/2140 insumos/950 plantillas**. Nueve tablas MD5 de preservación confirmadas independientemente; SHA worker de 27 tablas reportado, no reproducido por revisor. Inventario actual **27 tablas/245 columnas/Flyway 17**; fingerprint DDL previo completo no reproducido independientemente. No reset ni cambios schema/Java/seeds/commits. **Runtime/API/roundtrip con proyecto y concurrencia no probados**; suite Quarkus no ejecutada. Resultado global PARCIAL. Las tres unidades de implementación exceden conjuntamente la guía 400 líneas para mantener validaciones, recuperación y pruebas legibles; no se minifican ni omiten tests.

Semántica aprobada, excepciones y procedencia: [NOTAS_INTEGRACION_BACKEND](../../../thesis-docs/res/datasets_presupuestos/exports/dataset_maestro_daule_v1/NOTAS_INTEGRACION_BACKEND.md). Seguimiento humano: [docs/ingreso-datasets](../../docs/ingreso-datasets/README.md).
