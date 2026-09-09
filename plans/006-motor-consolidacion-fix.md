# 006 — Motor consolidación fix: GM-19 & GM-20, GM-21 allowlist audit, GM-24 real impl

- **Status (2026-08-28 — cierre parcial USER-DECIDED):**
  **IMPLEMENTACIÓN APLICADA workbook-consistent (2026-08-28) — PARTIAL — CLOSED WITH DOCUMENTED IESS RESIDUAL.**
  La regla workbook-consistent de la frontera APU→Rubro quedó **aplicada** en `internal/Consolidador.java` (`precioUnitario = apu.costoTotal.setScale(2, DOWN)`; `precioTotal = cantidad × PU_2dp` retenido a escala 6 `HALF_UP`; agregación de totales de capítulo y `totalGeneral` desde esos `precioTotal` a escala 6; display canónico a 2 dp `HALF_UP` solo en presentación/assertion). `ConsolidadorFronteraTest` 5/5 verde; `MotorApuTest` 21/21 verde; `MotorPropiedadesTest` 5/5 verde. GM-21 sigue verde con allowlist de **11 entradas ≤ 0.03 a nivel PU** (atribuido a artefactos de redondeo manual del example workbook IESS).
  **Residuo aceptado por el autor:** GM-19 actual `395108.37` vs esperado `395115.32` (delta `-$6.95`); GM-20 cap. 1 actual `158907.21` vs esperado `158908.05` (delta `-$0.84`). **No** se reabre el motor, **no** se cambian workbook, golden expected values, tolerancias ni fórmulas adicionales para cerrar este residual (preferencia del usuario: un único example workbook, no exhaustive per-rubro audit). Plan 014 queda con T2–T4 OPEN; este plan se reconcilia con el cierre parcial de Plan 02 ([`../docs/modulos/planes-para-estar-al-dia/02-motor-precision-y-consolidacion.md`](../docs/modulos/planes-para-estar-al-dia/02-motor-precision-y-consolidacion.md), §6 Residual IESS).
  Decisión funcional cerrada (2026-08-19, N04-bis Kevin Andrade); corrección workbook-consistent (2026-08-28); implementación de código aplicada (2026-08-28); investigación histórica conservada debajo.
- **Decisión funcional aprobada:** **Opción (a) workbook-consistent
  (corrección 2026-08-28)** — modificar `internal/Consolidador.java` para
  aplicar, en la frontera APU→Rubro:
  - `RubroConPrecio.precioUnitario = APU.costoTotal.setScale(2,
    RoundingMode.DOWN)` (única aplicación de `DOWN`; reproduce el
    workbook IESS, que tipea el precio unitario a 2 dp).
  - `RubroConPrecio.precioTotal = cantidad × precioUnitario`,
    retenido a la escala de persistencia 6 (`NUMERIC(14,6)`) con
    `HALF_UP` aplicado **únicamente** en esa frontera de resultado
    (sin truncar cada `precioTotal` a 2 dp). El workbook IESS multiplica
    `cantidad × PU_2dp` a precisión completa y suma antes de presentar a
    2 dp.
  - **Totales de capítulo y `totalGeneral`** agregan esos `precioTotal`
    a escala 6; su display/assertion canónica es a 2 dp `HALF_UP`.
  Rounding mode en `precioUnitario`: `RoundingMode.DOWN` (match Excel
  ROUNDDOWN / TRUNCATE que usa el workbook IESS).
  Esta es **la única rounding del motor** tras la decisión
  Plan 014 (2026-08-28) que retiró `CALC_PRECISION=3 HALF_UP` por
  operación: el motor opera con la precisión natural de `BigDecimal`,
  la frontera APU→Rubro cierra `precioUnitario` a 2 dp `DOWN` y deja
  `precioTotal` a escala 6 `HALF_UP`, y el display se rige
  por la config global `precisionDinero=2 / precisionPorcentaje=4`.
- **Iteration:** I-02 (roadmap/01, semana 4) — implementación pendiente
  (autorizada por [Plan 014](./014-motor-precision-no-links.md), 2026-08-28)
- **Depends on:** 005 (motor scaffold present in `2fe6c83`)
- **Blocks (histórico):** the I-02 hito "GM api verdes" — cierre parcial USER-DECIDED 2026-08-28: `exactitud_calculo` queda **defendible con excepción documentada** (regla workbook-consistent aplicada; GM-19 `-$6.95` y GM-20 cap. 1 `-$0.84` aceptados; no exhaustive per-rubro audit).
- **CLAUDE.md override:** la regla "Do not touch `Motor.java` or `internal/Consolidador.java` until the director decides" se levanta **para esta decisión específica**, justificada por cambio de requerimientos funcionales (N04-bis 2026-08-19). Documentado en `thesis-back-quarkus/CLAUDE.md` y `thesis-docs/CLAUDE.md`.

