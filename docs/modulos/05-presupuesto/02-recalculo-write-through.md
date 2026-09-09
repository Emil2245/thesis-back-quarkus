# Plan 020 — Activación del módulo profundo `recalculo` (write-through)

> **Plan 020** del módulo [`05-presupuesto`](00.md). **DONE — 2026-09-01
> (verificación dirigida completa; suite completa / Spotless / build siguen
> pendientes, no se reclaman).** Activa el módulo profundo
> `recalculo` descrito en
> [`../../../../thesis-docs/plan/architecture/08-codebase-design.md`](../../../../thesis-docs/plan/architecture/08-codebase-design.md)
> §1 y §3 (marcado ACTIVADO en I-07 / Plan 020, 2026-09-01). El bloqueo
> original que impedía la activación (incompatibilidad de contrato del
> motor con `porcentajeDescuento` global vs. por APU) lo resolvió
> [Plan 015 — DONE 2026-09-01](../../../plans/015-retirar-descuento-apu.md).

## Resultado esperado

Existe un nuevo módulo `ec.uce.propuestas.recalculo` con la interfaz
`recalcular(Alcance)` declarada en `08-codebase-design.md` §3. Toda
mutación del agregado `Presupuesto → Capitulo → Rubro` (y de
`APU → Rubro`) pasa por `RecalculoService`, que carga el snapshot
mínimo necesario, invoca el motor existente (`Motor.calcularApu` /
`Motor.consolidar`) y persiste los derivados (`precio_unitario`,
`precio_total`, `capitulo.total`, `presupuesto.total`) **en la misma
transacción** que la mutación. `actividad.peso_ponderado` queda diferido a
I-08: I-07 no crea entidades, repositorios ni SQL ad hoc de cronograma. Los recursos REST de I-07 (022, 023, 024) **no**
deciden qué recalcular — declaran el `Alcance` que mutaron y delegan.

## Dependencias

- [Plan 019 — Identidad pública y persistencia base](01-identidad-y-persistencia.md)
  (necesita `Capitulo`/`Rubro` JPA para propagar).
- **[Plan 015 — Retirar `descuento` por APU](../../../plans/015-retirar-descuento-apu.md)**
  (DONE 2026-09-01 — **pre-requisito cerrado**). Sin Plan 015,
  este Plan 020 permanecía **BLOCKED** por la incompatibilidad del
  motor (`Motor.consolidar` exigía `porcentajeDescuento` global mientras
  el modelo ofrecía `apu.porcentaje_descuento` por APU). Plan 015 retiró
  ambos y dejó la columna BD como compatibility seam inert.
- Módulo `apu/` ya cerrado (DONE 2026-08-30, Plan 03 + 07).
- Módulo `motor/` ya cerrado (DONE 2026-08-28, Plan 014 — **NO se
  reabre**, sólo se reusa `Motor.calcularApu` / `Motor.consolidar`).

## Estado de cierre

