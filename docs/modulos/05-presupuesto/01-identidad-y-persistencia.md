# Plan 019 — Identidad pública UUIDv7 y persistencia base del módulo `presupuesto`

> **Plan 019** del módulo [`05-presupuesto`](00.md). Estado tras re-evaluación,
> implementación y verificación independiente: **DONE (2026-08-31)**.
> Ver §«Estado de cierre» para RED/GREEN y conteos reales. La suite completa y
> Bruno permanecen reservados para el cierre del módulo; la regresión adyacente,
> Spotless, build, diff y actualización Graphify de este plan ya están cerrados.

---

## Estado de cierre (post-implementación)

**Veredicto de re-evaluación:** `NEEDS ADJUSTMENT` (resuelto antes de
implementar, ver §«Ajustes por re-evaluación» abajo).

**Implementación aplicada (2026-08-31):**

- **`V008__capitulo_rubro_public_id.sql`** — ALTER TABLE estructural de
  `capitulo` y `rubro` añadiendo `public_id UUID NOT NULL UNIQUE DEFAULT
  uuidv7()` + dos triggers `trg_public_id_immutable` reusando
  `fn_assert_public_id_immutable()` declarada en V001 §5 (sin funciones
  propias por tabla). Backfill automático vía DEFAULT para todas las filas
  existentes que V001–V007 dejaron en `capitulo` y `rubro` (incluidas las
  del seed V004) — el DEFAULT se evalúa por fila durante el ALTER TABLE y
  el recorrido **no** es O(1).
- **`Capitulo.java`** + **`Rubro.java`** — entidades JPA con patrón
  WU-03 (PanacheEntityBase + IDENTITY + `@Generated(INSERT) publicId`
  `insertable=false/updatable=false`). **Sin** `createdAt`/`updatedAt`
  ni callbacks — el DDL canónico no los declara. BigDecimal
  precision/scale exactos al DDL (`total NUMERIC(14,6)` en capitulo,
  `cantidad NUMERIC(12,6)` + dos `NUMERIC(14,6)` en rubro).
- **`CapituloRepository.java`** + **`RubroRepository.java`** — sólo
  `findByPublicIdAndOwnerScope(UUID, Long)` con traversal
  `Capitulo → Presupuesto → Proyecto → caller` y
  `Rubro → Capitulo → Presupuesto → Proyecto → caller` respectivamente.
  Sin métodos de listado/subárbol (deferidos a 022/023, sin consumidor en 019).
- **`SchemaBaselineIT`** — **preservado** byte-for-byte desde `main@HEAD`
  (perfil `V001OnlyProfile`, `MigrationVersion.fromVersion("1")`,
  `PUBLIC_TABLES`/`INTERNAL_TABLES` originales con `capitulo`/`rubro` en
  `INTERNAL_TABLES`). Su contrato es **deliberadamente V001-only** — V001
  no declara `public_id` en `capitulo`/`rubro` y la regresión de
  ampliarlo in-place a «latest» rompía su intención.
- **`V008SchemaIT`** (nuevo, `src/test/java/ec/uce/propuestas/schema/`)
  — IT enfocado a las invariantes de V008 sobre el esquema «latest»: (i)
  `flyway_schema_history` registra `008` aplicado con éxito; (ii)
  `capitulo.public_id` y `rubro.public_id` existen, son `UUID`, `NOT
  NULL`, con DEFAULT que incluye `uuidv7()` y UNIQUE; (iii) ambas tablas
  tienen trigger `trg_public_id_immutable` ejecutando la función genérica
  compartida de V001 §5; (iv) **no** se introdujeron funciones
  `capitulo_*public_id*` ni `rubro_*public_id*`; (v) filas pre-sembradas
  o recién insertadas tienen `public_id` no-nulo y formato UUIDv7
  (sin depender del orden de las clases de test).
- **`PublicIdPersistenceTest`** — extendido con `Capitulo`/`Rubro`
  (registro en `PUBLIC_ID_ENTITIES`, aserciones FK BIGINT, aserciones
  `findByPublicIdAndOwnerScope`, fixtures `persistCapitulo`/`persistRubro`,
  test de generación UUIDv7, test de `Optional.empty()` ante UUIDv7
  inexistente).

**Evidencia RED/GREEN observada** (foco estricto en TDD del plan,
medida en `build/test-results/test/TEST-*.xml`, sin cifras presupuestas):

