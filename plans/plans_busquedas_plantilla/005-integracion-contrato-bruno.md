# Plan backend 005 — Integración, contrato canónico y Bruno

**Estado:** DONE (2026-09-11) · **Prioridad:** P1 · **Depende de:** backend 001–004

## 01. Resultado observable

Los cambios de búsqueda, catálogo, lote y alta manual funcionan juntos sobre PostgreSQL con V001–V012, mantienen compatibilidad con el listado/detalle legado y tienen journeys reproducibles en las colecciones Bruno existentes. No se añadió una tercera variante de búsqueda o creación.

## 02. Reconciliación obligatoria — COMPLETADA

Se actualizaron los documentos canónicos de `../thesis-docs` antes del cierre:

- `plan/architecture/07-api-contract.md`: rutas, `Page<T>`, parámetros `tipo` repetibles, DTOs, códigos HTTP, owner-scope, lote atómico y alta manual strict.
- `plan/design/03-procesos-detalle.md`: cierre funcional de P-20, P-26 y P-29, destino de última hoja, cantidad server-authored y rollback identificado.
- `plan/quality/02-catalogo-pruebas.md`: TC-P20-04/05, TC-P26-10/11 y TC-P29-04/05/06.

Las rutas legacy `/plantillas-apu` y `POST /presupuestos/{id}/apus` permanecen intactas y documentadas como compatibles.

## 03. Bruno — COMPLETADO

Se extendieron `api/bruno/09-i02-i06/` y `api/bruno/10-presupuesto/` sin crear colecciones paralelas:

- FTS: página vacía, búsqueda sin tilde, ranking nombre/descripción, filtros repetibles y detalle.
- Preparación de lote sobre un presupuesto propio sin cronograma V004, capítulo raíz/hoja y destino implícito.
- Lote simple/mixto, advertencias no bloqueantes, rollback en segundo elemento, validación de duplicados y owner-to-404.
- Alta manual completa con insumo derivado de la respuesta, HM/fila/rubro server-authored, strict JSON y owner-to-404.
- Se añadieron directivas `body: json` que faltaban en requests POST/PUT/PATCH de la colección I-06; las aserciones de variables capturadas en el mismo request ahora usan `res.body`, conforme a Bruno CLI 4.1.

Evidencia medida contra PostgreSQL 18 limpio + fast-jar, `bru` 4.1.0:

- Plan 005 en `09-i02-i06`: **19/19 requests**, **41/41 tests**.
- `10-presupuesto` completo: **27/27 requests**, **91/91 tests**.

La colección histórica completa de I-06 conserva casos previos que requieren credenciales administrativas externas y expectativas antiguas ajenas a este plan; la evidencia anterior aísla los journeys nuevos y sus helpers requeridos.

## 04. Integración y rendimiento — COMPLETADO

- PostgreSQL 18 temporal `p05_verification` con Flyway hasta V012; no se modificó ninguna migración aplicada.
- FTS medido con `EXPLAIN (ANALYZE, BUFFERS)` sobre 100.000 filas temporales dentro de una transacción revertida. Resultado: `Bitmap Index Scan on ix_plantilla_apu_busqueda_fts`, 10.002 filas candidatas, **281.156 ms** de ejecución (`Planning 0.903 ms`), sin filtro FTS en memoria.
- Lote de 20 medido con 20 plantillas SISTEMA y un presupuesto/capítulo nuevo: **HTTP 201**, 20 resultados, **2.516876 s** reportados por curl (`2.537 s` wall). La implementación ejecuta una sola `RecalculoService.recalcular(Alcance.Version)` después del bucle; no hay N consolidaciones completas.
- El lote mixto con advertencia y el lote fallido en segundo elemento verificaron, por IT y Bruno, advertencias no bloqueantes y rollback de APUs/rubros/auditoría.
- La operación manual y el lote conservan la frontera transaccional; `apu.creado` se emite dentro de ella.

## 05. Verificación

Comandos ejecutados:

```bash
./gradlew test --tests 'ec.uce.propuestas.presupuesto.resource.PlantillaLoteResourceIT' \
  --tests 'ec.uce.propuestas.plantilla.resource.PlantillaApuResourceIT' --console=plain
./gradlew test --console=plain
./gradlew spotlessCheck --console=plain
./gradlew build -x test --console=plain
git diff --check
graphify update .
```

Resultados:

- Focal: `PlantillaLoteResourceIT` **7/7** y `PlantillaApuResourceIT` **14/14**.
- Suite completa: **763 tests = 760 pass + 2 fallos aceptados (GM-19/GM-20) + 1 skipped (GM-24), 0 errors**. Los dos fallos son los residuales IESS documentados y no pertenecen a Plan 005.
- `spotlessCheck`: PASS.
- `build -x test`: PASS.
- `git diff --check`: PASS.
- `graphify update .`: completado (**4.821 nodos / 15.164 aristas / 199 comunidades**).

## 06. Done criteria

- Contrato canónico y recursos coinciden en rutas, query params, DTOs y owner-scope.
- Los journeys nuevos de Bruno tienen conteos medidos y pasan.
- El FTS usa la columna `tsvector`/índice GIN de PostgreSQL; no hay filtro nuevo en memoria.
- No hay éxito parcial, y el lote ejecuta una sola recalculación final.
- V001–V010 permanecen intactas; V011/V012 son las migraciones aditivas de los planes anteriores.
- El frontend puede iniciar su Plan 001 sin adivinar ninguna forma.

## 07. Rollback

La reversión funcional puede retirar endpoints nuevos manteniendo el detalle/listado legado. Las migraciones Flyway aplicadas no se borran en entornos existentes: una reversión posterior debe dejar columna/configuración sin consumidores o usar una migración compensatoria aprobada.
