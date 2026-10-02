# Seguimiento humano del ingreso de datasets

**CMT-02: reconstrucción limpia con Flyway 019, recarga Daule y health verificados;
adopción funcional pendiente.** Resultado actual y recibos nuevos al final.
La revisión independiente del rebuild queda pendiente. COPY-02 y las cargas
anteriores se conservan como evidencia histórica, no como inventario actual.

Revisión independiente histórica: **PASS del conjunto de datos/recibos**; resultado
global **PARCIAL** por runtime/API y concurrencia no probados y evidencia de
preservación/DDL no reproducida por completo. Resultados remitidos por el padre;
no se inventan fechas ni firmas. [Tarea LOAD](../../odd/tasks/dataset-daule-data-only-load.md).

## Estado por etapa

| Etapa | Estado actual | Evidencia y puerta restante |
|---|---|---|
| [00](00-diagnostico-y-decisiones.md) | Diagnóstico histórico | Propuesta anterior supersedida por política aprobada |
| [01](01-preflight-y-aprobaciones.md) | Preflight local ejecutado | Uso local autorizado, NO_VERIFICADA |
| [02](02-mapeo-y-preparacion.md) | Release sellada y verificada | 1372/937, UUIDv7 y hashes exactos |
| [03](03-ensayo-de-carga.md) | DB ROLLBACK PASS | Runtime/concurrencia no probados |
| [04](04-carga-controlada.md) | Carga confirmada; replay NOOP | Recibos abajo |
| [05](05-verificacion-y-cierre.md) | DB PASS; cierre global PARCIAL | Validación funcional de aplicación pendiente |
| [06](06-futuros-datasets.md) | NO EJECUTADO | Nueva autorización y puertas propias |

## Preparación y decisiones aprobadas

- RED histórico observado: `ModuleNotFoundError: operator_daule`; GREEN inicial
  6/6 y final **10/10**, también confirmado por revisión independiente.
- Comando de pruebas observado:
  `python3 -B -m unittest discover -s thesis-back-quarkus/database/dataset_ingestion -p 'test_*.py'`.
- Preparación repetida conservó UUID/payload; archivo durable anterior a BD.
- [Release](../../database/dataset_ingestion/releases/daule-v1/release.json):
  SHA256 **e2e19accb4e723b3c7920f4344cead6a3aadc570a009014a8986bd285fcdff35**.
  Los **15 hashes originales** permanecen iguales; NOTAS Markdown es adicional.
- 1372 no-HM y 937 SISTEMA sin cuarentena; especificaciones distintas y E53/E54
  conservados por identidad exacta, nunca fuzzy merge.
- Precio: UNA observación positiva ORIGINAL mínima por
  `(source_id,dataset_origen,precio_observado_id)`, no media/mediana. Las 38
  identidades multiobservación retienen evidencia externa.
- HM snapshot solo placeholder: adopción actual proyecto 5%; las dos fuentes
  1% permanecen intactas. CI histórico 20%/37 ceros generales fuera de snapshot;
  no se promete equivalencia de totales históricos.
- Uso exclusivamente local autorizado; licencia **NO_VERIFICADA**, XLSX/ETL
  Regeneración ausentes, fechas exactas y dependencias fuente no fijadas.

## Corridas históricas locales y recibos (antes del reset)

Destino: PostgreSQL Compose local `propuestas`. No reset, cambios Java/esquema/
seeds ni commits. Ensayo `dry-run` terminó **ROLLBACK PASS**; no se inventa recibo
ni timestamp del ensayo. La recuperación tras commit incierto real no fue ejercida.

| Corrida | Insertados base/insumos/plantillas | Resultado | Recibo |
|---|---|---|---|
| Primera apply | 1/1372/937 | Commit confirmado y readback exacto | [apply-1790908265663817904](../../database/dataset_ingestion/releases/daule-v1/receipt-apply-1790908265663817904.json) |
| Verify posterior | 0/0/0; lectura | Payload exacto PASS | [verify-1790908271288836158](../../database/dataset_ingestion/releases/daule-v1/receipt-verify-1790908271288836158.json) |
| Replay apply | 0/0/0 | NOOP, commit confirmado | [apply-1790908276797904678](../../database/dataset_ingestion/releases/daule-v1/receipt-apply-1790908276797904678.json) |
| Segundo verify | 0/0/0; lectura | Payload exacto PASS | [verify-1790908280695717978](../../database/dataset_ingestion/releases/daule-v1/receipt-verify-1790908280695717978.json) |

## Verificación independiente histórica: alcance preciso

- **PASS**: recibos, 1372 insumos/937 plantillas con payload completo exacto,
  precios originales seleccionados, UUIDv7 y 15 hashes fuente; 10 tests PASS.
