# B01 — Árbol del snapshot con lecturas por lote

B01 reduce las lecturas de capítulos y rubros sin cambiar el snapshot ni los
resultados exactos del Motor. APUs, secciones, detalles y parámetros siguen sin
optimizar: corresponden a B02; la regresión funcional completa queda para B03.

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
Los informes 01/02 permanecen históricos; B02 y la verificación final siguen pendientes.
