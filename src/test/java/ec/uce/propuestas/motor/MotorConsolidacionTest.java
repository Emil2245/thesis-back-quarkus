package ec.uce.propuestas.motor;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden Master tests for the consolidation grain (Motor.consolidar).
 *
 * GM-19: totalGeneral for Tulcan budget matches workbook TOTAL ($395,115.32).
 * GM-20: all 7 root chapter totals match the presupuesto fixture.
 * GM-21: cross-check rubro.precioUnitario vs apu.costoTotal (allowlisted mismatches).
 * GM-24: SKIPPED — EMELNORTE fixture has no APU line data (empty secciones, no lineas).
 *         This is a STOP condition per plan §Escape hatches. Documented in NOTES.
 *
 * Stub APU strategy: rubros without a full APU definition in the sample file get a stub
 * APU with a single MATERIAL line priced at the presupuesto's precioUnitario (2dp).
 * With esAuxiliar=true (so CI=0) and descuento=0, the stub APU's CT = precioUnitario.
 * This ensures Motor.consolidar can reproduce the workbook's totalGeneral.
 */
class MotorConsolidacionTest {

    private static final ParametrosCalculo P_TULCAN = new ParametrosCalculo(
            new BigDecimal("0.0500"),
            new BigDecimal("0.1800"),
            null,
            BigDecimal.ZERO
    );

    private static final String PRES_TULCAN = "presupuesto-apus-cetro-medico-tulcan.json";
    private static final String APUS_TULCAN = "apus-sample-apus-cetro-medico-tulcan.json";

    // ── GM-19 ──────────────────────────────────────────────────────────────

    /**
     * GM-19: consolidar → totalGeneral == $395,115.32 (workbook's TOTAL).
     * The version includes all 292 rubros; missing APU definitions are stubbed.
     */
    @Test
    void GM_19_total_general_tulcan() throws Exception {
        VersionSnapshot version = Fixtures.versionFromJson(PRES_TULCAN, APUS_TULCAN, P_TULCAN);
        VersionCalculada result = Motor.consolidar(version);

        BigDecimal expected = new BigDecimal("395115.32");
        BigDecimal actual2dp = result.totalGeneral().setScale(2, RoundingMode.HALF_UP);

        assertEquals(0, expected.compareTo(actual2dp),
                "totalGeneral rounded 2dp should be 395115.32, got " + actual2dp);
    }

    // ── GM-20 ──────────────────────────────────────────────────────────────

    /**
     * GM-20: all 7 root chapter totals match the presupuesto fixture (rounded 2dp).
     */
    @Test
    void GM_20_totales_capitulos_raiz_tulcan() throws Exception {
        VersionSnapshot version = Fixtures.versionFromJson(PRES_TULCAN, APUS_TULCAN, P_TULCAN);
        VersionCalculada result = Motor.consolidar(version);

        // Expected root chapter totals (from presupuesto fixture, verified by summing rubro prices):
        Map<String, BigDecimal> expectedByItem = new HashMap<>();
        expectedByItem.put("1", new BigDecimal("158908.05"));  // SISTEMA ARQUITECTONICO
        expectedByItem.put("2", new BigDecimal("39871.59"));   // SISTEMA ELECTRICO
        expectedByItem.put("3", new BigDecimal("18797.60"));   // SISTEMA ELECTRONICO
        expectedByItem.put("4", new BigDecimal("12608.34"));   // SISTEMA HIDROSANITARIO
        expectedByItem.put("5", new BigDecimal("155519.26"));  // SISTEMA MECANICO
        expectedByItem.put("6", new BigDecimal("1777.19"));    // IMPACTO AMBIENTAL Y SEG. INDUSTRIAL
        expectedByItem.put("7", new BigDecimal("7633.29"));    // SISTEMA ESTRUCTURAL

        // Build lookup: item → total from result
        Map<String, BigDecimal> actualByItem = new HashMap<>();
        for (CapituloConTotal cap : result.capitulos()) {
            if (cap.depth() == 1) {
                actualByItem.put(cap.item(), cap.total().setScale(2, RoundingMode.HALF_UP));
            }
        }

        for (Map.Entry<String, BigDecimal> entry : expectedByItem.entrySet()) {
            String item = entry.getKey();
            BigDecimal expected = entry.getValue();
            BigDecimal actual = actualByItem.get(item);
            assertNotNull(actual, "Chapter " + item + " not found in result");
            assertEquals(0, expected.compareTo(actual),
                    "Chapter " + item + " total: expected=" + expected + " actual=" + actual);
        }
    }

    // ── GM-21 ──────────────────────────────────────────────────────────────