---

## Context

Plan 005 executed. Its scaffold works: 21/21 per-APU golden masters green
(`MotorApuTest`) and all 5 property tests green (`MotorPropiedadesTest`).
But 2/4 consolidation goldens fail:

- **GM-19** `MotorConsolidacionTest:53` — `totalGeneral` (2dp) expected
  `395115.32`, actual `395112.82`. Delta **−$2.50** across 298 rubros
  (< 1¢/rubro).
- **GM-20** `MotorConsolidacionTest:90` — root chapter "1" total
  expected `158908.05`, actual `158909.20`. Delta **+$1.15**.

Reproduce with:

```bash
./gradlew test --tests 'ec.uce.propuestas.motor.MotorConsolidacionTest' --console=plain
```

**Diagnosis** (already done, do not redo — just confirm by reading the
code):

`Fixtures.versionFromJson` at `src/test/java/ec/uce/propuestas/motor/Fixtures.java:216`
creates a **stub APU** for rubros that don't have a full sample APU in
`apus-sample-apus-cetro-medico-tulcan.json` (280 of 298 rubros). The stub
uses the presupuesto's `precioUnitario` (a **2dp-rounded** workbook
display value) as the stub APU's `costoTotal`. The workbook then computes
each row's `precioTotal = cantidad × precioUnitario_at_full_precision`.
Because the fixture stub loses precision, `motor.consolidar` computes
`cantidad × precioUnitario_2dp` and drifts.

**This is a `Fixtures.java` bug, not a `Motor` bug.** Every per-APU GM
is green; `Motor.calcularApu`'s math is right. The Motor's contract is
"consume snapshots, do BigDecimal math, produce results." The fixture
wiring is feeding it lossy snapshots.

Two additional pre-existing issues in the committed code that this plan
also closes:

1. **GM-21 allowlist has 11 entries; the plan spec called for 6.** Either
   the workbook has more manual-rounding artifacts than plan 005 expected
   (fine — the plan is guidance), or some of those 11 rubros are covering
   a genuine motor bug. Must be audited entry by entry.
2. **GM-24 is a no-op** (`MotorConsolidacionTest:190-194`, body =
   `assertTrue(true, ...)`). The executor of plan 005 documented this
   as "SKIPPED — EMELNORTE fixture unusable" but the file it references
   (`apus-sample-expansi-n-de-alumbrado-publico.json`) needs to be
   inspected fresh: if it truly has no APU line data, the STOP condition
   holds and we cannot implement GM-24 without upstream fixture repair;
   if inspection turns out the fixture is usable, GM-24 must be
   implemented properly.

## In scope

- `src/test/java/ec/uce/propuestas/motor/Fixtures.java` — fix the stub
  precision (§1 below).
- `src/test/java/ec/uce/propuestas/motor/MotorConsolidacionTest.java` —
  audit the 11 GM-21 allowlist entries and either keep, remove, or move
  to a genuine "motor bug" list (§3). Replace or delete the GM-24 stub
  (§4).

