# 005 — Motor de cálculo APU: pure Java module + Golden Master tests

- **Status:** TODO
- **Iteration:** I-02 (roadmap/01, weeks 3–4) — the thesis's `exactitud_calculo` variable
- **Depends on:** 001 (Quarkus skeleton). **Does NOT depend on 002/003/004** — motor is pure Java, no framework, no DB, no auth.
- **Blocks:** every downstream plan that computes APU/presupuesto/cronograma values

---

## Context

This is the **most load-bearing plan in the backend**. The thesis's
dependent variable `exactitud_calculo` requires **0.00 deviation** from
certified reference cases. The motor is what produces those numbers.

Constraints from
`../../thesis-docs/plan/architecture/08-codebase-design.md §2`:

1. **Pure domain logic, no framework** — no CDI, no JPA, no
   `@ApplicationScoped`, no Panache, no logging framework. Just a Java
   library. Trivially unit-testable, trivially runnable in a `main()`.
2. **BigDecimal everywhere.** Never `double`. Never `float`. Scale 6.
   Explicit `RoundingMode`. Rounding to 2 dp happens **only at export**,
   never in the motor.
3. **Two grains, interface A** (`§2.3`): `calcularApu(ApuSnapshot,
   ParametrosCalculo) → ApuCalculado` for per-APU work; `consolidar(
   VersionSnapshot) → VersionCalculada` for whole-version work.
   Package-private calculators (row/HM/consolidator) exist *inside* but
   are never exposed and never tested directly — "replace, don't
   layer."
4. **Golden Masters are the acceptance test.** GM-01…GM-25 encode real
   SERCOP reference cases. Every one must pass with 0.00 tolerance. A
   regression in any GM breaks the build in CI (plan 002).

**Package placement**: `ec.uce.propuestas.motor` (top-level slice under
`src/main/java/ec/uce/propuestas/`). Public interface is the two static
entry points on a single facade class `Motor`; snapshot/result records
are also public. Everything else is package-private.

**Fixtures**: JSON files live in
`../../thesis-docs/plan/domain/_artifacts/`. Confirmed present (as of
docs commit `d7508eb`):

- `apus-sample-apus-cetro-medico-tulcan.json` (~56 KB)
- `apus-sample-expansi-n-de-alumbrado-publico.json` (~1.5 KB)
- `presupuesto-apus-cetro-medico-tulcan.json` (~90 KB)
- `presupuesto-expansi-n-de-alumbrado-publico.json` (~15 KB)
- `structure-apus-cetro-medico-tulcan.json` (~25 KB)
- `structure-expansi-n-de-alumbrado-publico.json` (~1.2 KB)
- `insumos-seed-apus-cetro-medico-tulcan.csv` (~5 KB)
- `insumos-seed-expansi-n-de-alumbrado-publico.csv` (small)

These fixtures are **read at test time** from the docs repo. Copy them
into the backend's `src/test/resources/motor/fixtures/` so the tests
don't depend on a specific docs-repo working-copy path.

## In scope

Sources under `src/main/java/ec/uce/propuestas/motor/`:

- `Motor.java` — facade, two static entry points.
- `SeccionTipo.java` — enum `{EQUIPO, MANO_OBRA, MATERIAL, TRANSPORTE}`.
- `ApuSnapshot.java`, `FilaSnapshot.java`, `ParametrosCalculo.java`,
  `ApuCalculado.java`, `FilaCalculada.java` — input/output records for
  the per-APU grain.
- `VersionSnapshot.java`, `CapituloSnapshot.java`, `RubroSnapshot.java`,
  `CronogramaSnapshot.java`, `ActividadSnapshot.java`,
  `VersionCalculada.java`, `CapituloConTotal.java`,
  `RubroConPrecio.java`, `PesoPonderado.java`, `AvancePeriodo.java` —
  input/output records for the consolidation grain.