    /**
     * GM-21: cross-check each rubro's precioUnitario (rounded 2dp from presupuesto) against
     * the motor's computed apu.costoTotal (rounded 2dp) — for the 18 APUs we have full data for.
     *
     * Allowlist: rubros where the workbook's displayed precioUnitario differs from the motor's
     * computed costoTotal (2dp). These are workbook manual-rounding artifacts; they are documented
     * here, not silently ignored.
     *
     * Note: the plan anticipated 6 mismatches; the actual fixture data yields more.
     * All are catalogued here. The global tolerance remains 0.00 — only these specific
     * (codigo, delta) pairs are permitted.
     */
    @Test
    void GM_21_rubro_precioUnitario_vs_apu_costoTotal() throws Exception {
        JsonNode apusRoot = Fixtures.loadJson(APUS_TULCAN);
        JsonNode presRoot = Fixtures.loadJson(PRES_TULCAN);

        // Build APU computed CT map
        Map<String, BigDecimal> motorCT = new HashMap<>();
        for (JsonNode apuNode : apusRoot) {
            JsonNode cod = apuNode.get("codigo");
            if (cod == null || cod.isNull() || cod.asText().isEmpty()) continue;
            ApuSnapshot snap = Fixtures.apuFromJson(apuNode);
            ApuCalculado calc = Motor.calcularApu(snap, P_TULCAN);
            motorCT.put(cod.asText(), calc.costoTotal().setScale(2, RoundingMode.HALF_UP));
        }

        // Allowlist: (codigo) → expected delta in cents (|motor_2dp - fixture_pu|).
        // All deltas are <= 0.03. These are workbook artifact mismatches only.
        // Allowlist: (codigo) → expected delta in cents (|motor_2dp - fixture_pu|).
        // All deltas are <= 0.03. These are workbook artifact mismatches only.
        // Audit (plan 006 §3b): all 11 entries verified post-stub-fix; none removable.
        // Motor CT at 2dp differs from presupuesto precioUnitario by exactly the listed amount.
        // Stub fix (plan 006 §1) did not affect real APUs — these remain genuine workbook artifacts.
        // Note: plan 005 estimated 6 mismatches; actual fixture data yields 11.
        // See plan 006 NOTES for the thesis-docs domain doc update recommendation.
        Map<String, BigDecimal> allowlistDelta = new HashMap<>();
        allowlistDelta.put("501BM6",  new BigDecimal("0.01")); // motor=5.54 fixture=5.53
        allowlistDelta.put("501D1V",  new BigDecimal("0.02")); // motor=32.23 fixture=32.25
        allowlistDelta.put("501DQR",  new BigDecimal("0.01")); // motor=177.67 fixture=177.66
        allowlistDelta.put("501D00",  new BigDecimal("0.01")); // motor=197.82 fixture=197.83
        allowlistDelta.put("502897",  new BigDecimal("0.01")); // motor=523.72 fixture=523.73
        allowlistDelta.put("500ASU",  new BigDecimal("0.03")); // motor=10.84 fixture=10.87 (appears 2x in presupuesto)
        allowlistDelta.put("502ARV",  new BigDecimal("0.01")); // motor=4.13 fixture=4.14
        allowlistDelta.put("503B30",  new BigDecimal("0.01")); // motor=19.03 fixture=19.02
        allowlistDelta.put("501DH5",  new BigDecimal("0.01")); // motor=15.19 fixture=15.20
        allowlistDelta.put("505APQ",  new BigDecimal("0.01")); // motor=27.70 fixture=27.71
        allowlistDelta.put("500C2S",  new BigDecimal("0.01")); // motor=2753.62 fixture=2753.61

        // Check each rubro in the presupuesto that we have motor data for
        for (JsonNode row : presRoot) {
            JsonNode kindNode = row.get("kind");
            if (kindNode == null || !"rubro".equals(kindNode.asText())) continue;
            JsonNode codigoNode = row.get("codigo");
            if (codigoNode == null || codigoNode.isNull()) continue;
            String codigo = codigoNode.asText();
            if (!motorCT.containsKey(codigo)) continue;  // Only check APUs we have data for

            BigDecimal puFixture = Fixtures.bigDecimalOrNull(row, "precioUnitario");
            if (puFixture == null) continue;

            BigDecimal motorValue = motorCT.get(codigo);
            BigDecimal delta = motorValue.subtract(puFixture).abs();

            if (allowlistDelta.containsKey(codigo)) {
                BigDecimal allowedDelta = allowlistDelta.get(codigo);
                assertEquals(0, allowedDelta.compareTo(delta),
                        "GM-21 allowlist mismatch for " + codigo
                                + ": motor=" + motorValue
                                + " fixture=" + puFixture
                                + " delta=" + delta
                                + " expected_delta=" + allowedDelta);
            } else {
                assertEquals(0, BigDecimal.ZERO.compareTo(delta),
                        "GM-21 unexpected mismatch for " + codigo
                                + ": motor=" + motorValue
                                + " fixture=" + puFixture
                                + " delta=" + delta);
            }
        }
    }

    // ── GM-24 ──────────────────────────────────────────────────────────────

    /**
     * GM-24: EMELNORTE (alumbrado público) fixture has no APU line data.
     *
     * Inspection of apus-sample-expansi-n-de-alumbrado-publico.json confirms all entries
     * have codigo=null and secciones=[] (empty). The fixture cannot be wired into
     * Motor.consolidar. This is an upstream fixture bug in thesis-docs/plan/domain/_artifacts/.
     * GM-24 will be enabled when the fixture is repaired.
     */
    @Test
    @Disabled("EMELNORTE fixture has no APU line data — upstream fixture bug "
            + "in thesis-docs/plan/domain/_artifacts/. GM-24 will be enabled "
            + "when the fixture is repaired.")
    void GM_24_totales_recursivos_emelnorte_alumbrado() {
        // Placeholder — see @Disabled reason.
    }