Optionally (only if §1's stub fix is insufficient):
- `src/main/java/ec/uce/propuestas/motor/internal/Consolidador.java` —
  if a second-order bug shows up in `Consolidador` after the stub fix,
  address it here. Do NOT touch this file without a diagnostic dump
  showing which node diverges (§2).

## Out of scope — do NOT touch or add

- **Any file under `src/main/java/ec/uce/propuestas/motor/*.java`
  except `internal/Consolidador.java`.** Per-APU math is green; do not
  "improve" it.
- The 21 per-APU tests in `MotorApuTest`. Do not modify their
  assertions.
- The 5 property tests in `MotorPropiedadesTest`.
- `MotorApuTest.GM_23_annex_c7_vinyl_dissipator` and
  `MotorApuTest.GM_25_precision_rounding` — hand-crafted, not touched by
  this bug.
- The upstream fixture JSON files under
  `src/test/resources/motor/fixtures/`. They are frozen copies of
  `thesis-docs/plan/domain/_artifacts/`. If a fixture appears wrong,
  that's an upstream `thesis-docs` bug — this plan STOPs.
- The `pom.xml`.
- Any Java outside `src/test/java/ec/uce/propuestas/motor/`.
- Every rule from plan 005 still applies: BigDecimal only, no framework,
  no double/float, no adjustment of expected values.

## Repo conventions to match

Same as plan 005:

- `BigDecimal` only. `grep -r 'double\|float' src/main/java/ec/uce/propuestas/motor/`
  must return zero hits.
- `BigDecimal` arithmetic at natural scale; **no intermediate rounding**
  (post-2026-08-28 supersede of `CALC_PRECISION=3 HALF_UP` — see
  `plans/014-motor-precision-no-links.md`). `MathContext(20,
  RoundingMode.HALF_UP)` is **not** used anywhere in `motor/` after
  Plan 014 lands; the per-APU math is full-precision BigDecimal. The
  only rounding in the motor is the APU→Rubro border in
  `internal/Consolidador.java` with `RoundingMode.DOWN` to 2 dp.
- Two-dp rounding only at export/assertion time, not in the motor.
- No framework annotations under `motor/`.

## Steps

### 1 — Implementar opción (a) workbook-consistent: frontera APU→Rubro en `Consolidador.java`

Path: `src/main/java/ec/uce/propuestas/motor/internal/Consolidador.java`.

**1a** — Localizar el método que construye `RubroConPrecio` a partir del
APU. Normalmente en `consolidar()` (o un helper llamado desde ahí).
Identificar la línea donde se asigna `precioUnitario = apu.costoTotal`.

**1b** — Aplicar el redondeo a 2 dp **antes** de construir el Rubro:

```java
// Antes (motor 005):
// rubro.precioUnitario = apu.costoTotal;

// Después (006 opción a — corregido por Plan 014 / workbook-consistent
// 2026-08-28; la versión previa con `setScale(2, DOWN)` simétrico en PU y
// PT quedaba retirada por deltas sistemáticos GM19 -$9.37 y GM20 cap1
// -$3.09; ver `plans/014-motor-precision-no-links.md` STOP conditions):
//
// - precioUnitario: redondeo DOWN 2dp (match workbook IESS; precio
//   unitario tipeado a 2dp).
// - precioTotal: cantidad × PU_2dp retenido a la escala de persistencia 6
//   (`NUMERIC(14,6)`) con HALF_UP **únicamente** en esa frontera de
//   resultado. **No** se trunca cada precioTotal a 2dp — el workbook
//   IESS multiplica a precisión completa y suma antes de presentar.
rubro.precioUnitario = apu.costoTotal.setScale(2, RoundingMode.DOWN);

// precioTotal a escala 6 con HALF_UP en la frontera (workbook-consistent)
rubro.precioTotal = cantidad.multiply(rubro.precioUnitario)
                       .setScale(6, RoundingMode.HALF_UP);
// totales de capítulo y totalGeneral agregan esos precioTotal a escala 6;
// display/assertion canónica a 2dp HALF_UP ocurre en la capa de
// presentación, no por rubro.
```

**1c** — Imports: añadir `import java.math.RoundingMode;` si no está.

**1d** — En el alcance de *este* plan (006) NO se tocan `Motor.java` ni
`internal/CalculadorFila.java` (siguen operando a la precisión natural de
`BigDecimal`; post-Plan 014 ya no aplican `CALC_PRECISION=3 HALF_UP` por
operación). *Nota (2026-08-28):* [Plan 014](./014-motor-precision-no-links.md)
sí autoriza en esos dos archivos **ediciones estructurales mínimas** —
borrar las ramas `esAuxiliar`/`cdAuxiliar` y leer el `%CI` desde
`ApuSnapshot.porcentajeIndirecto` — **sin cambio aritmético**.

### 1.1 — Actualizar los tests existentes (workbook-consistent; cierre parcial 2026-08-28)

Estado real tras la implementación workbook-consistent:

- **GM-19** `MotorConsolidacionTest.GM_19_total_general_tulcan` —
  actual `395108.37` vs workbook esperado `395115.32` → **delta `-$6.95`**
  (sub-céntimo acumulado en 298 rubros). **Aceptado** por el autor; el
  motor **no se modifica más** para cerrar este residual.
- **GM-20** `MotorConsolidacionTest.GM_20_totales_capitulos_raiz_tulcan` —
  el resto de capítulos raíz cierra a delta 0.00; el capítulo `1`
  reproduce `158907.21` vs workbook `158908.05` → **delta `-$0.84`**.
  **Aceptado** por el autor.
- **GM-21** allowlist: las 11 entradas sobreviven con delta ≤ 0.03 a
  nivel PU, atribuidas a artefactos de redondeo manual del workbook (one
  example workbook, no exhaustive per-rubro audit — preferencia del
  usuario).

**Evidencia registrada en STOP conditions de Plan 014 (2026-08-28):** la
versión previa de Plan 006 con `setScale(2, DOWN)` simétrico en PU y PT
producía `GM19 = 395105.95` (delta `-$9.37`) y `GM20 cap1 = 158904.96`
(delta `-$3.09`). Esos deltas sistemáticos son los que motivaron la
corrección workbook-consistent.

### 2 — Verificación post-cambio

Path: `src/test/java/ec/uce/propuestas/motor/Fixtures.java`. Two edits.

**1a — Compute the stub CT at full precision** using the fixture's own
`precioTotal / cantidad`, not `precioUnitario` (which is 2dp-rounded).

Locate `versionFromJson`'s rubro branch (currently around line 200–222 —
`else if ("rubro".equals(kind))`). Just before the stub construction
(line 216, `apu = stubApuFromPrecioUnitario(codigo, precioUnitario);`),
extract the fixture's `precioTotal` too:

```java
// Prefer the fixture's precioTotal (full precision) over the 2dp precioUnitario
// so the stub APU's CT reproduces the workbook's row total exactly.
BigDecimal precioTotal = bigDecimalOrNull(row, "precioTotal");
BigDecimal stubCT;
if (precioTotal != null && cantidad.compareTo(BigDecimal.ZERO) != 0) {
    stubCT = precioTotal.divide(cantidad, 6, RoundingMode.HALF_UP);
} else {
    stubCT = precioUnitario;
}
apu = stubApuFromPrecioUnitario(codigo, stubCT);
```

**Rationale:** the fixture's `precioTotal` field is already double-precision
from the workbook computation. `stubCT = precioTotal / cantidad` at
scale 6 preserves the full precision. The Motor then computes
`cantidad × stubCT = precioTotal` up to a rounding cent, and the sum
matches the workbook TOTAL exactly.

**Why not use `precioTotal` directly**: the Motor's contract is per-APU
CT × cantidad. Bypassing that would defeat the point of testing
consolidation. Preserving CT at full precision is the honest fix.

**1b — Import `RoundingMode`** at the top of `Fixtures.java` if it's
not already imported (it may be; check the existing imports).

### 2 — Re-run and verify

```bash
./gradlew test --tests 'ec.uce.propuestas.motor.MotorConsolidacionTest'
```

**Expected (post-cambio opción a) — cierre parcial 2026-08-28:**
- GM-19 actual `395108.37` vs workbook `395115.32` (delta `-$6.95`).
  **Residual aceptado** (ver §1.1 arriba). **No** se reabre el motor.
- GM-20: 6 capítulos raíz cierran a delta 0.00; cap. 1 actual `158907.21`
  vs workbook `158908.05` (delta `-$0.84`). **Residual aceptado.**
- GM-21 allowlist: 11 entradas ≤ 0.03 sobreviven (atribuidas a artefactos
  de redondeo manual del workbook).

### 3 — Audit the GM-21 allowlist

`MotorConsolidacionTest.java:127-137` has 11 allowlist entries claiming
workbook manual-rounding artifacts. Plan 005's spec anticipated 6. The
extra 5 need to be attributed either to:
- **Genuine workbook artifact**: keep in allowlist with a source comment
  citing the workbook row.
- **Motor bug uncovered by the stub fix**: after §1's fix, re-run GM-21;
  if any of the current 11 allowlist entries no longer needs a delta
  (motor now matches workbook exactly), REMOVE that entry. That's a
  free correctness win.

Procedure:

**3a** — Re-run just GM-21 after §1:
```bash
./gradlew test --tests 'ec.uce.propuestas.motor.MotorConsolidacionTest.GM_21_rubro_precioUnitario_vs_apu_costoTotal' --console=plain
```

**3b** — For each currently-allowlisted rubro (11 codes: `501BM6`,
`501D1V`, `501DQR`, `501D00`, `502897`, `500ASU`, `502ARV`, `503B30`,
`501DH5`, `505APQ`, `500C2S`), temporarily set its allowlist delta to
`0.00` (delete the entry and let the assertion fail if there's actually
a mismatch). Re-run. Rebuild the allowlist keeping only entries that
STILL fail with delta ≠ 0.