- `internal/` (package-private):
  - `CalculadorFila.java` — computes per-row cost by block type.
  - `CalculadorHm.java` — computes the HM row.
  - `Consolidador.java` — recursive chapter totals + weighted progress.

Tests under `src/test/java/ec/uce/propuestas/motor/`:

- `MotorApuTest.java` — one `@Test` per GM-01…GM-18 + GM-22 + GM-23 +
  GM-25 (per-APU-grain tests: **21** tests).
- `MotorConsolidacionTest.java` — one `@Test` per GM-19, GM-20, GM-21,
  GM-24 (consolidation-grain tests: **4** tests).
- `MotorPropiedadesTest.java` — property-based tests (jqwik) for
  invariants: `descuento=0 → CD_ajustado == CD`; `%CI=0 → CI == 0`;
  auxiliar → `CI == 0`; associativity of block subtotals (**~5** tests).
- `Fixtures.java` — test helper that loads JSON, maps to snapshots.
- `src/test/resources/motor/fixtures/` — copies of the 8 fixture files.

## Out of scope — do NOT touch or add

- Persistence. The motor does not read or write the DB. No `Insumo`
  entity, no Panache. Snapshots come pre-built by the caller.
- REST endpoints. Wiring the motor to `/apu/*` is a later plan.
- Export (xlsx/pdf). The motor persists 6-decimal values; **the export
  layer** rounds to 2 dp. Do not touch export here.