- Totales históricos: **6 bases, 2140 insumos, 950 plantillas**. Baseline previo: 5/768/13.
- Preservación de **9 tablas por MD5** comparada y confirmada independientemente:
  `base_insumos`, `insumo`, `plantilla_apu` (filas previas), `proyecto`,
  `presupuesto`, `apu`, `rubro`, `parametros_sistema`, `parametros_proyecto`.
- Worker reportó preservación SHA de las **27 tablas**; algoritmo/baseline completo
  **no reproducidos independientemente**. No elevarlo a garantía independiente.
- Inventario histórico: **27 tablas, 245 columnas y 17 migraciones Flyway**.
  Esquema sin cambios reportado; fingerprint DDL previo completo **no reproducido
  independientemente**. No hubo cambios de schema/Java/seeds durante la carga.

## Pendientes y cierre global

- [x] Carga y replay reconciliados; recibos posteriores al commit disponibles.
- [x] Payload/UUID/precios fuente/hashes exactos verificados independientemente.
- [x] Preservación MD5 de nueve tablas confirmada independientemente.
- [ ] Adopción runtime/API en fixture expresamente autorizado.
- [ ] Concurrencia efectiva de escritores: no ensayada.
- [ ] Reproducir algoritmo SHA worker de 27 tablas/fingerprint DDL previo completo
  si se requiere ampliar la garantía independiente; no inventar baseline ausente.

**Resultado global: PARCIAL**, no PASS integral. Carga verificada en BD;
validación funcional de aplicación pendiente. No ejecutar nuevas cargas para
rellenar evidencia ni alterar parámetros para imitar totales históricos.

## Reglas de mantenimiento

Conservar intentos/recibos y decisiones históricas; no sustituir evidencia real
por fechas estimadas. Ningún secreto/PII. Cada release futura requiere permisos
propios. [Operación y recuperación](../../database/dataset_ingestion/README.md).

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

## CMT-02: reconstrucción con V019 y recarga Daule (2026-10-02)

El padre ejecutó el reset Compose autorizado; este executor no ejecutó comandos
destructivos. Desde el backend: `docker compose --project-directory "$PWD"
-f "$PWD/docker-compose.yml" up -d postgres`, readiness acotada y SELECT inicial
`propuestas|0` tablas públicas. El fast-jar previamente construido contenía V019
idéntica al source y aplicó **19 migraciones**, todas success, versión **019**.
Instancia propia **PID 958963**, puerto **8090**; `/q/health` HTTP 200/UP.

Baseline tras Flyway: **5 bases / 768 insumos / 13 plantillas**. CMT: 298
actividades, pesos y avances suman **100.0000**, cero desviaciones, MES/12 y
298 mapas iguales al peso almacenado en el período original. No se recalcularon
pesos ni costos. La huella revisada sigue ausente: requiere revisión explícita
para quitar el warning stale, no una aprobación fabricada. UCE conserva su
borrador (avance 9.000, 12 desviaciones), no se normalizó su programación.

`prepare` validó la release existente sin regeneración. Operador desde backend:
`python3 -B database/dataset_ingestion/operator_daule.py` con acciones
`dry-run`, `apply --confirm-local-apply`, `verify`, replay apply y verify final.
Ensayo ROLLBACK; primera apply **1/1372/937**, readback posterior al commit PASS;
replay **0/0/0** y ambos verify PASS. Release y operador mantienen los sellos
COPY-02. Recibos nuevos en `database/dataset_ingestion/releases/daule-v1/`:

- `receipt-dry-run-1790923458685945157.json`
- `receipt-apply-1790923459917844917.json`
- `receipt-verify-1790923460542440719.json`
- `receipt-apply-1790923461761193180.json` (replay)
- `receipt-verify-1790923462384733365.json`

Totales finales **6 bases / 2140 insumos / 950 plantillas**; sin cambios de
proyectos/presupuestos/APUs/rubros (3/2/311/310). Conteos y MD5 de filas seed de
las **27 tablas** idénticos antes/después, excluyendo únicamente UUID Daule de
las tres tablas destino. Evidencia: `build/cmt-seed-{baseline,final}.json`,
`build/cmt-seed-local-preconditions.json`, logs `build/cmt-daule-*` y
`build/cmt-seed-rebuild-{startup,health,shutdown}.*`.

SELECT local confirma CMT sin defectos P-32, total positivo y fecha inicial
presente; coincide con preflight real verificado en Dev Services en CMT-01.
No se ejecutó preflight HTTP autenticado, descarga, UI, interoperabilidad ni
concurrencia; no se reclama PASS funcional integral ni revisión independiente.
La instancia propia terminó con SIGTERM tras health final; backend apagado y
PostgreSQL Compose permanece levantado. Sin SQL manual de corrección, cambios
al operador, commits ni staging.