**3c** — For each surviving allowlist entry, add a line comment noting:
> `// Workbook row N: sample-file CT = X.YYYYYY (6dp); workbook display
> puFixture = Y.YY (2dp, half-up). Delta = |CT_2dp − puFixture| = 0.0Z.
> Documented artifact.`

If the surviving list still has more than 6 entries: acceptable, but
add a top-of-file comment saying "plan 005's estimate of 6 was
approximate; workbook has more rounding artifacts than the domain doc
listed. Consider updating `thesis-docs/plan/domain/02-data-model.md
§16` to reference the actual count if the discrepancy matters for the
thesis defense."

### 4 — Address GM-24

`MotorConsolidacionTest.java:190-194` currently reads:

```java
@Test
void GM_24_SKIPPED_alumbrado_fixture_unusable() {
    assertTrue(true, "GM-24 placeholder — see NOTES for STOP condition");
}
```

**4a — Inspect the EMELNORTE fixtures**:
```bash
head -20 src/test/resources/motor/fixtures/apus-sample-expansi-n-de-alumbrado-publico.json
head -20 src/test/resources/motor/fixtures/presupuesto-expansi-n-de-alumbrado-publico.json
```

If the sample APU file has all `secciones` arrays empty and all `codigo`
fields null (as the current test comment claims), or the presupuesto
has `precioUnitario` values that are IDs rather than prices, the STOP
holds. Keep the test but rewrite it as an explicit `@Disabled`:

```java
import org.junit.jupiter.api.Disabled;

@Test
@Disabled("EMELNORTE fixture has no APU line data — upstream fixture bug "
        + "in thesis-docs/plan/domain/_artifacts/. GM-24 will be enabled "
        + "when the fixture is repaired.")
void GM_24_totales_recursivos_emelnorte_alumbrado() {
    // Placeholder — see @Disabled reason.
}
```

`@Disabled` is more honest than an `assertTrue(true)` that pretends to
pass.

**4b — If the fixtures ARE usable** (e.g. the earlier assessment was
wrong): implement GM-24 following the same pattern as GM-19/GM-20 but
against the EMELNORTE files. Expected total: check
`presupuesto-expansi-n-de-alumbrado-publico.json` for the root TOTAL
row's `precioTotal`.

### 5 — Full-suite verification

```bash
./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain
```

