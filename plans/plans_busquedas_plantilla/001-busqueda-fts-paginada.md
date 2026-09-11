# Plan backend 001 — Búsqueda PostgreSQL FTS paginada de plantillas APU

**Estado:** TODO · **Prioridad:** P0 · **Puede ejecutarse en paralelo con:** 002 y 003

## 01. Resultado observable

`GET /api/v1/plantillas-apu/busqueda` devuelve una página estable de plantillas visibles. Con `q` usa FTS PostgreSQL con ranking; sin `q` devuelve resultados iniciales sin cargar el catálogo completo.

## 02. Estado inicial verificable

- `PlantillaApuService.listar` carga SISTEMA + PERSONAL propias y filtra con `String.contains`.
- `PlantillaApuRepository` solo tiene LIKE paginado para el recurso admin.
- `plantilla_apu` carece de `tsvector` e índice GIN.
- PostgreSQL local es 18-alpine; el contenedor vigente anuncia `unaccent 1.1` y `pg_trgm 1.6` en `pg_available_extensions`, pero ninguna está instalada ni declarada por migración. Repetir esa consulta en CI antes de aplicar V011.

## 03. Contrato

```http
GET /api/v1/plantillas-apu/busqueda
    ?q=hormigon
    &tipo=SISTEMA
    &tipo=PERSONAL
    &page=0
    &size=20
```

- `tipo` repetible; omitido = ambas fuentes. Un valor fuera del enum → 400 `validacion`.
- `page` default 0; `size` default 20, rango 1..50.
- Response: `Page<PlantillaApuResumenResponse>` común (`items`, `total`, `page`, `size`).
- `q` vacío/blanco: SISTEMA primero, luego PERSONAL propia; `nombre`, `id` como desempate.
- `q` no vacío: `ts_rank_cd` descendente; luego tipo, nombre e id.
- Mantener `GET /plantillas-apu` legado sin cambiar su shape y `GET /plantillas-apu/{id}` sin cambios.

## 04. Migración V011 reservada

Crear `V011__plantilla_apu_busqueda_fts.sql`; STOP si V011 ya existe al ejecutar.

1. `CREATE EXTENSION IF NOT EXISTS unaccent`.
2. Crear configuración `public.spanish_unaccent` copiando `pg_catalog.spanish`.
3. Alterar mappings de palabras para aplicar `unaccent, spanish_stem`.
4. Añadir columna almacenada `busqueda_fts tsvector GENERATED ALWAYS AS (...) STORED`:
   - `nombre` con peso `A`;
   - `descripcion_rubro` con peso `B`;
   - `coalesce` para null;
   - configuración explícita `public.spanish_unaccent` tanto al indexar como consultar.
5. Crear `GIN (busqueda_fts)`.
6. No añadir índices B-tree especulativos: `ix_plantilla_usuario` permanece y cualquier optimización adicional requiere evidencia en un plan posterior.

Referencias técnicas oficiales:

- [PostgreSQL — Tables and Indexes for Text Search](https://www.postgresql.org/docs/17/textsearch-tables.html)
- [PostgreSQL — Preferred Index Types for Text Search](https://www.postgresql.org/docs/17/textsearch-indexes.html)
- [PostgreSQL — Controlling Text Search](https://www.postgresql.org/docs/17/textsearch-controls.html)
- [PostgreSQL — `unaccent`](https://www.postgresql.org/docs/17/unaccent.html)

Usar la misma configuración al indexar y consultar; verificar tokenización española con `ts_debug` antes de fijar expectativas de ranking.

## 05. Implementación

- Añadir método repository con SQL nativo parametrizado; nunca concatenar `q`.
- Owner-scope en la misma consulta: `tipo='SISTEMA' OR (tipo='PERSONAL' AND usuario_id=:caller)`.
- Filtro por tipos permitido antes de FTS.
- Query: `busqueda_fts @@ websearch_to_tsquery('public.spanish_unaccent', :q)`.
- Count separado con exactamente los mismos filtros.
- Método `@GET @Path("/busqueda")` directamente en `PlantillaApuResource`, antes del método `/{id}`; una IT debe demostrar que `busqueda` no llega a `UuidV7.parse`.
- No devolver score en el DTO: el ranking es una decisión de consulta, no contrato público.

## 06. RED crítico

- `hormigon` encuentra `Hormigón`; `nivelacion` encuentra `nivelación`, demostrando diccionario español + `unaccent` y no solo lowercase.
- Términos completos en distinto orden conservan relevancia estable.
- Un prefijo incompleto como `hormig` no promete match: este plan implementa FTS, no tolerancia a errores tipográficos con `pg_trgm`.
- PERSONAL ajena no aparece ni altera `total`.
- SISTEMA, PERSONAL y ambas fuentes funcionan con `tipo` repetido.
- Página vacía y límites 1/50/51.
- `q` vacío no ejecuta tsquery inválida y mantiene orden estable.
- `EXPLAIN (ANALYZE, BUFFERS)` del caso representativo usa el índice GIN con volumen suficiente de fixture de test; no exigirlo sobre dos filas.

## 07. Archivos candidatos

- `src/main/resources/db/migration/V011__plantilla_apu_busqueda_fts.sql`
- `src/main/java/ec/uce/propuestas/plantilla/repository/PlantillaApuRepository.java`
- `src/main/java/ec/uce/propuestas/plantilla/service/PlantillaApuService.java`
- `src/main/java/ec/uce/propuestas/plantilla/resource/PlantillaApuResource.java`
- tests bajo `src/test/java/ec/uce/propuestas/plantilla/`

No editar V001–V010, admin salvo reutilización explícita posterior, seeds V004 ni DTOs de detalle.

## 08. Verificación

```bash
./gradlew test --tests 'ec.uce.propuestas.plantilla.*Search*'
./gradlew test --tests 'ec.uce.propuestas.plantilla.*'
./gradlew spotlessCheck
./gradlew build -x test
git diff --check
graphify update .
```

## 09. STOP y rollback

STOP si `SELECT * FROM pg_available_extensions WHERE name='unaccent'` no devuelve fila en cualquier imagen soportada, si el rol de migración no puede crearla, si la expresión generada no es inmutable con configuración fija, o si JAX-RS no diferencia `/busqueda` de `/{id}`. Rollback: eliminar solo V011 y los métodos/resource/tests nuevos; el listado legado debe seguir funcionando.