- Recalculo (dirty-tracking, "which APUs recompute when insumo X
  changes"). That's the `recalculo` deep module, later plan.
- Live database migration for fixture-derived seed data. Only V001–V003
  (plan 003) touch migrations.
- Auxiliary APU **nesting**. Explicitly forbidden by §17 #12. Not
  supported by the motor. Attempting to nest is a validation failure at
  the REST boundary (later plan).

## Repo conventions to match

- **Package layout** per `../../thesis-docs/plan/backend/01-quarkus-backend.md §2`:
  ```
  src/main/java/ec/uce/propuestas/
    motor/            ← this plan
      internal/         (package-private)
    …
  ```
- **BigDecimal scale 6** for storage; scale 2 only for exposition
  (**not applied in the motor** — the motor never rounds to 2).
- **RoundingMode: HALF_UP** for the one place rounding is needed
  internally: **percentage multiplications**. Concretely: when the
  motor stores an intermediate that logically has scale 6 but the
  arithmetic naturally produces scale 12+, call
  `.setScale(6, HALF_UP)` at the boundary. The GM fixtures were
  built from workbooks that also round intermediates — matching
  HALF_UP against those workbooks is what gets us 0.00 deviation.
- **No mutation.** Records + immutable lists. All results are
  freshly-constructed values.
- **No I/O in main code.** Only tests read the JSON fixtures.

## Steps

### 1 — Confirm baseline

```bash
./mvnw -q -DskipTests package
```

Expected: BUILD SUCCESS.

### 2 — Add test dependencies

The motor's tests need JSON parsing and (for the property tests) jqwik.
Both are test-scope. Add to `pom.xml` under `<dependencies>`:

```xml
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>net.jqwik</groupId>
  <artifactId>jqwik</artifactId>
  <version>1.9.0</version>
  <scope>test</scope>
</dependency>
```

Jackson is transitively available through `quarkus-rest-jackson`, but
adding it explicitly at test scope keeps the motor tests
framework-independent. Rebuild: `./mvnw -q -DskipTests package`.

### 3 — Copy fixture files

Copy the 8 JSON files (and, for completeness, the two CSV files) from
`../../thesis-docs/plan/domain/_artifacts/` to
`src/test/resources/motor/fixtures/`:

```bash
mkdir -p src/test/resources/motor/fixtures
cp ../../thesis-docs/plan/domain/_artifacts/*.json src/test/resources/motor/fixtures/
cp ../../thesis-docs/plan/domain/_artifacts/*.csv  src/test/resources/motor/fixtures/
```

Add a `src/test/resources/motor/fixtures/README.md`:
> Fixtures for the motor de cálculo golden master tests. Sourced from
> `thesis-docs/plan/domain/_artifacts/` at docs commit `d7508eb`. Do not
> edit here — re-copy from source if the docs change. The reference
> values in these files are the *thesis-defining* numbers; a motor that
> disagrees with them is broken by definition.

### 4 — Write the input records

Path: `src/main/java/ec/uce/propuestas/motor/`. All types are `record`.

```java
public enum SeccionTipo { EQUIPO, MANO_OBRA, MATERIAL, TRANSPORTE }

public record FilaSnapshot(
    SeccionTipo seccion,
    boolean esHerramientaMenor,   // exactly one true per APU snapshot, in EQUIPO
    BigDecimal cantidad,           // null iff esHerramientaMenor
    BigDecimal rendimiento,        // null for MATERIAL/TRANSPORTE and for HM
    BigDecimal precioInsumo,       // null iff esHerramientaMenor
    BigDecimal overridePrecio,     // nullable; when non-null, overrides precioInsumo
    BigDecimal cdAuxiliar          // non-null iff row is an auxiliar reference
) {
    public FilaSnapshot {
        // Only sanity: seccion != null, seccion == EQUIPO if esHerramientaMenor.
    }
}

public record ApuSnapshot(
    String codigo,
    boolean esAuxiliar,
    List<FilaSnapshot> filas       // in original workbook order
) {}

public record ParametrosCalculo(
    BigDecimal porcentajeHerramientaMenor,   // scale 4, e.g. 0.0500
    BigDecimal porcentajeIndirectoDefault,   // scale 4, from proyecto; null means "no default"
    BigDecimal porcentajeIndirectoApu,       // scale 4, from apu; null means "inherit"
    BigDecimal porcentajeDescuento           // scale 4, e.g. 0.1000 for 10%
) {}
```

Output records:

```java
public record FilaCalculada(
    SeccionTipo seccion,
    boolean esHerramientaMenor,
    BigDecimal cantidad,
    BigDecimal rendimiento,
    BigDecimal precioUnitarioEfectivo,   // COALESCE(override, precioInsumo, cdAuxiliar)
    BigDecimal costoHora,                 // EQUIPO/MO only: cantidad × precio
    BigDecimal costoFila                  // final row cost per §16
) {}

public record ApuCalculado(
    String codigo,
    boolean esAuxiliar,
    List<FilaCalculada> filas,
    BigDecimal subtotalM,     // EQUIPO block including HM
    BigDecimal subtotalN,     // MANO_OBRA
    BigDecimal subtotalO,     // MATERIAL + auxiliares
    BigDecimal subtotalP,     // TRANSPORTE
    BigDecimal costoHm,       // the HM row's costoFila (also included in subtotalM)
    BigDecimal costoDirecto,  // = M + N + O + P
    BigDecimal costoDirectoAjustado,  // = CD × (1 − descuento)
    BigDecimal costoIndirecto,        // = CD_ajustado × %CI efectivo; 0 if auxiliar
    BigDecimal costoTotal             // = CD_ajustado + CI
) {}
```

### 5 — Write `Motor.calcularApu`

Formulas from `../../thesis-docs/plan/domain/02-data-model.md §16`,
verbatim:

- For EQUIPO row (not HM): `costoHora = cantidad × precio`;
  `costoFila = costoHora × rendimiento`.
- For MANO_OBRA row: same as EQUIPO (`costoHora = cantidad × jornal`;
  `costoFila = costoHora × rendimiento`).
- For MATERIAL row referencing an insumo:
  `costoFila = cantidad × COALESCE(override, precioInsumo)`.
- For MATERIAL row referencing an auxiliar (`cdAuxiliar != null`):
  `costoFila = cantidad × cdAuxiliar`.
- For TRANSPORTE row: `costoFila = cantidad × precio`.
- For HM row: `costoFila = %HM × Subtotal_N`. HM contributes to
  `subtotalM`.
- **Computation order** (§16, §17 #9): compute N first (so HM has
  something to multiply); then M with HM prepended (the HM row is
  the first row of block M); then O, then P.
- `CD = M + N + O + P`.
- `CD_ajustado = CD × (1 − porcentajeDescuento)`.
- Effective `%CI = porcentajeIndirectoApu` if non-null, else
  `porcentajeIndirectoDefault`, else `BigDecimal.ZERO`.
- `CI = 0` if `esAuxiliar`, else `CD_ajustado × %CI`.
- `CT = CD_ajustado + CI`.

Skeleton (with `.setScale(6, HALF_UP)` at each multiplicative step):

```java
public final class Motor {
    private Motor() {}

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);
    private static final int SCALE = 6;

    public static ApuCalculado calcularApu(ApuSnapshot in, ParametrosCalculo p) {
        // 1) partition filas by seccion; separate HM row.
        // 2) compute filas N, then M (HM first), then O, then P.
        // 3) subtotals, CD, CD_ajustado, CI, CT.
        // 4) build ApuCalculado record.
    }

    public static VersionCalculada consolidar(VersionSnapshot v) { … }
}
```

Concrete row-cost helper (illustrative):

```java
static BigDecimal costoFilaMaterial(BigDecimal cantidad, BigDecimal override,
                                     BigDecimal precioInsumo, BigDecimal cdAuxiliar) {
    BigDecimal price = cdAuxiliar != null ? cdAuxiliar
                     : (override != null ? override : precioInsumo);
    return cantidad.multiply(price, MC).setScale(SCALE, RoundingMode.HALF_UP);
}
```

**Do not** use `BigDecimal.ZERO` shortcuts like `if (percent.compareTo(ZERO) == 0)` to skip work — the point is to be arithmetically correct and match reference workbooks; short-circuits invite drift.

### 6 — Write the consolidation records + `Motor.consolidar`

```java
public record RubroSnapshot(String codigo, BigDecimal cantidad, ApuSnapshot apu) {}

public record CapituloSnapshot(
    String item,
    String descripcion,
    int depth,                                   // 1 = root
    List<CapituloSnapshot> subcapitulos,          // recursive
    List<RubroSnapshot> rubros                   // leaves at any depth
) {}

public record ActividadSnapshot(
    String rubroCodigo,
    Map<Integer, BigDecimal> avancePorPeriodo    // periodo → avance %
) {}

public record CronogramaSnapshot(
    int numeroPeriodos,
    List<ActividadSnapshot> actividades
) {}

public record VersionSnapshot(
    ParametrosCalculo parametrosProyecto,
    List<CapituloSnapshot> capitulosRaiz,
    CronogramaSnapshot cronograma                // nullable if version has no schedule yet
) {}

public record RubroConPrecio(String codigo, BigDecimal cantidad,
    BigDecimal precioUnitario, BigDecimal precioTotal) {}

public record CapituloConTotal(String item, int depth, BigDecimal total) {}

public record PesoPonderado(String rubroCodigo, BigDecimal pesoPct) {}
public record AvancePeriodo(int periodo, BigDecimal avanceAcumuladoPct) {}

public record VersionCalculada(
    Map<String, ApuCalculado> apus,               // por codigo, auxiliares primero
    List<RubroConPrecio> rubros,
    List<CapituloConTotal> capitulos,             // in-order flat, with depth
    BigDecimal totalGeneral,
    List<PesoPonderado> pesosPonderados,
    List<AvancePeriodo> avancesAcumulados         // one per periodo
) {}
```

Order of operations in `consolidar`:
1. Topologically sort APUs so **auxiliares first** — an APU referencing
   an auxiliar needs the auxiliar's CD in its `FilaSnapshot.cdAuxiliar`
   field. Snapshots provide that, but the traversal still computes
   auxiliares first as a sanity contract.
2. Compute every APU (via `calcularApu`).
3. For each rubro, compute
   `precioTotal = cantidad × apu.costoTotal` at scale 6.
4. For each capitulo, recursively sum children (rubros + sub-capitulos).
5. `totalGeneral = Σ root chapters`.
6. For each rubro, `pesoPonderado = precioTotal / totalGeneral × 100`
   at scale 4 (matches DB `NUMERIC(7,4)`).
7. If `cronograma != null`, compute `avanceAcumulado` per periodo
   summing across all activities: `avancePorPeriodo[t]` where the
   invariant `Σ avancePorPeriodo == pesoPonderado` should already hold
   (validation at REST boundary), but the motor just sums them.

### 7 — Write `Fixtures.java` test helper

Path: `src/test/java/ec/uce/propuestas/motor/Fixtures.java`. This is the
one non-obvious piece — the JSON structure in
`_artifacts/*.json` has to be mapped to snapshots.

```java
final class Fixtures {
    private static final ObjectMapper M = new ObjectMapper();

    static JsonNode loadJson(String name) throws IOException {
        try (var in = Fixtures.class.getResourceAsStream("/motor/fixtures/" + name)) {
            if (in == null) throw new FileNotFoundException(name);
            return M.readTree(in);
        }
    }

    static ApuSnapshot apuFromJson(JsonNode apuNode) { … }
    static VersionSnapshot versionFromJson(String presupuestoFile, String apusFile) { … }
    static Map<String, BigDecimal> expectedCTsByCodigo(JsonNode apusArray) { … }
    // Each helper mirrors the JSON shape observed in the fixture files.
    // Executor: OPEN a fixture and inspect the top-level keys before
    // writing this — don't guess the shape.
}
```

**Executor guidance:** before writing `apuFromJson`, open
`src/test/resources/motor/fixtures/apus-sample-apus-cetro-medico-tulcan.json`
and note the actual field names used (e.g., is it `costoTotal` or
`costo_total`? are sections nested arrays or flat rows tagged with a
`seccion` key?). The subagent-provided spec described these as
`costoTotal`, but confirm by reading the file. If the field names
disagree with the subagent's summary, follow the file — the file is
authoritative.

### 8 — Write per-APU tests (GM-01…GM-18, GM-22, GM-23, GM-25)

`MotorApuTest.java`:

```java
class MotorApuTest {

    @Test
    void GM_01_501BM6() {
        var node = Fixtures.loadJson("apus-sample-apus-cetro-medico-tulcan.json");
        var apuNode = findByCodigo(node, "501BM6");
        ApuSnapshot snap = Fixtures.apuFromJson(apuNode);
        ParametrosCalculo p = new ParametrosCalculo(
            new BigDecimal("0.0500"),
            new BigDecimal("0.1800"),   // 18% default from workbook
            null,                        // no per-apu override
            BigDecimal.ZERO);
        ApuCalculado out = Motor.calcularApu(snap, p);

        assertEquals(0, new BigDecimal("4.690875").compareTo(out.costoDirecto()));
        assertEquals(0, new BigDecimal("5.5352325").compareTo(out.costoTotal()));
    }
    // GM_02…GM_18 follow the same pattern with the codes from step 6.
}
```

**Expected values are taken from the fixture JSON, not from the plan
text.** The plan text has GM-01…GM-18 values as guidance, but the
authoritative source is the fixture file. If the fixture disagrees
with the plan text values, use the fixture — the fixture is what the
thesis will be measured against.

**GM-22 — HM formula spot-check** on 501BM6:
- Expected: with subtotal_N = 4.4675 (from workbook), HM = 0.05 ×
  4.4675 = 0.223375. Assert `out.costoHm().compareTo(new
  BigDecimal("0.223375")) == 0`.

**GM-23 — v1.1 Annex C.7** (vinyl dissipator APU, code 501062):
- Build the snapshot **by hand from the annex text** (not a fixture) —
  see `../../thesis-docs/res/docs/requirements/v1.1-functional-requirements.md`
  Annex C.7. Expected: HM 0.45, CD 61.39, CI 11.05, CT 72.44. **These
  are the workbook's 2-dp rounded values;** the motor's 6-dp results
  should round to them. Assert with `HALF_UP` at scale 2.

**GM-25 — precision / rounding spot-check**:
- Take GM-01's CT = 5.5352325. Assert that
  `out.costoTotal().setScale(2, HALF_UP).compareTo(new
  BigDecimal("5.54")) == 0`. This is the ONE place we round to 2 dp,
  and only in a test — to verify export math against workbook display.

### 9 — Write consolidation tests (GM-19, GM-20, GM-21, GM-24)

`MotorConsolidacionTest.java`:

- **GM-19**: build a `VersionSnapshot` from
  `presupuesto-apus-cetro-medico-tulcan.json` + the corresponding APUs;
  call `Motor.consolidar`; assert `totalGeneral.setScale(2, HALF_UP)`
  equals the workbook's TOTAL (`$395,115.32`).
- **GM-20**: same version; assert the 7 root chapter totals (values in
  the subagent spec; confirm from fixture).
- **GM-21**: cross-check each `rubro.precioUnitario` (rounded 2 dp) vs
  `apu.costoTotal` (rounded 2 dp) per rubro. Six documented sub-cent
  mismatches (workbook manual-rounding artifacts) — allow those via a
  **hardcoded allowlist** of `(rubro codigo, delta)` pairs. Do NOT
  loosen the tolerance globally — the allowlist has 6 entries and
  they're logged in the domain doc.
- **GM-24**: same shape as GM-19 but against the EMELNORTE fixture.
  Fewer APU details; primarily a hierarchy roll-up test.

### 10 — Write property tests (jqwik)

`MotorPropiedadesTest.java`:

```java
@Property
void descuento_cero_no_cambia_CD(@ForAll("apuSnapshots") ApuSnapshot snap) {
    var p0 = new ParametrosCalculo(new BigDecimal("0.0500"),
        new BigDecimal("0.1800"), null, BigDecimal.ZERO);
    var pDisc = new ParametrosCalculo(new BigDecimal("0.0500"),
        new BigDecimal("0.1800"), null, new BigDecimal("0.1000"));
    // Descuento 0 vs 10%: CD same, CD_ajustado different, CI different.
    assertEquals(0, Motor.calcularApu(snap, p0).costoDirecto().compareTo(
        Motor.calcularApu(snap, pDisc).costoDirecto()));
}

@Property
void ci_cero_hace_CT_igual_a_CDajustado(@ForAll("apuSnapshots") ApuSnapshot snap) { … }

@Property
void auxiliar_tiene_CI_cero(@ForAll("apuSnapshotsAuxiliares") ApuSnapshot aux) { … }

@Property
void subtotales_no_negativos(@ForAll("apuSnapshots") ApuSnapshot snap) { … }
```

`@Provide` method(s) generate `ApuSnapshot` instances with bounded
BigDecimals. Do not go crazy on generator complexity — 100 shrunk
examples per property is plenty. If jqwik integration proves fussy,
these can degrade to hand-written `@ParameterizedTest` cases — the GM
tests are the hard gate.

### 11 — Verify

```bash
./mvnw -q -DskipTests package
# expected: BUILD SUCCESS

./mvnw -q test
# expected: BUILD SUCCESS, ≥25 tests run (21 apu + 4 consolidation + ~5 property)
```

Any GM test failure → STOP; investigate. Do not "adjust the expected
value" — the fixture is authoritative. A failure means the motor is
wrong (or, less likely, the fixture-to-snapshot mapping in `Fixtures.java`
is wrong; check the JSON structure first).

## Done criteria

- [ ] `src/main/java/ec/uce/propuestas/motor/Motor.java` exists with
  `public static ApuCalculado calcularApu(ApuSnapshot, ParametrosCalculo)`
  and `public static VersionCalculada consolidar(VersionSnapshot)`.
- [ ] All input/output records exist under `motor/`; `internal/`
  package-private calculators exist.
- [ ] **No** framework annotations (`@ApplicationScoped`, `@Inject`,
  `@Entity`, etc.) anywhere under `motor/`.
- [ ] `grep -r 'double\|float' src/main/java/ec/uce/propuestas/motor/`
  returns zero hits (no double / float primitives; BigDecimal only).
- [ ] `src/test/resources/motor/fixtures/` contains the 8 JSON + 2 CSV
  fixture files.
- [ ] `./mvnw -q -DskipTests package` → BUILD SUCCESS.
- [ ] `./mvnw -q test` → BUILD SUCCESS with **all** GM-01…GM-25 tests
  green (25 golden masters; property tests may add ~5 more).
- [ ] Zero test failures.

## Test plan

- 21 `@Test` methods in `MotorApuTest` (GM-01…GM-18, GM-22, GM-23,
  GM-25).
- 4 `@Test` methods in `MotorConsolidacionTest` (GM-19, GM-20, GM-21,
  GM-24).
- ~5 `@Property` methods in `MotorPropiedadesTest`.

Naming convention: `GM_<NN>_<hint>` (e.g. `GM_01_501BM6`,
`GM_20_totales_capitulos_raiz`). This makes failures instantly
locatable in the thesis's evidence bundle.

**Regression rule**: no test may lower an expected value to make the
motor pass. If a test needs to change, the change is a domain decision
and must be traceable to a docs edit under `thesis-docs/plan/domain/`.

**Coverage note**: no need for JaCoCo. The GM tests cover the motor's
public interface by construction — every code path visible to a caller
runs on real data.

## Maintenance note

- **When SERCOP norms change** (e.g. new %HM band, new indirect-cost
  formula): the change lives in **input data** (config, DB) — the
  motor's code should almost never move. If it must, treat every GM
  test regression as a signal to first ask "did the norm change?"
  before "did the motor break?"
- **Adding a new golden master**: (i) add its fixture JSON under
  `src/test/resources/motor/fixtures/` (with a docstring pointing at
  the source workbook); (ii) add one `@Test` in `MotorApuTest` or
  `MotorConsolidacionTest`. Do not modify existing tests to
  "accommodate" the new case — each GM stands alone.
- **Rounding hazards**: if you ever see a GM fail by a rounding cent,
  do not "tighten tolerance" or switch to `HALF_EVEN`. The workbooks
  are `HALF_UP`. Match them. If you find a genuine algebraic issue,
  the fix is a code change with a corresponding docs update
  under `domain/02-data-model.md §16`.
- **Native-image compat**: this module uses no reflection, no JNI, no
  dynamic proxies. It should GraalVM-native-image cleanly out of the
  box. If it doesn't, that's a real bug — do not add
  `@RegisterForReflection`.

## Escape hatches — STOP conditions

- The JSON fixture structure doesn't match the shape assumed in step 7
  (e.g. field names in Spanish snake_case where the plan expected
  camelCase) → STOP. Follow the file's shape; report the divergence in
  NOTES; adjust `Fixtures.java` (not the motor).
- Any test in `MotorApuTest` or `MotorConsolidacionTest` requires
  `tolerance > 0.00` to pass (other than the documented 6-entry
  workbook-rounding allowlist in GM-21) → STOP. That's a real bug.
- Any code path in the motor references a Quarkus / CDI /
  framework annotation → STOP. Motor is pure.
- Any code path uses `double`, `float`, `Math.round`, or
  `String.format` with a `%f` on a monetary value → STOP. BigDecimal
  only.
- Any step wants you to import from `ec.uce.propuestas.usuario` or any
  other slice → STOP. The motor has no dependencies on other slices.
- The Annex C.7 spot values for GM-23 disagree with the motor's
  computed rounded values by more than 0.01 in any component (HM, CD,
  CI, CT) → STOP. Report the four values side by side. Do not adjust
  the assertion.