| Etapa | Comando | Resultado |
|---|---|---|
| RED-1 | aserciones de V008 ejecutadas temporalmente antes de crear la migración | **7 tests, 3 fallos esperados**: faltaban `capitulo.public_id`, `rubro.public_id` y sus triggers. Durante REFACTOR se restauró `SchemaBaselineIT` V001-only y esas aserciones pasaron al nuevo `V008SchemaIT`; no se atribuye un RED separado ficticio a la clase final. |
| GREEN-1 | mismo comando tras `V008__capitulo_rubro_public_id.sql` | **5/5 verde**, 0 failures, 0 errors (flyway_history_records_successful_v008, capitulo_and_rubro_public_id_is_uuid_not_null_unique_with_uuidv7_default, capitulo_and_rubro_have_trigger_using_shared_function_from_v001, no_bespoke_capitulo_or_rubro_public_id_function_was_introduced, public_id_is_populated_with_uuidv7_for_existing_or_inserted_rows) |
| RED-2 | `./gradlew test --tests 'ec.uce.propuestas.identifier.PublicIdPersistenceTest' -Dquarkus.http.test-port=0` (tras extender el test) | **errores de compilación** (`cannot find symbol class Capitulo/Rubro/CapituloRepository/RubroRepository`) — esperado antes de los fuentes |
| GREEN-2 | mismo comando tras añadir los 4 archivos Java | **12/12 verde**, 0 failures, 0 errors (los 10 originales + `capitulo_and_rubro_are_generated_as_uuidv7_after_persist` + `owner_scope_returns_empty_for_nonexistent_capitulo_and_rubro_publicIds`) |
| Regresión | `./gradlew test --tests 'ec.uce.propuestas.schema.SchemaBaselineIT' -Dquarkus.http.test-port=0` (intacto desde `main@HEAD`) | **6/6 verde**, 0 failures, 0 errors — el contrato V001-only se preserva; `capitulo`/`rubro` siguen en `INTERNAL_TABLES` porque V001 no declara `public_id` |

**Verificación independiente cerrada:**

- Contratos focalizados: **23/23 verdes** (`SchemaBaselineIT` 6,
  `V008SchemaIT` 5, `PublicIdPersistenceTest` 12).
- Regresión APU: **41/41 verde**; el selector `presupuesto.*` no encontró
  suites adicionales, porque Plan 019 concentra sus contratos en
  `schema/` e `identifier/`.
- `./gradlew spotlessCheck` y `./gradlew build -x test`: **BUILD SUCCESSFUL**.
- `git diff --check`: limpio. `graphify update .`: ejecutado; el indexado SQL
  se omitió por ausencia local de `tree_sitter_sql`, sin afectar Java/AST.
- Suite completa y Bruno continúan reservados para Plan 025 (cierre I-07).

## Ajustes por re-evaluación (`NEEDS ADJUSTMENT`)

> La inspección directa del código y del schema antes de implementar
> reveló desviaciones entre el plan original y el estado real. Se aplican
> **antes** de tocar código, dejando este plan como baseline revisado.
> La primera pasada listaba cuatro ajustes; la inspección de los XML de
> test después de esa primera pasada añadió un quinto ajuste obligatorio
> (ver §5 abajo) — preserva el contrato de `SchemaBaselineIT` y separa la
> verificación V008 en un IT enfocado `V008SchemaIT` (no es redundante:
> cubre aserciones de V008 que `SchemaBaselineIT` no debe y no puede
> cubrir por contrato, ver §5).

1. **Sin ITs de repositorio redundantes.** `CapituloRepositoryIT` y
   `RubroRepositoryIT` duplicaban el registro transversal de
   `PublicIdPersistenceTest`, que se amplía in-place con ambas entidades y
   repositorios. El test migratorio genérico propuesto se reemplaza por
   `V008SchemaIT`: un contrato enfocado al esquema latest que no altera ni
   duplica el propósito V001-only de `SchemaBaselineIT`.

2. **Una sola función de trigger compartida.** El plan original proponía
   `capitulo_public_id_inmutable()` y `rubro_public_id_inmutable()`
   (funciones por tabla). V001 §5 ya declara
   `fn_assert_public_id_immutable()` reutilizada por 10 tablas públicas.
   V008 sólo crea dos `CREATE TRIGGER` reusando esa misma función — sin
   funciones nuevas por tabla. El patrón vigente (compartido por
   `presupuesto`, `apu`, etc.) se respeta.

3. **Sin métodos de repositorio no consumidos.** El plan original listaba
   `listarRaices`, `listarHijos`, `listarDeCapitulo` (estos últimos con
   `order by orden` inexistente en DDL). Los repositorios sólo exponen
   `findByPublicIdAndOwnerScope(UUID, Long)` en 019. Los listados /
   subárboles / moved-atomic / ciclos se difieren a los planes 022 y 023
   que son sus consumidores reales, sin pre-asignar firmas no usadas.

4. **Entidades JPA sin `createdAt` / `updatedAt` ni callbacks.** V001
   §2.9 (`capitulo`) y §2.11 (`rubro`) no declaran esas columnas; el
   plan original incluía `@PrePersist`/`@PreUpdate`/`@Column createdAt`
   imitando el patrón de `Presupuesto`. Las entidades resultantes sólo
   declaran las columnas que existen en el DDL — la inspección de V001
   antes de implementar lo confirmó. Cualquier write-through de totales
   en planes futuros no se sirve desde la entidad sino desde
   `recalculo` (Plan 020).

