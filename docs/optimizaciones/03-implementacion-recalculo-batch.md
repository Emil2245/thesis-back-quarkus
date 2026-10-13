# B01 — Árbol del snapshot con lecturas por lote

B01 reduce las lecturas de capítulos y rubros sin cambiar el snapshot ni los
resultados exactos del Motor. B02 añade la hidratación batch de APUs descrita
abajo; la regresión funcional completa queda para B03.

## Implementación y equivalencia

- [Builder](../../src/main/java/ec/uce/propuestas/recalculo/internal/VersionSnapshotBuilder.java): una lectura de capítulos del presupuesto y una lectura batch de rubros alcanzables; sin consultas por capítulo durante la travesía.
- Mapas locales por padre/capítulo; raíces y hermanos ordenados establemente por `orden`, rubros con `ItemJerarquico.ORDEN`, profundidad y árbol recursivo conservados. Capítulos desconectados de las raíces quedan excluidos.
- [Prueba](../../src/test/java/ec/uce/propuestas/recalculo/VersionSnapshotTreeBatchIT.java) y [fixtures](../../src/test/java/ec/uce/propuestas/recalculo/VersionSnapshotBatchFixtures.java): snapshots completos, Motor exacto, aislamiento de presupuesto y APUs vacíos.

## Evidencia observada

RED anterior: 3 capítulos preparaban 16 statements; 33 preparaban 76.
GREEN B01: **11 y 11**, con 1 prueba, 0 fallos y 0 errores. La métrica cubre todo
el build, no solo el árbol; se mantiene constante el número de APUs y no hay
cronograma. No representa todavía el escalado de APUs de B02.

Comando ejecutado desde el backend, con URL sin override y PostgreSQL 18 efímero:

```bash
env -u DB_URL -u DB_USER -u DB_PASSWORD -u QUARKUS_DATASOURCE_JDBC_URL -u QUARKUS_DATASOURCE_DEVSERVICES_ENABLED QUARKUS_PROFILE=test QUARKUS_DATASOURCE_DEVSERVICES_ENABLED=true QUARKUS_DATASOURCE_USERNAME=quarkus_test QUARKUS_DATASOURCE_PASSWORD=quarkus_test QUARKUS_HTTP_TEST_PORT=0 ./gradlew --no-daemon test --tests 'ec.uce.propuestas.recalculo.VersionSnapshotTreeBatchIT'
```

El guard comprobó la conexión antes de los fixtures: puerto JDBC 32775 del
contenedor `04ff373875f2`; HTTP efímero 34247. No se usó la base del usuario.

## Rollback y límite

Revertir solo el cambio del builder y retirar esta prueba, su helper y este
documento deshace B01 sin cambiar Motor, servicios, persistencia ni cronograma.
Los informes 01/02 permanecen históricos; la verificación final B03 sigue pendiente.

## B02 — APUs referenciados, sin fanout por fila

El build completo carga por lote únicamente APUs de rubros alcanzables, sus
secciones, detalles e IDs de insumos referenciados (también bases compartidas).
Los cuatro repositorios devuelven vacío sin consultar cuando no reciben IDs.
Mapas locales conservan referencias gestionadas, sin caché entre builds.
Proyecto/parámetros se reutilizan por presupuesto/proyecto: el presupuesto actual
ya está cargado; referencias a otro presupuesto conservan su propio %HM.

- Secciones por tipo canónico, detalles por `orden` y rubros por orden natural.
- Se conserva `snapshotDeDetalle`: HM × 100, insumo nulo, precios heredados y overrides; CI del APU y parámetros de versión sin cambios.
- Motor, ApuCalculoService (incluido cálculo individual), servicios, transacciones, esquema y configuración no se modifican.

[Prueba B02](../../src/test/java/ec/uce/propuestas/recalculo/VersionSnapshotApuBatchIT.java):
300 y 1000 APUs, cuatro secciones y trece filas por APU, costos no nulos,
HM de otro proyecto (7% frente a 5%), CI alterno, insumos centrales compartidos,
pendientes sin insumo y APU no vinculado excluido. Igualdad de snapshot completo
y Motor exacto; estadísticas verifican las entidades realmente hidratadas.

| Escenario        |       RED B01 | GREEN B02 |
| ---------------- | ------------: | --------: |
| 300 APUs         |          2110 |        11 |
| 1000 APUs        |          7010 |        11 |
| 3 / 33 capítulos | 11 / 11 (B01) |     7 / 7 |

GREEN: 2 pruebas, 0 fallos; PostgreSQL 18 efímero, 20 migraciones, guard antes
de fixtures en JDBC 32779 y HTTP 35841. RED previo: fallo de cota SQL después
de pasar snapshots/Motor, sin correcciones de fixture. Métrica de statements
preparados del build, **no latencia HTTP**, heap ni concurrencia.

Comando GREEN (mismo prefijo aislado del comando B01):

```bash
./gradlew --no-daemon test --tests 'ec.uce.propuestas.recalculo.VersionSnapshotApuBatchIT' --tests 'ec.uce.propuestas.recalculo.VersionSnapshotTreeBatchIT'
```

Rollback B02: retirar únicamente este delta del builder, los cuatro métodos
batch, la prueba B02 y su extensión de fixtures/documentación, conservando B01.
B03 realizará la regresión funcional completa y actualización del grafo.
