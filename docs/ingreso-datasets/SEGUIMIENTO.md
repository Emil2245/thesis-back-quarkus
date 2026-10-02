# Seguimiento humano del ingreso de datasets

**Carga verificada en BD; validación funcional de aplicación pendiente.**
Revisión independiente final: **PASS del conjunto de datos/recibos**; resultado
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

## Corridas locales y recibos

Destino: PostgreSQL Compose local `propuestas`. No reset, cambios Java/esquema/
seeds ni commits. Ensayo `dry-run` terminó **ROLLBACK PASS**; no se inventa recibo
ni timestamp del ensayo. La recuperación tras commit incierto real no fue ejercida.

| Corrida | Insertados base/insumos/plantillas | Resultado | Recibo |
|---|---|---|---|
| Primera apply | 1/1372/937 | Commit confirmado y readback exacto | [apply-1790908265663817904](../../database/dataset_ingestion/releases/daule-v1/receipt-apply-1790908265663817904.json) |
| Verify posterior | 0/0/0; lectura | Payload exacto PASS | [verify-1790908271288836158](../../database/dataset_ingestion/releases/daule-v1/receipt-verify-1790908271288836158.json) |
| Replay apply | 0/0/0 | NOOP, commit confirmado | [apply-1790908276797904678](../../database/dataset_ingestion/releases/daule-v1/receipt-apply-1790908276797904678.json) |
| Segundo verify | 0/0/0; lectura | Payload exacto PASS | [verify-1790908280695717978](../../database/dataset_ingestion/releases/daule-v1/receipt-verify-1790908280695717978.json) |

## Verificación independiente: alcance preciso

- **PASS**: recibos, 1372 insumos/937 plantillas con payload completo exacto,
  precios originales seleccionados, UUIDv7 y 15 hashes fuente; 10 tests PASS.
- Totales: **6 bases, 2140 insumos, 950 plantillas**. Baseline previo: 5/768/13.
- Preservación de **9 tablas por MD5** comparada y confirmada independientemente:
  `base_insumos`, `insumo`, `plantilla_apu` (filas previas), `proyecto`,
  `presupuesto`, `apu`, `rubro`, `parametros_sistema`, `parametros_proyecto`.
- Worker reportó preservación SHA de las **27 tablas**; algoritmo/baseline completo
  **no reproducidos independientemente**. No elevarlo a garantía independiente.
- Inventario actual: **27 tablas, 245 columnas y 17 migraciones Flyway**.
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