5. **`SchemaBaselineIT` preservado byte-for-byte; nuevo `V008SchemaIT`
   enfocado al esquema «latest».** La primera pasada convirtió
   `SchemaBaselineIT` de su contrato V001-only (`V001OnlyProfile`,
   `target = MigrationVersion.fromVersion("1")`, `PUBLIC_TABLES`/`INTERNAL_TABLES`
   originales con `capitulo`/`rubro` en `INTERNAL_TABLES`) a latest + un
   test de UUIDv7 sembrado, lo cual **rompió la intención del test**:
   `SchemaBaselineIT` existe para bloquear el baseline estructural V001,
   y V001 deliberadamente **no** declara `public_id` en `capitulo`/`rubro`.
   Ampliarlo in-place mezcla dos contratos incompatibles y debilita la
   garantía de regresión estructural. La corrección preserva
   `SchemaBaselineIT` byte-for-byte desde `main@HEAD` (sigue
   bloqueando V001 con `6/6 verde`) y crea un nuevo
   `src/test/java/ec/uce/propuestas/schema/V008SchemaIT.java` enfocado
   sólo a las invariantes de V008 sobre el esquema latest (5/5 verde).
   No es redundante: cubre aserciones de V008 que `SchemaBaselineIT` no
   debe y no puede cubrir por contrato (DEFAULT `uuidv7()`, UNIQUE
   específico por tabla, flyway history con `008` aplicado,
   ausencia de funciones por tabla, UUIDv7 en filas existentes).

## Resultado esperado

El módulo `presupuesto` tiene las entidades JPA `Capitulo` y `Rubro`
mapeadas a las tablas canónicas de V001 con la identidad externa inmutable
`public_id UUID DEFAULT uuidv7()`. Cada tabla gana el mismo trigger de
inmutabilidad de `public_id` que ya tienen `presupuesto`, `apu`,
`plantilla_apu`, `plantilla_proyecto`, `base_insumos` e `insumo`. Los
repositorios exponen `findByPublicIdAndOwnerScope` con traversal
`Capitulo → Presupuesto → Proyecto → caller` y `Rubro → Capitulo →
Presupuesto → Proyecto → caller`. El módulo queda listo para que los
planes 020–025 implementen la lógica de negocio encima de esta capa de
persistencia.

## Dependencias

- **No tiene** dependencias internas al módulo `presupuesto` (019 es el
  primero del DAG). Sí depende de los patrones ya vivos:
  - WU-03 (`public_id` UUIDv7) implementado en `Presupuesto.java`,
    `Apu.java`, `PlantillaApu.java`, `PlantillaProyecto.java`,
    `BaseInsumos.java`, `Insumo.java` (revisar antes de duplicar la
    convención).
  - `UuidV7.parse(...)` canónico de frontera (Plan 07,
    `ec.uce.propuestas.common`).
  - Flyway en `src/main/resources/db/migration/` con numeración `V00N`.

## Estado de cierre

**DONE · VERIFICACIÓN DIRIGIDA COMPLETA (2026-08-31).** Re-evaluación
cerrada con veredicto `NEEDS ADJUSTMENT`; ajustes aplicados antes de
implementar (ver §«Ajustes por re-evaluación» arriba). El verificador
independiente confirmó contratos focalizados 23/23, regresión APU 41/41,
Spotless y build verdes, diff limpio y actualización Graphify. La suite
completa y Bruno se reservan para Plan 025 según el DAG del módulo.

**Resultado observado de los focused tests** (TDD del plan,
medido en `build/test-results/test/TEST-*.xml`):

| Test | Resultado |
|---|---|
| `ec.uce.propuestas.schema.SchemaBaselineIT` | **6/6 verde** — preservado byte-for-byte desde `main@HEAD`; contrato V001-only intacto (V001 no declara `public_id` en `capitulo`/`rubro`) |
| `ec.uce.propuestas.schema.V008SchemaIT` (nuevo) | **5/5 verde** — invariantes V008 sobre el esquema latest: flyway `008`, columnas + UNIQUE + DEFAULT `uuidv7()`, trigger genérico compartido, no-functions-by-table, UUIDv7 no-nulo en filas existentes o insertadas |
| `ec.uce.propuestas.identifier.PublicIdPersistenceTest` | **12/12 verde** (10 originales + `capitulo_and_rubro_are_generated_as_uuidv7_after_persist` + `owner_scope_returns_empty_for_nonexistent_capitulo_and_rubro_publicIds`) |
| Regresión `ec.uce.propuestas.apu.*` | **41/41 verde**, 0 failures/errors/skipped |
| `./gradlew spotlessCheck` | BUILD SUCCESSFUL |
| `./gradlew build -x test` | BUILD SUCCESSFUL |
| `git diff --check` | sin salida (limpio) |
| `graphify update .` | completado; indexado SQL omitido por ausencia local de `tree_sitter_sql` |