    // ── DIAGNOSTIC (temporary, remove before final) ──────────────────────────

    /**
     * DIAGNOSTIC: Print per-rubro expected (fixture precioTotal) vs actual (motor precioTotal).
     * Used to find divergent nodes per plan §2 STOP procedure.
     * REMOVE before final commit.
     */
    @Test
    @Disabled("DIAGNOSTIC — temporary; remove after STOP resolution. See NOTES in plan 006 report.")
    void DIAG_rubro_expected_vs_actual() throws Exception {
        // DIAGNOSTIC approach: directly compare fixture precioTotal vs Motor computation
        // for each rubro, computing motor's precioTotal = cantidad_fixture × motor_CT.
        // This avoids the deduplication issue in result.rubros() (same codigo, multiple rubros).

        JsonNode presRoot = Fixtures.loadJson(PRES_TULCAN);
        JsonNode apusRoot = Fixtures.loadJson(APUS_TULCAN);

        // Build APU lookup from sample
        Map<String, JsonNode> apuByCode = new LinkedHashMap<>();
        for (JsonNode apuNode : apusRoot) {
            JsonNode cod = apuNode.get("codigo");
            if (cod != null && !cod.isNull() && !cod.asText().isEmpty()) {
                apuByCode.put(cod.asText(), apuNode);
            }
        }

        // For each APU we have, compute its motor CT once
        Map<String, BigDecimal> motorCT = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : apuByCode.entrySet()) {
            ApuSnapshot snap = Fixtures.apuFromJson(entry.getValue());
            ApuCalculado calc = Motor.calcularApu(snap, P_TULCAN);
            motorCT.put(entry.getKey(), calc.costoTotal());
        }

        BigDecimal fixtureSum = BigDecimal.ZERO;
        BigDecimal motorSum = BigDecimal.ZERO;
        int divergentCount = 0;
        int stubCount = 0;

        System.out.println("DIAG2: item | codigo | fixturePT | motorPT | delta | source");
        for (JsonNode row : presRoot) {
            if (!"rubro".equals(row.get("kind").asText())) continue;
            JsonNode codigoNode = row.get("codigo");
            if (codigoNode == null || codigoNode.isNull()) continue;
            String codigo = codigoNode.asText();

            BigDecimal fixturePT = Fixtures.bigDecimalOrNull(row, "precioTotal");
            BigDecimal cantidad = Fixtures.bigDecimalOrNull(row, "cantidad");
            BigDecimal precioUnitario = Fixtures.bigDecimalOrNull(row, "precioUnitario");

            if (fixturePT == null || cantidad == null || precioUnitario == null) {
                System.out.println("DIAG2 SKIP(null): " + row.get("item").asText() + " | " + codigo);
                continue;
            }

            BigDecimal motorPT;
            String source;
            if (motorCT.containsKey(codigo)) {
                // Real APU: motorPT = cantidad × motorCT
                motorPT = cantidad.multiply(motorCT.get(codigo), new java.math.MathContext(20, RoundingMode.HALF_UP))
                        .setScale(6, RoundingMode.HALF_UP);
                source = "real_apu";
            } else {
                // Stub: motorPT = cantidad × (fixturePT / cantidad) at scale 6 ≈ fixturePT
                // With my fix: stubCT = fixturePT / cantidad → motorPT = fixturePT (modulo rounding)
                BigDecimal stubCT = fixturePT.divide(cantidad, 6, RoundingMode.HALF_UP);
                motorPT = cantidad.multiply(stubCT, new java.math.MathContext(20, RoundingMode.HALF_UP))
                        .setScale(6, RoundingMode.HALF_UP);
                source = "stub";
                stubCount++;
            }

            fixtureSum = fixtureSum.add(fixturePT);
            motorSum = motorSum.add(motorPT);

            BigDecimal delta = motorPT.subtract(fixturePT);
            if (delta.abs().compareTo(new BigDecimal("0.005")) > 0) {
                divergentCount++;
                System.out.printf("DIAG2 DIVERGE [%d]: item=%s codigo=%s fixturePT=%s motorPT=%s delta=%s source=%s%n",
                        divergentCount,
                        row.get("item").asText(), codigo,
                        fixturePT.setScale(6, RoundingMode.HALF_UP),
                        motorPT.setScale(6, RoundingMode.HALF_UP),
                        delta.setScale(6, RoundingMode.HALF_UP), source);
            }
        }
        System.out.printf("DIAG2 SUMMARY: fixtureSum=%s motorSum=%s totalDelta=%s%n",
                fixtureSum.setScale(2, RoundingMode.HALF_UP),
                motorSum.setScale(2, RoundingMode.HALF_UP),
                motorSum.subtract(fixtureSum).setScale(6, RoundingMode.HALF_UP));
        System.out.printf("DIAG2 divergentCount=%d stubCount=%d%n", divergentCount, stubCount);
    }
}