**Expected:**
```
Tests run: N, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

Where `N` is the total pre-existing test count (25 auth + 21 apu + 4
consolidation + 5 property = 55 nominal, but the actual number depends
on jqwik property counting). `Skipped: 1` for `@Disabled` GM-24.

If any test fails: STOP; report exactly which test, actual vs expected,
verbatim.

## Done criteria

> **Cierre parcial USER-DECIDED 2026-08-28.** Items **[x]** cerrados; **[~]** residuales aceptados; **[ ]** abiertos para plans/014.

- [~] `./gradlew test --tests 'ec.uce.propuestas.motor.MotorConsolidacionTest.GM_19_total_general_tulcan' --console=plain`
  → actual `395108.37` vs esperado `395115.32` (delta `-$6.95`). Residual aceptado.
- [~] `./gradlew test --tests 'ec.uce.propuestas.motor.MotorConsolidacionTest.GM_20_totales_capitulos_raiz_tulcan' --console=plain`
  → 6/7 capítulos raíz a delta 0.00; cap. 1 actual `158907.21` vs `158908.05` (delta `-$0.84`). Residual aceptado.
- [x] GM-21's allowlist audited: las 11 entradas ≤ 0.03 sobreviven con comentario fuente (workbook artifact); ninguna removible.
- [x] GM-24 está `@Disabled` con razón específica (fixture EMELNORTE upstream bug). No implementado contra datos EMELNORTE (rotura upstream); sigue como deuda en `plans/014`.
- [x] `./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain` corre la suite completa con `ConsolidadorFronteraTest` 5/5 + `MotorApuTest` 21/21 + `MotorPropiedadesTest` 5/5 + GM-21 verde + GM-19/GM-20 rojos con residual aceptado + GM-24/DIAG `@Disabled`. Sin nuevas fallas no atribuidas al workbook.
- [x] `grep -r 'double\|float' src/main/java/ec/uce/propuestas/motor/` zero hits.
- [x] No files touched outside the in-scope list. `Motor.java`, `internal/CalculadorFila.java`, y las assertions de tests per-APU **sin cambios**.

## Test plan

**Un test nuevo** (`ConsolidadorFronteraTest`, 5/5 verde) verifica la regla workbook-consistent a nivel de unidad a través de la API pública `Motor.consolidar(VersionSnapshot)`. El resto de la suite del motor se mantiene como red de regresión.

**Post-fix regression rule** (permanent): las dos GMs aún rojas (GM-19, GM-20) tienen un residual documentado en §1.1 y **no** deben regresar silenciosamente a 0.00 bajo la regla workbook-consistent; cualquier plan futuro que cierre el delta residual debe adjuntar evidencia nueva y abrir STOP al autor antes de tocar el motor. Plan 002 (CI GitHub Actions — historical CI plan, **not** the current Plan 02 of the motor) will run `./gradlew test --tests 'ec.uce.propuestas.motor.*'` on every push cuando aterrice; esa será la guarda permanente.

## Maintenance note

- **When the upstream `thesis-docs/plan/domain/_artifacts/` fixtures
  are refreshed**: re-copy them into `src/test/resources/motor/fixtures/`
  and re-run the full motor suite. If a fixture refresh breaks a GM,
  first check whether the fixture's workbook TOTAL row changed (real
  data change) vs. whether the motor's arithmetic changed (real bug).
- **If the EMELNORTE fixture is later repaired upstream**: enable
  GM-24 by removing `@Disabled` and filling in the expected values.
- **`Fixtures.java` is test infrastructure, not motor code.** Future
  test additions for I-05 (APU editor) and I-07 (presupuesto) may
  add more Fixtures helpers. Keep the file organized; consider
  splitting into `ApuFixtures.java` and `VersionFixtures.java` if it
  exceeds ~300 lines.
- **Do not add a `tolerance` parameter to any GM assertion.** The
  0.00-deviation rule is the thesis's contract. **Excepción documentada
  2026-08-28 (cierre parcial user-accepted):** GM-19 (`-$6.95`) y
  GM-20 cap. 1 (`-$0.84`) **no** cierran a 0.00; el residual es
  aceptado por el autor y este plan se cierra como PARTIAL — no se
  reabre el motor para cerrarlo. El workbook IESS es el example
  único y no se audita per-rubro (preferencia del usuario).
- **No reintroducir `setScale(2, DOWN)` por rubro total.** La versión
  previa de este plan (anterior a la corrección workbook-consistent
  2026-08-28) aplicaba `precioTotal = cantidad × PU_2dp, setScale(2,
  DOWN)` simétrico con `precioUnitario` y producía deltas
  sistemáticos `GM19 = -$9.37` y `GM20 cap1 = -$3.09` vs workbook
  IESS. La regla vigente es **`precioTotal = cantidad × PU_2dp`
  retenido a escala 6 `HALF_UP`** (workbook-consistent); cualquier
  futuro `plans/0NN-motor-fix.md` que proponga truncar `precioTotal`
  a 2 dp debe adjuntar evidencia de que no reintroduce esos deltas.
  Ver STOP conditions de [`plans/014-motor-precision-no-links.md`](./014-motor-precision-no-links.md).

## Escape hatches — STOP conditions

- After §1, GM-19 or GM-20 still fails → STOP; run the diagnostic
  dump described in §2 and report the first divergent node. Do NOT
  edit `Motor.java` or `Consolidador.java` before reporting.
- Any rubro in the fixture has `precioTotal = null` when it also has
  a non-null `precioUnitario` and `cantidad` → STOP; that's a fixture
  bug, report the row's `item`/`codigo`.
- Any chapter's fixture-recorded `precioTotal` differs from the sum
  of its children's `precioTotal` fields by more than $0.01 → STOP;
  that's an upstream workbook inconsistency, report the chapter's
  `item`.
- Any step wants to change `Motor.java`'s public API (record fields,
  method signatures) → STOP. This is a bug-fix plan, not an API
  change plan.
- Any step wants to add a new dependency to `pom.xml` → STOP. Not
  in scope.
- After §3's audit, if you find that fewer than 3 or more than 15
  allowlist entries survive → STOP and report. Fewer means the stub
  fix was so effective the audit tautologically passes (verify);
  more means the workbook is unusually rounding-heavy and the domain
  doc should be updated first.
- **STOP — residual IESS aceptado 2026-08-28 (cerrado):** el delta
  sub-céntimo residual sobre GM-19 y GM-20 cap. 1 con la regla
  workbook-consistent vigente **no** se reabre para cerrarlo en el
  motor. Cualquier plan futuro que proponga cerrar el residual debe
  aportar evidencia nueva y adjuntar STOP al autor antes de tocar
  código.