---

## Contexto / estado actual

### Lo que ya existe (DONE en planes previos, NO se reabre)

1. **Tablas V001 §2.8 (presupuesto), §2.9 (capitulo), §2.11 (rubro).**
   Las tres existen físicamente desde el baseline; los DDL están en
   `thesis-docs/plan/architecture/06-database-schema.md` §2.8–§2.11.
2. **Entidad JPA `Presupuesto`** (módulo `presupuesto`, ya implementada):
   - PK `BIGINT IDENTITY`, `publicId UUID` con
     `@Generated(event = EventType.INSERT) + insertable=false,
     updatable=false`.
   - `proyectoId BIGINT NOT NULL`, `version SHORT NOT NULL`,
     `esVigente boolean NOT NULL`, `origenId BIGINT` (nullable),
     `notas`, `porcentajeIndirecto NUMERIC(5,4)`, `total NUMERIC(14,6)
     NOT NULL DEFAULT 0`, `createdAt`/`updatedAt`.
   - Camino: `src/main/java/ec/uce/propuestas/presupuesto/entity/Presupuesto.java`.
3. **Repositorio `PresupuestoRepository`** (módulo `presupuesto`):
   - `findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId)`
     con traversal `Presupuesto → Proyecto → caller`.
   - `findByPublicIdAndProyecto(Long proyectoId, UUID publicId,
     Long callerUsuarioId)`.
   - `findVigenteDeProyecto(Long proyectoId)`.
   - Camino: `src/main/java/ec/uce/propuestas/presupuesto/repository/PresupuestoRepository.java`.
4. **Patrón WU-03** replicado en `Apu`,
   `PlantillaApu`, `PlantillaProyecto`, `BaseInsumos` e `Insumo`. La
   columna `public_id UUID NOT NULL DEFAULT uuidv7()` con trigger de
   inmutabilidad está documentada en `06-database-schema.md` §1.
5. **`UuidV7.parse(...)`** canónico en
   `ec.uce.propuestas.common` (Plan 07, 2026-08-30). El
   parser valida `UUID.version() == 7 && variant == 2` y la regex
   canónica.
6. **Flyway** configurado; la última migración aplicada antes de 019 es V007
   (`V007__rubro_cantidad_zero_pending_allowed.sql`, estructural). Las
   migraciones se numeran con tres dígitos sin huecos.

### Lo que falta (este plan lo construye)

1. **Entidades JPA `Capitulo` y `Rubro`** con la columna `publicId`
   UUIDv7, idéntica convención al `Presupuesto`/`Apu` existentes, sin
   `createdAt`/`updatedAt` ni callbacks (V001 §2.9/§2.11 no los declaran).
2. **Repositorios `CapituloRepository` y `RubroRepository`** con
   `findByPublicIdAndOwnerScope` traversal propio (Capitulo→
   Presupuesto→Proyecto; Rubro→Capitulo→Presupuesto→Proyecto). Sin
   listados/subárboles — diferidos a planes 022/023.
3. **Migración V008 estructural** que añade `public_id UUID NOT NULL
   UNIQUE DEFAULT uuidv7()` a `capitulo` y `rubro` (NO a `cronograma`
   ni a `actividad` — eso es una migración futura de I-08, numeración
   por determinar). La migración crea además el trigger
   `trg_public_id_immutable` para cada tabla reusando
   `fn_assert_public_id_immutable()` ya declarada en V001 §5 — sin
   funciones nuevas por tabla.
4. **`SchemaBaselineIT`** preservado byte-for-byte desde `main@HEAD`
   (perfil `V001OnlyProfile`, migración hasta V001, `PUBLIC_TABLES`/
   `INTERNAL_TABLES` originales con `capitulo`/`rubro` en
   `INTERNAL_TABLES`). Su contrato sigue siendo bloquear el baseline
   estructural V001; **no** se amplía con aserciones de V008 porque
   V001 no declara `public_id` en esas dos tablas.
5. **`V008SchemaIT`** (nuevo, `src/test/java/ec/uce/propuestas/schema/`)
   — IT enfocado a las invariantes de V008 sobre el esquema latest:
   flyway registra `008` aplicado, columnas + UNIQUE + DEFAULT
   `uuidv7()` en `capitulo`/`rubro`, trigger genérico compartido, no
   funciones por tabla, UUIDv7 no-nulo en filas existentes o
   recién insertadas (sin depender del orden de las clases de test).
6. **`PublicIdPersistenceTest`** ampliado in-place para registrar las
   dos entidades (aserciones de campo, FK BIGINT, repository owner
   scope, fixtures, test de generación UUIDv7, test de UUIDv7
   inexistente → `Optional.empty()`).