**DONE — 2026-09-01 · verificación dirigida completa; suite completa / Spotless
/ build siguen pendientes (no se reclaman).** El módulo
`ec.uce.propuestas.recalculo` está activo con `Alcance = Version | Apu | Insumo`
(sealed interface); `Motor` se invoca estáticamente; `VersionSnapshotBuilder`
permanece en `recalculo/internal` (`public` por el límite real de paquete Java,
no es seam adicional). `@Transactional` usa propagación `REQUIRED` (default).
`actividad.peso_ponderado` queda fuera hasta I-08. El motor no se reabre; la
regla workbook-consistent (Plan 014) y los residuales aceptados
(GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84`) se preservan.

**Bloqueo original resuelto por Plan 015 (DONE 2026-09-01):** el contrato de
`Motor.consolidar(VersionSnapshot)` exigía un único
`VersionSnapshot.parametrosProyecto().porcentajeDescuento` global, mientras el
modelo ofrecía `apu.porcentaje_descuento` por APU. Plan 015 retiró el *seam*
activo: `motor.ParametrosCalculo.porcentajeDescuento` eliminado;
`motor.ApuCalculado.costoDirectoAjustado` eliminado (CD ≡ CD_ajustado con
`descuento = 0` ⇒ `CI = CD × %CI`, `CT = CD + CI`); `Apu.porcentajeDescuento`
(campo Java) eliminado; columna BD `apu.porcentaje_descuento` queda como
**compatibility seam inert** (sin V009; JPA la ignora);
`PATCH /api/v1/apus/{apuId}/porcentaje-descuento` retirado; campos JSON
`ApuResponse.porcentajeDescuento`, `ApuCalculoParametros.descuento`,
`ApuCalculoResumen.cdAjustado`/`operacionCdAjustado` retirados. El descuento
sobrevive únicamente como **FORMA 1** (mutación de las columnas base de los
**insumos elegibles** copiados a la base PROYECTO del proyecto; **MO exenta**,
reversible desde la base, regulada por
`parametros_sistema.rango_descuento_min/max`) y **FORMA 2** (edición atómica
de un insumo ya PROYECTO; sin seam nuevo). **No** se reintroduce “monto
absoluto”. `rango_descuento_min/max` se conservan en `ParametrosSistema`
(regulan FORMA 1).

**Evidencia TDD (medida):** `RecalculoServiceIT` 4/4 verdes (TDD focal con
`@QuarkusTest` + Dev Services; `Motor` real, sin mocks; cobertura de los
3 `Alcance` con `Version`/`Apu`/`Insumo`). El intento BLOCKED previo fue
retirado (RED focal había fallado en `:compileTestJava` por ausencia de
`Alcance`/`RecalculoService`; ningún código de producción ni test de ese
intento sobrevivió).

**DAG I-07:** Plan 019 + Plan 015 + Plan 020 **DONE**. Planes 021–025 vuelven
a **PLANNED / READY** sin bloqueo transitivo. **Siguiente plan ejecutable =
Plan 021** (`03-ciclo-presupuesto.md`). V001–V008 intactas; V009 no se
pre-asigna.

---

## Contexto / estado actual

### Lo que ya existe (DONE, NO se reabre)

1. **Motor `Motor.calcularApu(snapshot, params)` y
   `Motor.consolidar(versionSnapshot)`** —
   `src/main/java/ec/uce/propuestas/motor/Motor.java`. Puro Java,
   `BigDecimal` natural, regla workbook-consistent en
   `internal/Consolidador.java` (`PU DOWN 2dp`, `PT = cantidad × PU_2dp`
   a escala 6 `HALF_UP`). GM-19/GM-20 con residual aceptado (cierre
   parcial 2026-08-28).
2. **Snapshots y resultados del motor** —
   `ApuSnapshot`, `ApuCalculado`, `VersionSnapshot`, `VersionCalculada`,
   `FilaSnapshot`, `ParametrosCalculo`, `SeccionSnapshot`. Todos en
   `src/main/java/ec/uce/propuestas/motor/`. Reutilizables tal cual.
3. **Módulo `apu.service.ApuCalculoService`** ya hace el write-through a
   nivel APU (sus propias columnas `costo_directo`, `costo_indirecto`,
   `costo_total`). Lo que falta es la propagación **rubro → capítulo →
   presupuesto**.
4. **`Actividad.peso_ponderado` write-through** — la columna existe en
   V001 pero **no se calcula** y todavía no existe entidad/repositorio del
   módulo cronograma. Plan 020 no anticipa I-08: no lee ni escribe
   `actividad`; su integración con `Alcance.Version` se añadirá cuando I-08
   active ese agregado.
5. **`recalculo/`** existe en `src/main/java/ec/uce/propuestas/` desde
   el cierre de este plan (Plan 020, DONE 2026-09-01). I-06 lo había
   dejado DEFERRED (`docs/modulos/04-apu-avanzado.md` §2.5; repetido en
   README del módulo y `estado-actual.md` §2); este plan lo activó.
6. **`ApuRepository`, `CapituloRepository` (Plan 019),
   `RubroRepository` (Plan 019), `PresupuestoRepository`** —
   disponibles para que `RecalculoService` cargue el snapshot mínimo.

### Decisiones de diseño locked (no se reabren)

- **Motor no se reabre** (Plan 014 §STOP, motor cerrado con residual
  aceptado).
- **`recalculo` es un módulo profundo real** (`08-codebase-design.md`
  §1 — «siete módulos con comportamiento real»). Su interfaz es
  `recalcular(Alcance)`; su única dependencia interna es `motor`; su
  dependencia externa es Postgres (local-sustituible — ADR 10).
- **No se introduce puerto de repositorio** (ADR 10): `recalculo`
  consume los repos de `apu`/`presupuesto` directamente, sin interfaz
  propia. Coherente con la regla «un adaptador = costura hipotética;
  dos adaptadores = costura real».
- **Write-through es transaccional**: la mutación y el recálculo viven
  en una sola `@Transactional`. Si el recálculo falla, la mutación
  aborta (rollback).
- **`BigDecimal` natural**: `recalculo` NO aplica redondeo intermedio.
  Sólo invoca el motor existente, que ya implementa la regla
  workbook-consistent.
- **Sin propagación a la base PROYECTO** de insumos: el ajuste del
  precio de un insumo (P-22, P-14) ya está cubierto por el motor
  (herencia null-means-inherit) + el `RecalculoService` que aquí se
  activa. La edición atómica P-22 llama a `recalcular(APU(...))` por
  cada APU afectado (fan-out). I-07 no introduce nueva lógica para la
  propagación insumo→APU (ya vivía en P-22); lo que introduce es la
  propagación APU→Rubro→Capitulo→Presupuesto.
- **Sin seam para tests paralelos**: tests por la API pública
  (`@QuarkusTest` + Dev Services). Tests unitarios directos al
  `RecalculoService` con mocks sólo cuando la firma lo permita
  claramente (no forzarlos).

---

## Alcance

### Incluye

- Crear `src/main/java/ec/uce/propuestas/recalculo/` con:
  - `Alcance.java` (sealed interface con exactamente 3 records).
  - `RecalculoService.java`.
  - `internal/VersionSnapshotBuilder.java` (`public` por el límite de
    subpaquete Java; sin exponerlo por REST ni convertirlo en seam).
- Añadir únicamente consultas de persistencia consumidas por el builder/orquestador
  en los repositorios existentes de `apu` y `presupuesto` (listar una versión,
  resolver el rubro de un APU y resolver versiones afectadas por un insumo).
- Crear tests en `src/test/java/ec/uce/propuestas/recalculo/`:
  - `RecalculoServiceIT.java` (`@QuarkusTest` + Dev Services,
    integración real y evidencia RED/GREEN principal).
  - `RecalculoServiceTest.java` sólo si aparece una regla pura que pueda
    aislarse sin mockear `Motor` ni falsear la transacción.
- Documentar el seam en
  `docs/modulos/05-presupuesto/02-recalculo-write-through.md`
  (este archivo) + actualizar `08-codebase-design.md` §3 marcando
  `recalculo` como **IMPLEMENTED en I-07** (alineamiento canónico
  por Plan 020).
- Sin migraciones nuevas.

### No incluye (alcance cerrado)

- **No** se reabre el motor (`Motor.java`, `internal/*`).
- **No** se crea un nuevo seam de `recalculo` con adaptadores
  alternativos (un solo adaptador Postgres; ADR 10).
- **No** se introduce `Lombok`/`MapStruct` ni dependencias nuevas.
- **No** se modifica la firma de `Motor.calcularApu` /
  `Motor.consolidar` (la interfaz es estable desde Plan 014).
- **No** se modifica `ApuCalculoService` (sigue haciendo el
  write-through APU-only; Plan 020 no lo toca).
- **No** se conectan las mutaciones de
  `parametros_proyecto`/`%CI`/`%HM` a este seam (esa integración queda fuera
  del alcance cerrado de Plan 020). No obstante, al construir un
  `VersionSnapshot`, se leen los parámetros efectivos vigentes del proyecto
  porque son entrada obligatoria del motor; esto no crea una mutación nueva.
- **No** se modifica el contrato API. Los recursos REST de I-07
  invocan `RecalculoService` desde el lado servidor — sin nuevos
  endpoints ni cambios en responses existentes.

---

## Contrato esperado (interfaz del módulo)

### `Alcance.java`

```java
package ec.uce.propuestas.recalculo;

/**
 * Seam del módulo {@code recalculo}. Modela «qué cambió» en una
 * mutación; el {@link RecalculoService} decide qué recalcular y cómo
 * propagar.
 *
 * <p>Los recursos REST declaran el Alcance que mutaron; nunca deciden
 * qué recalcular. Esto cumple la regla de disciplina de costuras del
 * módulo profundo (08-codebase-design.md §3).
 */
public sealed interface Alcance
        permits Alcance.Version, Alcance.Apu, Alcance.Insumo {

    /** Recalcular una versión entera (capítulos raíz + totales). */
    record Version(Long presupuestoId) implements Alcance {}

    /** Recalcular un APU: actualiza su costo_total y propaga a su rubro
     *  (si está vinculado) y ascendentes. */
    record Apu(Long apuId) implements Alcance {}

    /** Recalcular un insumo: propaga al APU que hereda el precio mutado
     *  (override NULL) y, vía APU, a su rubro y ascendentes. */
    record Insumo(Long insumoId) implements Alcance {}
}
```

### `RecalculoService.java`

```java
package ec.uce.propuestas.recalculo;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Módulo profundo {@code recalculo} (08-codebase-design.md §3).
 *
 * <p>Convierte «qué cambió» en derivados persistidos: los recursos
 * REST declaran el {@link Alcance} y este servicio decide qué
 * invocar del motor y qué columnas reescribir.
 *
 * <p>Cobertura:
 * <ul>
 *   <li>{@link Alcance.Version}: versión completa → totales raíz
 *       (reusa {@code Motor.consolidar} con el snapshot reducido).
 *       Es el alcance que declaran las mutaciones estructurales del
 *       agregado {@code Presupuesto → Capitulo → Rubro} (P-28/29/31);
 *       la propagación grano fino (capítulo, rubro) se sirve
 *       recorriendo el árbol por alcance de versión completa.</li>
 *   <li>{@link Alcance.Apu}: APU individual → reusa
 *       {@code Motor.calcularApu}, actualiza su rubro (si lo hay),
 *       luego escala a capítulo y presupuesto.</li>
 *   <li>{@link Alcance.Insumo}: insumo → reusa
 *       {@code Motor.calcularApu} sobre cada APU que hereda el
 *       precio mutado (override NULL, N04 §A1 FORMA 2), propaga
 *       al rubro vinculado y ascendentes hasta el presupuesto.</li>
 * </ul>
 *
 * <p>El servicio es transaccional: si el recálculo falla, la
 * mutación que lo invocó aborta (rollback).
 */
@ApplicationScoped
public class RecalculoService {

    @Inject ApuRepository apuRepository;
    @Inject ApuCalculoService apuCalculoService;
    @Inject RubroRepository rubroRepository;
    @Inject PresupuestoRepository presupuestoRepository;
    @Inject VersionSnapshotBuilder snapshotBuilder;

    // REQUIRED es el default: se une a la transacción de la mutación caller.
    // Motor.consolidar(...) se invoca estáticamente; Motor nunca es CDI.
    @Transactional
    public void recalcular(Alcance alcance) {
        switch (alcance) {
            case Alcance.Version v -> recalcularVersion(v.presupuestoId());
            case Alcance.Apu a     -> recalcularApu(a.apuId());
            case Alcance.Insumo i  -> recalcularInsumo(i.insumoId());
        }
    }

    // ... métodos privados ...
}
```

### `internal.VersionSnapshotBuilder.java`

Helper ubicado en el subpaquete `internal` que construye el
`VersionSnapshot` consumido por el motor. Debe ser `public` porque un subpaquete
Java no comparte visibilidad package-private con `recalculo`; esta visibilidad
es una necesidad de compilación, no un seam adicional. Su implementación inicial
carga la versión completa y construye capítulos/rubros/APUs/filas con IDs internos
sólo en persistencia; el contrato del motor continúa usando códigos e ítems.

> **Regla de disciplina:** los tests verifican el seam por la API pública
> (`RecalculoService.recalcular(...)`). **No** se testea el builder directamente.

---

## Pasos

### Numerados y secuenciales

1. **Auditar el contrato del motor** abriendo
   `src/main/java/ec/uce/propuestas/motor/Motor.java` +
   `VersionSnapshot.java` + `ApuSnapshot.java` +
   `VersionCalculada.java`. Confirmar firmas. Listar qué campos del
   snapshot exige el motor y qué columnas de BD alimenta la
   `VersionCalculada` de vuelta (necesario para mapear a
   `rubro.precio_unitario`/`precio_total`/`capitulo.total`/
   `presupuesto.total`).
2. **RED — tests del seam**: crear primero `RecalculoServiceIT` contra
   Postgres real y, sólo si aporta aislamiento sin falsear transacciones,
   `RecalculoServiceTest`. El motor no es inyectable ni se mockea. Cubrir los
   3 casos del seam canónico (`Alcance = Version | Apu | Insumo`):
   - `recalcular_apu_sin_rubro_vinculado_actualiza_solo_el_apu`.
   - `recalcular_apu_con_rubro_propagacion_esperada`.
   - `recalcular_version_raices_coinciden_con_consolidar` (este test
     cubre por alcance de versión la propagación grano fino del
     agregado `Presupuesto → Capitulo → Rubro`, ya que las mutaciones
     estructurales de P-28/29/31 declaran `Alcance.Version(...)`).
   - `recalcular_insumo_propaga_a_herederos` (override NULL): un insumo
     mutado propaga a los APUs que lo heredan sin override y, vía APU, a
     sus versiones afectadas; los detalles con override explícito no cambian.
   - `recalcular_version_es_idempotente_y_totaliza_capitulos_recursivos`:
     dos invocaciones producen exactamente los mismos derivados a escala 6.
   - `recalcular_falla_revierte_mutacion_y_derivados`: una mutación previa en
     la misma transacción se revierte si el snapshot/consolidación falla.
3. **GREEN — `Alcance.java`** (sealed interface con 3 records:
   `Version`, `Apu`, `Insumo` — no se introducen `Capitulo` ni `Rubro`).
4. **GREEN — `RecalculoService.java`**: orquestador transaccional con
   el `switch` exhaustivo sobre `Alcance`. Cada rama:
   - `Version`: cargar la versión completa mediante `VersionSnapshotBuilder`,
     invocar estáticamente `Motor.consolidar(...)`, persistir
     `presupuesto.total` + `capitulo.total` recursivos +
     `rubro.precio_unitario` / `precio_total`. Es
     además el alcance que cubren las mutaciones estructurales del
     agregado de P-28/29/31 (crear/editar/mover/eliminar capítulos,
     CRUD rubros, deep copy): el recorrido del árbol se hace dentro
     del orquestador de `Version`.
   - `Apu`: cargar el APU, delegar su write-through local a
     `ApuCalculoService.recalcular(apu)` y, si está vinculado a un rubro,
     consolidar la versión completa. Así la frontera APU→Rubro permanece
     exclusivamente en `Motor.consolidar`; no se replica `setScale` ni se
     accede a helpers internos del motor. Si el APU no tiene rubro, termina
     tras actualizar sólo el APU.
   - `Insumo`: resolver los APUs que heredan el precio (override `NULL`),
     agrupar sus `presupuesto_id` distintos y consolidar cada versión afectada
     una sola vez. No se inventa un umbral: primero se implementa el camino
     correcto y determinista; una optimización futura exige medición y plan.
5. **GREEN — `internal/VersionSnapshotBuilder.java`**: clase `public` con
   un método público que retorna el `VersionSnapshot` de un presupuesto.
   Implementación simple: carga la versión completa (el caso real IESS —
   33 capítulos / 298 rubros
   — es sub-segundo en memoria según §2.4 del codebase-design). Si en
   el futuro una versión excede el presupuesto, se cachea
   `ApuCalculado` por APU no afectado dentro del propio motor (sin
   cambiar la interfaz — `08-codebase-design.md` §2.4).
6. **TRIANGULATE — `RecalculoServiceIT`** (`@QuarkusTest` + Dev Services):
   - El setup persiste directamente proyecto, parámetros, presupuesto con
     `total = 0`, árbol recursivo, rubros, APUs, secciones y detalles. No usa
     `POST /proyectos`, read models ni recursos de Planes 021–025.
   - Modifica cantidad y llama `recalcular(new Alcance.Version(id))`; verifica
     por repositorios `Rubro.precioUnitario/precioTotal`, `Capitulo.total`
     recursivo y `Presupuesto.total` en la misma transacción.
   - Verifica rollback con motor real provocando una entrada inválida dentro
     de la transacción; no se inyecta ni mockea `Motor`.
7. **Verificación dirigida**:
   - `./gradlew test --tests 'ec.uce.propuestas.recalculo.*'
     -Dquarkus.http.test-port=0 --console=plain` → tests verdes.
   - `./gradlew test --tests 'ec.uce.propuestas.motor.*'
     -Dquarkus.http.test-port=0 --console=plain` → regresión motor
     verde (no se reabre).
   - `./gradlew test --tests 'ec.uce.propuestas.apu.*'
     -Dquarkus.http.test-port=0 --console=plain` → regresión APU
     verde.
   - `./gradlew spotlessCheck --console=plain` → BUILD SUCCESSFUL.
   - `./gradlew build -x test --console=plain` → BUILD SUCCESSFUL.
   - `git diff --check` → sin salida.
   - `graphify update .` → grafo actualizado.
8. **Actualizar `08-codebase-design.md` §3** cambiando
   «`recalculo` permanece DEFERRED» por «**ACTIVADO en I-07
   (2026-MM-DD), Plan 020**» y añadiendo una nota que el módulo ya
   implementa los 3 `Alcance` con su semántica. Esta edición está
   autorizada por el alcance de Plan 020 (alineamiento canónico).
9. **No crear commit.** Esta ejecución fue solicitada explícitamente sin
   commits; dejar el working tree listo para revisión humana.

---

## Pruebas y comprobaciones

```bash
# Verificación dirigida
./gradlew test --tests 'ec.uce.propuestas.recalculo.*' \
    -Dquarkus.http.test-port=0 --console=plain

# Regresión adyacente
./gradlew test --tests 'ec.uce.propuestas.motor.*' \
    -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.apu.*' \
    -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.presupuesto.entity.*' \
    -Dquarkus.http.test-port=0 --console=plain

# Calidad y build sin tests
./gradlew spotlessCheck --console=plain
./gradlew build -x test --console=plain

# Conteo real
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
    build/test-results/test/TEST-ec.uce.propuestas.recalculo.*.xml

git diff --check
git status --short

# Refrescar el grafo al final
graphify update .
```

**Resultado esperado** (cifras al cierre):

- `RecalculoServiceIT` verde; unit test adicional sólo si aporta una regla pura.
- Motor, APU y presupuesto siguen sin regresión, preservando los residuales aceptados.
- `spotlessCheck`, `build -x test` y `git diff --check` limpios.
- `graphify update .` ejecutado.

---

## Criterios de terminado

- [ ] Existe `Alcance.java` (sealed interface con 3 records:
  `Version`, `Apu`, `Insumo`).
- [ ] Existe `RecalculoService.java` con `recalcular(Alcance)`
  transaccional y orquestación completa.
- [ ] Existe `internal/VersionSnapshotBuilder.java`.
- [ ] `RecalculoServiceIT` verde y evidencia RED/GREEN registrada; cualquier
  unit test adicional cubre sólo reglas puras (cifras desde XML Gradle).
- [ ] Regresión motor/APU/presupuesto-entity verde (sin cifras
  presupuestas).
- [ ] `git diff --check` limpio.
- [ ] `08-codebase-design.md` §3 marcado como ACTIVADO en I-07 (Plan
  020); la firma canónica `Alcance = apu | version | insumo` se
  preserva — **no** se introducen casos `Capitulo` o `Rubro` (la
  propagación grano fino del agregado se sirve por
  `Alcance.Version(presupuestoId)`).
- [ ] Plan 022 (capítulos) declara `Alcance.Version(presupuestoId)`
  tras mutaciones estructurales del árbol; Plan 023 (rubros) idem.
  La propagación APU→Rubro sigue usando `Alcance.Apu(apuId)`.

---

## STOP conditions (específicas de este plan)

- El motor expone una nueva dependencia que `recalculo` no puede
  satisfacer sin reabrir `Motor.java`. **STOP** — el motor está
  cerrado; este plan no negocia su forma.
- La propagación APU→Rubro requiere una regla de redondeo distinta
  a la workbook-consistent. **STOP** — Plan 014 §STOP prohíbe
  reintroducir `setScale(2, DOWN)` simétrico.
- `RecalculoServiceIT` requiere sembrar más de un proyecto + un APU
  con 4 secciones + 1 cronograma para que el test sea realista. Si
  la fixture es demasiado costosa (>5 segundos), el ejecutor debe
  reducir el test a una sola mutación atómica. Si el problema
  persiste, **STOP** — escalar.
- `presupuesto.total` calculado por `Motor.consolidar` no coincide
  con el `presupuesto.total` actual tras la primera invocación. **STOP**
  — verificar que la fila inicial `presupuesto` se crea con
  `total = 0` y que ningún `presupuesto.total` previo sesga el
  recálculo. Si hay residuo histórico, documentar.
- Aparece un test que requiere `Mock` del motor. **STOP** — el motor no es
  inyectable y su contrato es estable; mockear estáticos falsearía el seam.
  Invertir el test: usar el motor real con persistencia/snapshot controlados.

---

## Siguiente plan ejecutable

**Plan 021** — [`03-ciclo-presupuesto.md`](03-ciclo-presupuesto.md). DAG
I-07 desbloqueado (Plan 019 + Plan 015 + Plan 020 **DONE**); Plan 021 es
el siguiente ejecutable.
