# Plan 020 — Activación del módulo profundo `recalculo` (write-through)

> **Plan 020** del módulo [`05-presupuesto`](00.md). PLANNED / READY —
> 2026-08-31. **No implementado todavía.** Activa el módulo profundo
> `recalculo` descrito en
> [`../../../../thesis-docs/plan/architecture/08-codebase-design.md`](../../../../thesis-docs/plan/architecture/08-codebase-design.md)
> §1 y §3 con su contrato diferido por I-06 (donde se documentó
> `recalculo — DEFERRED`). I-07 es el primer plan que lo activa.

## Resultado esperado

Existe un nuevo módulo `ec.uce.propuestas.recalculo` con la interfaz
`recalcular(Alcance)` declarada en `08-codebase-design.md` §3. Toda
mutación del agregado `Presupuesto → Capitulo → Rubro` (y de
`APU → Rubro`) pasa por `RecalculoService`, que carga el snapshot
mínimo necesario, invoca el motor existente (`Motor.calcularApu` /
`Motor.consolidar`) y persiste los derivados (`precio_unitario`,
`precio_total`, `capitulo.total`, `presupuesto.total`,
`actividad.peso_ponderado` cuando exista) **en la misma transacción**
que la mutación. Los recursos REST de I-07 (022, 023, 024) **no**
deciden qué recalcular — declaran el `Alcance` que mutaron y delegan.

## Dependencias

- [Plan 019 — Identidad pública y persistencia base](01-identidad-y-persistencia.md)
  (necesita `Capitulo`/`Rubro` JPA para propagar).
- Módulo `apu/` ya cerrado (DONE 2026-08-30, Plan 03 + 07).
- Módulo `motor/` ya cerrado (DONE 2026-08-28, Plan 014 — **NO se
  reabre**, sólo se reusa `Motor.calcularApu` / `Motor.consolidar`).

## Estado de cierre

**PLANNED / READY — 2026-08-31.** El ejecutor actualiza esta sección al
término. Resultado esperado: «DONE (YYYY-MM-DD). Verificación dirigida
verificada: `RecalculoServiceTest` + `RecalculoServiceIT` verde;
regresión APU/motor verde; `git diff --check` limpio.» Si V008 o V001
necesitan retoques, queda «PARTIAL — V00X adicional requerida,
documentada en §STOP».

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
   V001 pero **no se calcula** (Plan 09 I-08 introducirá el cronograma).
   En este plan, `recalcular(Alcance.VERSION(...))` puede **escribir**
   `peso_ponderado` cuando exista la fila `actividad`; si no existe, la
   deja intacta.
5. **No existe `recalculo/`** en `src/main/java/ec/uce/propuestas/`.
   I-06 lo dejó explícitamente DEFERRED
   (`docs/modulos/04-apu-avanzado.md` §2.5; repetido en README del
   módulo y `estado-actual.md` §2). Este plan lo activa.
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
  - `Alcance.java` (record sealed o jerarquía de records).
  - `RecalculoService.java`.
  - `internal/VersionSnapshotBuilder.java` (package-private).
- Crear tests en `src/test/java/ec/uce/propuestas/recalculo/`:
  - `RecalculoServiceTest.java` (unit con mocks de repos cuando el
    motor se puede aislar).
  - `RecalculoServiceIT.java` (`@QuarkusTest` + Dev Services,
    integración real).
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
- **No** se introduce `recalculo` para
  `parametros_proyecto`/`%CI`/`%HM` (esa propagación ya está fuera
  del alcance cerrado por I-06; queda DEFERRED para iteraciones
  futuras — el diseño del módulo `recalculo` lo deja previsto con el
  caso `Alcance.VERSION(...)`).
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

    @Inject Motor motor;
    @Inject ApuRepository apuRepository;
    @Inject CapituloRepository capituloRepository;
    @Inject RubroRepository rubroRepository;
    @Inject PresupuestoRepository presupuestoRepository;
    @Inject internal.VersionSnapshotBuilder snapshotBuilder;

    @Transactional(Transactional.TxType.MANDATORY)
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

Helper package-privado que construye el `VersionSnapshot` que el motor
consume. Reduce el `VersionSnapshot` completo del motor al subset
necesario para el `Alcance` dado (carga sólo los `Capitulo`/`Rubro`/
`APU`/filas afectados + sus ancestros hasta la raíz).

> **Regla de disciplina:** los tests de I-07 verifican el seam por la
> API pública (`RecalculoService.recalcular(...)`). **No** se testean
> los internals directamente — `VersionSnapshotBuilder` es
> package-private y existe para hacer legible el orquestador.

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
2. **RED — `RecalculoServiceTest`** (unit con mocks cuando el motor
   es inyectable): define los tests mínimos que cubren los 3 casos del
   seam canónico (`Alcance = Version | Apu | Insumo`):
   - `recalcular_apu_sin_rubro_vinculado_actualiza_solo_el_apu`.
   - `recalcular_apu_con_rubro_propagacion_esperada`.
   - `recalcular_version_raices_coinciden_con_consolidar` (este test
     cubre por alcance de versión la propagación grano fino del
     agregado `Presupuesto → Capitulo → Rubro`, ya que las mutaciones
     estructurales de P-28/29/31 declaran `Alcance.Version(...)`).
   - `recalcular_insumo_propagates_to_herederos` (override NULL,
     N04 §A1 FORMA 2): un insumo mutado propaga a los APUs que lo
     heredan sin override y, vía APU, a su rubro y ascendentes.