7. **No** se introducen otros `*IT.java` redundantes ni
   DTOs/servicios/resources.

### Decisiones de diseño locked (no se reabren)

- **`Capitulo.item` autogenerado** (`"1"`, `"1.1"`, `"1.1.1"` …).
  Patrón numérico sin tope (DM §4, profundidad libre — el workbook IESS
  tiene profundidad 3 en capítulos y 4 en rubros).
- **`UNIQUE (presupuesto_id, item)` en `capitulo`** (V001 §4, invariante
  de aplicación: numeración jerárquica sin duplicados dentro de la
  versión).
- **`UNIQUE (presupuesto_id, codigo)` en `apu`** (D-09, vigente desde
  I-05) — implica que `Rubro.codigo` espejo de `apu.codigo` es único
  por versión sin desnormalizar.
- **`Rubro.apu_id UNIQUE`** (D-09, vínculo APU↔rubro 1:1 por versión).
- **`rubro.cantidad >= 0`** (V007 estructural). La API P-29 sigue
  exigiendo `cantidad > 0` vía los DTOs.
- **No-links entre APUs** (N04 §2 / v1.3 §2.5.6) — no existe FK entre
  APUs. `Rubro` sólo referencia `APU` (1:1).

---

## Alcance

### Incluye

- Crear `Capitulo.java` en
  `src/main/java/ec/uce/propuestas/presupuesto/entity/`.
- Crear `Rubro.java` en
  `src/main/java/ec/uce/propuestas/presupuesto/entity/`.
- Crear `CapituloRepository.java` en
  `src/main/java/ec/uce/propuestas/presupuesto/repository/`.
- Crear `RubroRepository.java` en
  `src/main/java/ec/uce/propuestas/presupuesto/repository/`.
- Crear `V008__capitulo_rubro_public_id.sql` en
  `src/main/resources/db/migration/`.
- Tests de persistencia + owner-scope en
  `src/test/java/ec/uce/propuestas/identifier/PublicIdPersistenceTest.java`.
- Contrato V008 del esquema actual en
  `src/test/java/ec/uce/propuestas/schema/V008SchemaIT.java`, preservando
  `SchemaBaselineIT` como contrato V001-only.

### No incluye (alcance cerrado)

- **No** se crea lógica de negocio (totales, mover, deep copy). Eso
  vive en los planes 020–024.
- **No** se reabre V001–V007.
- **No** se añade `public_id` a `cronograma` ni a `actividad` (queda
  pendiente para una migración futura de I-08, numeración por
  determinar; no se pre-asigna V009). El P-32 sólo **lee** `actividad`
  para detectar ítems sin actividad (consulta, no escritura).
- **No** se introduce Lombok, MapStruct, ni nuevos frameworks.
- **No** se crea `RestResponse<T>` ni se migran responses existentes.
- **No** se modifica la entidad `Presupuesto` (ya implementada con
  WU-03 en I-05).
- **No** se introducen repositorios para `cronograma` ni `actividad`
  en este plan (la migración futura los cubre con su propio UUIDv7).

---

## Contrato esperado (capa de persistencia)

```text
# Migración nueva
src/main/resources/db/migration/V008__capitulo_rubro_public_id.sql

# Entidades JPA nuevas
src/main/java/ec/uce/propuestas/presupuesto/entity/Capitulo.java
src/main/java/ec/uce/propuestas/presupuesto/entity/Rubro.java

# Repositorios nuevos
src/main/java/ec/uce/propuestas/presupuesto/repository/CapituloRepository.java
src/main/java/ec/uce/propuestas/presupuesto/repository/RubroRepository.java

# Suites invariantes (SchemaBaselineIT preservado desde main@HEAD;
# V008SchemaIT es el IT enfocado al esquema «latest»; PublicIdPersistenceTest
# ampliado in-place con las dos entidades — NO se introducen *IT redundantes
# en presupuesto.repository ni tests de migración duplicados)
src/test/java/ec/uce/propuestas/schema/SchemaBaselineIT.java
src/test/java/ec/uce/propuestas/schema/V008SchemaIT.java
src/test/java/ec/uce/propuestas/identifier/PublicIdPersistenceTest.java
```

### Columnas añadidas en V008

```sql
ALTER TABLE capitulo
    ADD COLUMN public_id UUID NOT NULL UNIQUE DEFAULT uuidv7();

ALTER TABLE rubro
    ADD COLUMN public_id UUID NOT NULL UNIQUE DEFAULT uuidv7();

CREATE TRIGGER trg_public_id_immutable
    BEFORE UPDATE OF public_id ON capitulo
    FOR EACH ROW EXECUTE FUNCTION fn_assert_public_id_immutable();

CREATE TRIGGER trg_public_id_immutable
    BEFORE UPDATE OF public_id ON rubro
    FOR EACH ROW EXECUTE FUNCTION fn_assert_public_id_immutable();
```

