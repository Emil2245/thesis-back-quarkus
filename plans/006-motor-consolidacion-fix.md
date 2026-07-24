# 006 — Motor consolidación fix: GM-19 & GM-20, GM-21 allowlist audit, GM-24 real impl

- **Status:** TODO
- **Iteration:** I-02 (roadmap/01, semana 4)
- **Depends on:** 005 (motor scaffold present in `2fe6c83`)
- **Blocks:** the I-02 hito "GM api verdes" — without this fix, the thesis's `exactitud_calculo` variable is not defensible

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
./mvnw -q test -Dtest='ec.uce.propuestas.motor.MotorConsolidacionTest'
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
- `MathContext(20, RoundingMode.HALF_UP)` for multiplications, then
  `.setScale(6, HALF_UP)` at each result boundary.
- Two-dp rounding only at export/assertion time, not in the motor.
- No framework annotations under `motor/`.

## Steps

### 1 — Fix the stub-precision bug

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
./mvnw -q test -Dtest='ec.uce.propuestas.motor.MotorConsolidacionTest'
```

**Expected:**
- GM-19 passes: `totalGeneral (2dp) == 395115.32`.
- GM-20 passes: all 7 root chapter totals match.
- GM-21 result unchanged (may still pass/fail per its allowlist — see §3).
- GM-24 result unchanged (still a stub — see §4).

**If GM-19 or GM-20 STILL fails after §1:**
- **STOP.** Do NOT modify Motor code yet.
- Add a temporary diagnostic dump inside the test (a `@Disabled`ed
  new test method is fine — remove before commit) that prints, for
  every root chapter and every leaf rubro:
  ```
  <item>  <depth>  <expected precioTotal>  <actual precioTotal>  <delta>
  ```
  by re-parsing the fixture and walking `result.rubros()` /
  `result.capitulos()`.
- Report the first divergent node in NOTES. Common culprits:
  - `cantidad` parsed as `String` instead of `BigDecimal` (loss).
  - A rubro whose fixture has `precioTotal = null` — that rubro would
    fall back to `precioUnitario` and drift again.
  - A chapter whose child rubros' `precioTotal` fields sum to a value
    different from the chapter's own `precioTotal` field (workbook
    inconsistency — that's an upstream fixture bug, STOP condition).

Only if a genuine Motor bug is found (after the diagnostic dump) may
you edit `Consolidador.java`.

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
./mvnw -q test -Dtest='ec.uce.propuestas.motor.MotorConsolidacionTest#GM_21_rubro_precioUnitario_vs_apu_costoTotal'
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
./mvnw -q test 2>&1 | tail -8
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

- [ ] `./mvnw -q test -Dtest='ec.uce.propuestas.motor.MotorConsolidacionTest#GM_19_total_general_tulcan'`
  → PASS.
- [ ] `./mvnw -q test -Dtest='ec.uce.propuestas.motor.MotorConsolidacionTest#GM_20_totales_capitulos_raiz_tulcan'`
  → PASS.
- [ ] GM-21's allowlist has been audited: each entry has a source
  comment attributing it to a specific workbook row; any entries no
  longer needed after §1 are removed.
- [ ] GM-24 is either `@Disabled` with a specific reason (upstream
  fixture bug) OR implemented against real EMELNORTE data and green.
- [ ] `./mvnw -q test` runs the full suite to BUILD SUCCESS. No new
  failures anywhere.
- [ ] `grep -r 'double\|float' src/main/java/ec/uce/propuestas/motor/`
  still zero hits.
- [ ] No files touched outside the in-scope list. Especially:
  `Motor.java`, `internal/CalculadorFila.java`, and per-APU test
  assertions unchanged.

## Test plan

No **new** production tests — this plan repairs existing tests and,
optionally, one Fixture bug. The regression net is the existing
55-test suite; this plan is done when it's fully green.

**Post-fix regression rule** (permanent): the two failing GMs
(GM-19, GM-20) must never regress silently. Plan 002's CI runs
`./mvnw test` on every push; that becomes the ongoing guard.

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
  0.00-deviation rule is the thesis's contract. If a future fixture
  refresh requires tolerance, that's the fixture's problem and must
  be resolved upstream in `thesis-docs`.

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