3. **GREEN — `Alcance.java`** (sealed interface con 3 records:
   `Version`, `Apu`, `Insumo` — no se introducen `Capitulo` ni `Rubro`).
4. **GREEN — `RecalculoService.java`**: orquestador transaccional con
   el `switch` exhaustivo sobre `Alcance`. Cada rama:
   - `Version`: cargar `VersionSnapshot` reducido, invocar
     `Motor.consolidar(...)`, persistir `presupuesto.total` +
     `capitulo.total` recursivos + `rubro.precio_unitario` /
     `precio_total` + (si existen) `actividad.peso_ponderado`. Es
     además el alcance que cubren las mutaciones estructurales del
     agregado de P-28/29/31 (crear/editar/mover/eliminar capítulos,
     CRUD rubros, deep copy): el recorrido del árbol se hace dentro
     del orquestador de `Version`.
   - `Apu`: cargar `ApuSnapshot`, invocar `Motor.calcularApu(...)`,
     persistir `apu.costo_total` y propagar al `Rubro` vinculado
     (delegando al helper interno del motor para la regla
     workbook-consistent — **NO** se duplica la regla); el
     `RecalculoService` resuelve el `Rubro.apu_id` y reescala la
     propagación hacia el `Capitulo` y el `Presupuesto` vía el
     recorrido del árbol por alcance de versión.
   - `Insumo`: resolver los APUs que heredan el precio (override
     `NULL`), invocar `Motor.calcularApu(...)` sobre cada uno y
     propagar al rubro vinculado (N04 §A1 FORMA 2). Si la cantidad
     de APUs afectados excede un umbral razonable, el servicio
     degrada a `Alcance.Version(presupuestoId)` para reutilizar
     `Motor.consolidar` con el snapshot completo.
5. **GREEN — `internal/VersionSnapshotBuilder.java`**: builder
   package-private con un método `para(Alcance)` que retorna el
   `VersionSnapshot` mínimo. Por ahora, implementación simple: carga
   la versión completa (el caso real IESS — 33 capítulos / 298 rubros
   — es sub-segundo en memoria según §2.4 del codebase-design). Si en
   el futuro una versión excede el presupuesto, se cachea
   `ApuCalculado` por APU no afectado dentro del propio motor (sin
   cambiar la interfaz — `08-codebase-design.md` §2.4).
6. **TRIANGULATE — `RecalculoServiceIT`** (`@QuarkusTest`):
   - Crea proyecto + presupuesto + capítulo + rubro + APU; modifica
     la cantidad del rubro → `GET /presupuestos/{id}` (o equivalente
     vía repositorio) refleja el `totalGeneral` actualizado en la
     misma transacción.
   - Verifica que un fallo simulado del motor (inyectando un mock
     con excepción) hace rollback: el rubro conserva su estado
     previo (sin `precio_total` parcial).
7. **Verificación dirigida**:
   - `./gradlew test --tests 'ec.uce.propuestas.recalculo.*'
     -Dquarkus.http.test-port=0 --console=plain` → tests verdes.
   - `./gradlew test --tests 'ec.uce.propuestas.motor.*'
     -Dquarkus.http.test-port=0 --console=plain` → regresión motor
     verde (no se reabre).
   - `./gradlew test --tests 'ec.uce.propuestas.apu.*'
     -Dquarkus.http.test-port=0 --console=plain` → regresión APU
     verde.
   - `./gradlew build -x test --console=plain` →
     BUILD SUCCESSFUL.
   - `git diff --check` → sin salida.
8. **Actualizar `08-codebase-design.md` §3** cambiando
   «`recalculo` permanece DEFERRED» por «**ACTIVADO en I-07
   (2026-MM-DD), Plan 020**» y añadiendo una nota que el módulo ya
   implementa los 4 `Alcance` con su semántica. Esta edición está
   autorizada por el alcance de Plan 020 (alineamiento canónico).
9. **Commit unitario** con mensaje
   `feat(recalculo): Plan 020 activar módulo profundo write-through`.

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

# Build sin tests
./gradlew build -x test

# Conteo real
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
    build/test-results/test/TEST-ec.uce.propuestas.recalculo.*.xml

git diff --check
git status --short
```

**Resultado esperado** (cifras al cierre):

- `RecalculoServiceTest` + `RecalculoServiceIT` verdes.
- Motor, APU, presupuesto entity siguen verdes.
- `git diff --check` limpio.

---

## Criterios de terminado

- [ ] Existe `Alcance.java` (sealed interface con 3 records:
  `Version`, `Apu`, `Insumo`).
- [ ] Existe `RecalculoService.java` con `recalcular(Alcance)`
  transaccional y orquestación completa.
- [ ] Existe `internal/VersionSnapshotBuilder.java`.
- [ ] `RecalculoServiceTest` + `RecalculoServiceIT` verdes (cifras
  reportadas desde `build/test-results/test/`).
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
- Aparece un test que requiere `Mock` del motor. **STOP** — el motor
  es inyectable pero su contrato es estable; mockearlo indica
  probable desviación. Mejor invertir el test (llamar al motor real
  con un snapshot sintético).

---

## Siguiente plan ejecutable

[`03-ciclo-presupuesto.md`](03-ciclo-presupuesto.md) (Plan 021) —
auto-create `presupuesto` v1 vigente en `POST /proyectos`,
`GET /proyectos/{id}/presupuestos` y read model
`GET /presupuestos/{id}`. Depende de 019 + 020.