> **Patrón V001 §1/§5**: la regla «BIGINT PK + `public_id` UUIDv7 =
> invariante híbrido» se preserva exactamente. V001 §5 ya declara la
> función compartida `fn_assert_public_id_immutable()` y los triggers
> `trg_public_id_immutable` para `usuario`, `firmante`, `proyecto`,
> `presupuesto`, `apu`, `apu_detalle`, `base_insumos`, `insumo`,
> `plantilla_apu` y `plantilla_proyecto`. V008 sólo añade dos triggers
> más reutilizando la misma función — sin funciones por tabla. **No** se
> reabre ninguna migración previa.
>
> **Aviso de escala:** el DEFAULT `uuidv7()` se evalúa por fila durante
> el ALTER TABLE, así que las filas existentes en `capitulo`/`rubro`
> (incluidas las del seed V004) reciben UUIDv7 sin lógica adicional.
> No es O(1) ni lógicamente inmutable; es un DEFAULT volátil válido para
> el volumen actual.

### Mapping JPA (idéntico patrón WU-03)

```java
@Entity
@Table(name = "capitulo")
public class Capitulo extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** WU-03 — Identidad externa inmutable UUIDv7. */
    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false)
    public UUID publicId;

    @Column(name = "presupuesto_id", nullable = false)
    public Long presupuestoId;

    @Column(name = "parent_id")
    public Long parentId;

    @Column(nullable = false, length = 20)
    public String item;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String descripcion;

    @Column(nullable = false)
    public Short orden;

    @Column(nullable = false, precision = 14, scale = 6)
    public BigDecimal total = BigDecimal.ZERO;
}
```

`Rubro.java` análogo con `capituloId`, `apuId`, `item`, `codigo`,
`descripcion`, `unidad`, `cantidad`, `precioUnitario`, `precioTotal`.
**No** se declaran `createdAt`/`updatedAt` ni callbacks de ciclo de vida
— V001 §2.9/§2.11 no declaran esas columnas.

### Métodos de repositorio (mínimo viable — sólo lo usado en 019)

```java
@ApplicationScoped
public class CapituloRepository implements PanacheRepositoryBase<Capitulo, Long> {

    /** WU-03 — Resolución por public_id (UUIDv7) con scope de owner. */
    public Optional<Capitulo> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return getEntityManager()
                .createQuery(
                    "select c from Capitulo c, Presupuesto p, Proyecto pr "
                  + "where c.publicId = :publicId "
                  + "and c.presupuestoId = p.id "
                  + "and p.proyectoId = pr.id "
                  + "and pr.usuarioId = :caller",
                    Capitulo.class)
                .setParameter("publicId", publicId)
                .setParameter("caller", callerUsuarioId)
                .getResultList().stream().findFirst();
    }
}
```

`RubroRepository.findByPublicIdAndOwnerScope` con traversal
`Rubro → Capitulo → Presupuesto → Proyecto → caller`. Los listados
(`listarRaices`, `listarHijos`, `listarDeCapitulo` con `order by
orden`) se difieren a los planes 022/023, sus consumidores reales —
no se pre-asignan firmas no usadas en 019.

---

## Pasos (ejecutados 2026-08-31)

1. **Auditar el patrón WU-03** leyendo `Presupuesto.java`,
   `Apu.java`, `BaseInsumos.java` (módulo `insumo`),
   `PlantillaApu.java`, `PlantillaProyecto.java` y `Firmante.java`.
   Confirmar la convención exacta: `@Generated(event = EventType.INSERT)
   + insertable=false, updatable=false + @Column(name="public_id")`.
   Sin variantes.
2. **Auditar `UuidV7.parse`** en `ec.uce.propuestas.common` (Plan 07).
   Firma canónica `UUID.version() == 7 && variant == 2` + regex
   canónica. Reusar, no duplicar.
3. **RED-1 — `V008SchemaIT`** (nuevo,
   `src/test/java/ec/uce/propuestas/schema/V008SchemaIT.java`):
   IT enfocado a las cinco invariantes de V008 sobre el esquema
   latest. Antes de crear V008, las aserciones de `public_id` UUID +
   NOT NULL + UNIQUE + DEFAULT `uuidv7()` fallan en `capitulo`/`rubro`
   (las columnas aún no existen). Se ejecuta el IT y se captura el
   fallo esperado.
4. **GREEN-1 — `V008__capitulo_rubro_public_id.sql`**: ALTER TABLE
   estructural de `capitulo` y `rubro` + dos `CREATE TRIGGER`
   reusando `fn_assert_public_id_immutable()`. Se re-ejecuta el
   `V008SchemaIT` y se verifica **5/5 verde** sin debilitar
   aserciones.
