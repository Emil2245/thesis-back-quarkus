# Plan 019 — Identidad pública UUIDv7 y persistencia base del módulo `presupuesto`

> **Plan 019** del módulo [`05-presupuesto`](00.md). PLANNED / READY —
> 2026-08-31. **No implementado todavía.**

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

**PLANNED / READY — 2026-08-31.** El ejecutor actualiza esta sección al
término. Resultado esperado: «DONE (YYYY-MM-DD). Verificación dirigida
verificada: N/N tests verdes (lista); sin regresión en módulos
colindantes; `git diff --check` limpio; build sin tests y
`spotlessCheck` verde (la deuda Spotless global quedó saldada por
Plan 08; no se excluye la tarea).» Si la verificación dirigida no
se ejecuta, queda «PARTIAL — verificación dirigida no reportada».

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
6. **Flyway** configurado; la última migración aplicada es V007
   (`V007__rubro_cantidad_check_ge_zero.sql`, estructural). Las
   migraciones se numeran con tres dígitos sin huecos.

### Lo que falta (este plan lo construye)

1. **Entidades JPA `Capitulo` y `Rubro`** con la columna `publicId`
   UUIDv7, idéntica convención al `Presupuesto`/`Apu` existentes.
2. **Repositorios `CapituloRepository` y `RubroRepository`** con
   `findByPublicIdAndOwnerScope` traversal propio.
3. **Migración V008 estructural** que añade `public_id UUID NOT NULL
   UNIQUE DEFAULT uuidv7()` a `capitulo` y `rubro` (NO a `cronograma`
   ni a `actividad` — eso es una migración futura de I-08 (numeración por determinar)). La migración crea además el
   trigger de inmutabilidad `BEFORE UPDATE OF public_id` para cada
   tabla, alineado con el patrón de V001 §1 / §5.
4. **DTO de error `validacion`** consistente (reusa `ProblemaException`
   de `common/`).
5. **Tests de persistencia + owner-scope** verde antes de continuar con
   020.

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
  `src/test/java/ec/uce/propuestas/presupuesto/`.

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

# Tests
src/test/java/ec/uce/propuestas/presupuesto/repository/CapituloRepositoryIT.java
src/test/java/ec/uce/propuestas/presupuesto/repository/RubroRepositoryIT.java
src/test/java/ec/uce/propuestas/presupuesto/repository/V008MigrationIT.java
```

### Columnas añadidas en V008

Para `capitulo`:

```sql
ALTER TABLE capitulo
    ADD COLUMN public_id UUID NOT NULL UNIQUE DEFAULT uuidv7();

CREATE OR REPLACE FUNCTION capitulo_public_id_inmutable()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.public_id IS DISTINCT FROM OLD.public_id THEN
        RAISE EXCEPTION 'public_id inmutable en capitulo (fila id=%)', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_capitulo_public_id_inmutable
    BEFORE UPDATE OF public_id ON capitulo
    FOR EACH ROW
    EXECUTE FUNCTION capitulo_public_id_inmutable();
```

Para `rubro` — DDL análogo. La función y trigger se nombran
`rubro_public_id_inmutable()` y `trg_rubro_public_id_inmutable`.

> **Patrón V001 §1**: la regla «BIGINT PK + `public_id` UUIDv7 = invariante
> híbrido» se preserva exactamente. La columna `public_id` ya tenía un
> trigger equivalente en V001 para `presupuesto` y `apu`; V008 lo extiende
> a `capitulo` y `rubro`. **No** se reabre ninguna migración previa.

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

    @PrePersist
    void onInsert() { /* ... */ }

    @PreUpdate
    void onUpdate() { /* ... */ }
}
```

`Rubro.java` análogo con `capituloId`, `apuId`, `item`, `codigo`,
`descripcion`, `unidad`, `cantidad`, `precioUnitario`, `precioTotal`.