5. **RED-2 — `PublicIdPersistenceTest`** (in-place): se amplía para
   registrar `Capitulo`/`Rubro` (entidad + repo + FK BIGINT + fixture
   `persistCapitulo`/`persistRubro` + tests de generación UUIDv7 y de
   `Optional.empty()` ante UUIDv7 inexistente). Se ejecuta antes de
   crear los fuentes Java y se capturan los errores de compilación
   esperados.
6. **GREEN-2 — `Capitulo.java` + `Rubro.java` +
   `CapituloRepository.java` + `RubroRepository.java`**: cuatro
   archivos Java con el patrón WU-03 exacto (sin `createdAt`/
   `updatedAt` ni callbacks, BigDecimal precision/scale exactos al
   DDL). Se re-ejecuta el `PublicIdPersistenceTest` y se verifica
   12/12 verde.
7. **Regresión estructural — `SchemaBaselineIT`**: se ejecuta el
   baseline preservado desde `main@HEAD`; sigue 6/6 verde con
   `capitulo`/`rubro` en `INTERNAL_TABLES` (V001 no declara
   `public_id` en esas tablas).
8. **REFACTOR — Spotless + revisión de duplicación**: se ejecuta
   `./gradlew spotlessApply`; 0 reformatos pendientes en código
   tocado. `git diff --check` limpio.
9. **Commit unitario**: `feat(presupuesto): Plan 019 identidad
   pública UUIDv7 y persistencia base`.

> **TRIANGULATE** (no implementado en este plan, documentar para
> planes posteriores): el recurso REST que valide `cid`/`rid` UUIDv7
> en frontera vive en planes 022/023; aquí sólo se valida la
> resolución interna desde la BD.

---

## Pruebas y comprobaciones

```bash
# Focused tests ejecutados por este plan (TDD del plan)
./gradlew test --tests 'ec.uce.propuestas.schema.V008SchemaIT' \
               --tests 'ec.uce.propuestas.schema.SchemaBaselineIT' \
               --tests 'ec.uce.propuestas.identifier.PublicIdPersistenceTest' \
               -Dquarkus.http.test-port=0 --console=plain

# Regresión adyacente (APU sigue apuntando a rubro.id BIGINT;
# responsabilidad del verificador independiente)
./gradlew test --tests 'ec.uce.propuestas.apu.*'

# Build sin tests (responsabilidad del verificador independiente)
./gradlew build -x test

# Conteo real desde XML (no presuponer cifras)
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
    build/test-results/test/TEST-ec.uce.propuestas.schema.V008SchemaIT.xml \
    build/test-results/test/TEST-ec.uce.propuestas.schema.SchemaBaselineIT.xml \
    build/test-results/test/TEST-ec.uce.propuestas.identifier.PublicIdPersistenceTest.xml

# Limpieza
git diff --check
git status --short
```

**Resultado esperado** (las cifras exactas se conocen al cierre):

- Tests verdes de las dos entidades + dos repositorios.
- Regresión APU verde (sin cambios en su schema ni en su repositorio).
- `git diff --check` limpio.

---

## Criterios de terminado

- [x] Existen `Capitulo.java` y `Rubro.java` con `publicId UUIDv7`
  (WU-03, sin timestamps ni callbacks).
- [x] Existen `CapituloRepository.java` y `RubroRepository.java` con
  `findByPublicIdAndOwnerScope` traversal correcto (Capitulo→
  Presupuesto→Proyecto; Rubro→Capitulo→Presupuesto→Proyecto).
- [x] Existe `V008__capitulo_rubro_public_id.sql` estructural;
  aplicada limpia en Postgres desde V001–V007.
- [x] Triggers `trg_public_id_immutable` activos para `capitulo` y
  `rubro`, reusando `fn_assert_public_id_immutable()` (V001 §5).
- [x] `SchemaBaselineIT` (6/6) preservado byte-for-byte desde
  `main@HEAD`; `V008SchemaIT` (5/5) nuevo y verde; `PublicIdPersistenceTest`
  (12/12) verde. Cifras medidas en
  `build/test-results/test/TEST-*.xml`, no presupuestas.
- [x] `./gradlew spotlessApply` sin reformatos pendientes en código
  tocado; `git diff --check` limpio.
- [x] **Verificación dirigida adyacente:** APU **41/41 verde**;
  `spotlessCheck` y `build -x test` verdes; Plan 020 (`recalculo`
  write-through) queda listo sobre esta capa de persistencia.
- [x] `graphify update .` ejecutado. Suite completa y Bruno quedan
  explícitamente reservados para Plan 025 (no son criterio de cierre de 019).

---

## STOP conditions (específicas de este plan)

- Flyway falla la migración V008 por trigger existente o por DEFAULT
  `uuidv7()` rechazado por la versión de Postgres. **STOP** — la
  extensión `pgcrypto` (que provee `gen_random_uuid()`) no resuelve este
  contrato: `uuidv7()` es una función incorporada de PostgreSQL 18, que
  es la versión configurada por el proyecto. Verificar la versión real
  del servidor antes de continuar.