### Métodos de repositorio (mínimo viable)

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

    public List<Capitulo> listarRaices(Long presupuestoId) {
        return list("presupuestoId = :pid and parentId is null order by orden",
                    Parameters.with("pid", presupuestoId));
    }

    public List<Capitulo> listarHijos(Long parentId) {
        return list("parentId = :pid order by orden",
                    Parameters.with("pid", parentId));
    }
}
```

`RubroRepository.findByPublicIdAndOwnerScope` con traversal
`Rubro → Capitulo → Presupuesto → Proyecto → caller`. Listados por
`capituloId` con `order by orden` ascendente.

---

## Pasos

1. **Auditar el patrón WU-03** leyendo `Presupuesto.java`,
   `Apu.java`, `BaseInsumos.java` (en el módulo `insumo`),
   `PlantillaApu.java` y `PlantillaProyecto.java`. Confirmar la convención
   exacta: `@Generated(event = EventType.INSERT) +
   insertable=false, updatable=false` + `@Column(name="public_id")`. No
   introducir variantes.
2. **Auditar `UuidV7.parse`** en
   `ec.uce.propuestas.common`. Confirmar firma canónica.
   Reusar, no duplicar.
3. **RED — `CapituloRepositoryIT`** (`@QuarkusTest` con Dev Services):
   - Crea proyecto + presupuesto + capítulo; comprueba que
     `findByPublicIdAndOwnerScope` resuelve por UUIDv7.
   - Comprueba que un capítulo de proyecto ajeno devuelve `empty`.
   - Comprueba que un UUID v4 devuelve `empty`.
   - Comprueba que el trigger de inmutabilidad rechaza
     `UPDATE capitulo SET public_id = …` con la fila modificada
     (cualquier valor distinto al UUIDv7 asignado por `DEFAULT`).
4. **RED — `V008MigrationIT`** (`@QuarkusTest`): ejecuta Flyway
   sobre una BD limpia V007 y verifica que la columna
   `capitulo.public_id` / `rubro.public_id` existe con `NOT NULL`,
   que el trigger `trg_public_id_immutable` rechaza cambios, y que
   un segundo pase de la migración no rompe nada. El test verifica
   además que una BD pre-poblada al estado V007 recibe los UUIDv7
   vía `DEFAULT uuidv7()` sin backfill manual ni reseed de negocio.
5. **GREEN — `Capitulo.java`**: implementa la entidad con el patrón
   WU-03 y los 4 métodos de consulta.
6. **GREEN — `CapituloRepository.java`**: implementa los métodos del
   contrato. Anota con comentario `WU-03` la firma owner-scope.
7. **Repite RED/GREEN para `Rubro.java` + `RubroRepository.java`** con
   sus tests homólogos (`RubroRepositoryIT`).
8. **V008 — escribir `V008__capitulo_rubro_public_id.sql`** siguiendo
   el patrón V001 §1 (DDL + trigger genérico `BEFORE UPDATE OF
   public_id` por tabla, reusando `fn_assert_public_id_immutable()`).
   La verificación de idempotencia de la migración vive en
   `V008MigrationIT` (no se usa `docker exec psql` ni se asume un
   contenedor externo).
9. **Verificación dirigida**:
   - `./gradlew test --tests 'ec.uce.propuestas.presupuesto.*'
     -Dquarkus.http.test-port=0 --console=plain` → tests
     presupuesto.* verde.
   - `./gradlew test --tests 'ec.uce.propuestas.apu.*'
     -Dquarkus.http.test-port=0 --console=plain` → regresión APU
     verde (las columnas APU no cambian; el FK `rubro.apu_id → apu.id`
     ya existía).
   - `./gradlew build -x test --console=plain` →
     BUILD SUCCESSFUL.
   - `git diff --check` → sin salida.
10. **Commit unitario** con mensaje `feat(presupuesto): Plan 019
    identidad pública UUIDv7 y persistencia base`.

> **TRIANGULATE** (no implementado en este plan, documentar para
> planes posteriores): el recurso REST que valide `cid`/`rid` UUIDv7
> en frontera vive en planes 022/023; aquí sólo se valida la
> resolución interna desde la BD.

---

## Pruebas y comprobaciones

```bash
# Verificación dirigida (este plan)
./gradlew test --tests 'ec.uce.propuestas.presupuesto.entity.*'
./gradlew test --tests 'ec.uce.propuestas.presupuesto.repository.*'

# Regresión adyacente (APU sigue apuntando a rubro.id BIGINT)
./gradlew test --tests 'ec.uce.propuestas.apu.*'

# Build sin tests
./gradlew build -x test

# Conteo real desde XML (no presuponer cifras)
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
    build/test-results/test/TEST-ec.uce.propuestas.presupuesto.*.xml

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

- [ ] Existen `Capitulo.java` y `Rubro.java` con `publicId UUIDv7` (WU-03).
- [ ] Existen `CapituloRepository.java` y `RubroRepository.java` con
  `findByPublicIdAndOwnerScope` traversal correcto.
- [ ] Existe `V008__capitulo_rubro_public_id.sql` estructural;
  aplicada limpia en Postgres desde V001–V007.
- [ ] Triggers genéricos `BEFORE UPDATE OF public_id` activos para
  `capitulo` y `rubro`, reutilizando `fn_assert_public_id_immutable()`
  (V001 §1).
- [ ] Tests verdes: `CapituloRepositoryIT`, `RubroRepositoryIT` y
  `V008MigrationIT` (verifica UUIDv7 generado, owner-scope, rechazo
  de mutación inválida del `public_id` y migración sobre BD V007
  poblada). Sin cifras presupuestas — se reportan desde
  `build/test-results/test/`.
- [ ] Regresión APU verde.
- [ ] `git diff --check` limpio.
- [ ] Plan 020 (`recalculo` write-through) listo para ejecutarse
  encima de esta capa de persistencia.

---

## STOP conditions (específicas de este plan)

- Flyway falla la migración V008 por trigger existente o por DEFAULT
  `uuidv7()` rechazado por la versión de Postgres. **STOP** — la
  extensión `pgcrypto` (que provee `uuid_generate_v4`) **no** es la que
  se usa; V001 ya usa `uuidv7()` que requiere la función personalizada
  declarada en la migración inicial. Verificar el seed de V001 antes de
  continuar.
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
V001 §1. La función `capitulo_public_id_inmutable()` rechaza
`NEW.public_id IS DISTINCT FROM OLD.public_id`. La función
`rubro_public_id_inmutable()` análoga. La migración es **estructural**:
no reseed, no backfill, no modifica V001–V007.