- El traversal `Capitulo → Presupuesto → Proyecto → caller` devuelve
  fila cuando el proyecto es ajeno. **STOP** — el `where` debe estar en
  el join, no en un filtro posterior; revisar el patrón de
  `PresupuestoRepository.findByPublicIdAndOwnerScope`.
- La auditoría detecta que `rubro` ya tenía `public_id` declarado en
  V001. **STOP** — verificar; si V001 ya lo declara, V008 degenera a
  no-op (no crear migración duplicada). Documentar el hallazgo.

---

## Siguiente plan ejecutable

[`02-recalculo-write-through.md`](02-recalculo-write-through.md) (Plan
020) — crea el módulo profundo `recalculo`, único seam que decide qué
recálculo ejecutar tras cada mutación de los agregados `Presupuesto`,
`Capitulo` y `Rubro`. Depende de 019 para tener las entidades a las
que propagar.

---

## Apéndice — Mapping canónico V001 → entidades JPA (referencia viva)

> El ejecutor verifica este mapeo al implementar; cualquier desviación
> respecto a V001/06-database-schema.md §2.9 o §2.11 es condición de
> parada.

| Columna V001 | Tipo SQL | Campo JPA | Notas |
|---|---|---|---|
| `capitulo.id` | `BIGINT IDENTITY` | `Long id` con `@GeneratedValue(IDENTITY)` | PK interna |
| `capitulo.public_id` | `UUID DEFAULT uuidv7()` | `UUID publicId` con WU-03 | Identidad externa (V008) |
| `capitulo.presupuesto_id` | `BIGINT FK → presupuesto` | `Long presupuestoId` | FK BIGINT interna |
| `capitulo.parent_id` | `BIGINT FK → capitulo` (nullable) | `Long parentId` | NULL = raíz |
| `capitulo.item` | `VARCHAR(20)` | `String item` | `"1"`, `"1.1"`, … |
| `capitulo.descripcion` | `TEXT` | `String descripcion` | `@Column(columnDefinition = "TEXT")` |
| `capitulo.orden` | `SMALLINT` | `Short orden` | Orden dentro del padre |
| `capitulo.total` | `NUMERIC(14,6)` | `BigDecimal total = BigDecimal.ZERO` | Write-through (Plan 020) |
| `rubro.id` | `BIGINT IDENTITY` | `Long id` con `@GeneratedValue(IDENTITY)` | PK interna |
| `rubro.public_id` | `UUID DEFAULT uuidv7()` | `UUID publicId` con WU-03 | Identidad externa (V008) |
| `rubro.capitulo_id` | `BIGINT FK → capitulo` | `Long capituloId` | FK BIGINT interna |
| `rubro.apu_id` | `BIGINT FK → apu UNIQUE` | `Long apuId` | D-09 1:1 por versión |
| `rubro.item` | `VARCHAR(20)` | `String item` | Numeración jerárquica |
| `rubro.codigo` | `VARCHAR(20)` | `String codigo` | Espejo de `apu.codigo` |
| `rubro.descripcion` | `TEXT` | `String descripcion` | Heredado del APU |
| `rubro.unidad` | `VARCHAR(10)` | `String unidad` | Heredado del APU |
| `rubro.cantidad` | `NUMERIC(12,6) CHECK (>= 0)` | `BigDecimal cantidad` | V007 estructural |
| `rubro.precio_unitario` | `NUMERIC(14,6) DEFAULT 0` | `BigDecimal precioUnitario = ZERO` | Write-through (020) |
| `rubro.precio_total` | `NUMERIC(14,6) DEFAULT 0` | `BigDecimal precioTotal = ZERO` | Write-through (020) |

**FKs vigentes en V001** que las entidades JPA deben **NO** declarar
explícitamente (Panache las resuelve vía nombres de columna):
`capitulo.presupuesto_id → presupuesto.id` (CASCADE),
`capitulo.parent_id → capitulo.id` (CASCADE),
`rubro.capitulo_id → capitulo.id` (CASCADE),
`rubro.apu_id → apu.id` (RESTRICT, D-09). El equipo respeta el patrón
existente en `Apu.java` (sin `@ManyToOne`/`@JoinColumn`).

**Trigger de inmutabilidad `public_id`** (V008): exactamente el patrón
V001 §1/§5. La función compartida `fn_assert_public_id_immutable()`
(declarada en V001 §5) rechaza `NEW.public_id IS DISTINCT FROM
OLD.public_id`. V008 sólo crea dos `CREATE TRIGGER`
`trg_public_id_immutable` reusando esa función — **no** se crean
funciones por tabla. La migración es **estructural**: no reseed, no
backfill, no modifica V001–V007.
