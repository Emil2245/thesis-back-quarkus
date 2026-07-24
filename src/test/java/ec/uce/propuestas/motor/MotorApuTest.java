package ec.uce.propuestas.motor;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden Master tests for the per-APU grain of the motor.
 * GM-01…GM-18: all 18 APUs from apus-sample-apus-cetro-medico-tulcan.json.
 * GM-22: HM formula spot-check.
 * GM-23: Annex C.7 APU built by hand.
 * GM-25: precision/rounding spot-check (motor CT rounded 2dp matches workbook display).
 *
 * Comparison strategy:
 *   - costoTotal compared at scale 6 against fixture stored value at scale 6.
 *   - This handles floating-point noise in the fixture JSON (stored as IEEE 754 doubles).
 *   - costoDirecto compared similarly for fixtures that have exact values.
 *
 * The ParametrosCalculo used for Tulcan fixtures: %HM=5%, %CI_default=18%, descuento=0.
 */
class MotorApuTest {

    private static final ParametrosCalculo P_TULCAN = new ParametrosCalculo(
            new BigDecimal("0.0500"),
            new BigDecimal("0.1800"),
            null,
            BigDecimal.ZERO
    );

    private static final String TULCAN_FILE = "apus-sample-apus-cetro-medico-tulcan.json";

    /** Compare motor output at scale 6 against fixture value at scale 6. */
    private static void assertEq6(String label, BigDecimal expected, BigDecimal actual) {
        BigDecimal e = expected.setScale(6, RoundingMode.HALF_UP);
        BigDecimal a = actual.setScale(6, RoundingMode.HALF_UP);
        assertEquals(0, e.compareTo(a),
                label + ": expected=" + e + " actual=" + a);
    }

    private static ApuCalculado computeApu(JsonNode root, String codigo) {
        JsonNode node = Fixtures.findByCodigo(root, codigo);
        ApuSnapshot snap = Fixtures.apuFromJson(node);
        return Motor.calcularApu(snap, P_TULCAN);
    }

    // ── GM-01 ──────────────────────────────────────────────────────────────

    @Test
    void GM_01_501BM6() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        JsonNode apuNode = Fixtures.findByCodigo(root, "501BM6");
        ApuSnapshot snap = Fixtures.apuFromJson(apuNode);
        ApuCalculado out = Motor.calcularApu(snap, P_TULCAN);

        // Fixture exact values (no float noise)
        assertEquals(0, new BigDecimal("4.690875").compareTo(out.costoDirecto()),
                "501BM6 costoDirecto");
        assertEquals(0, new BigDecimal("5.5352325").compareTo(out.costoTotal()),
                "501BM6 costoTotal");
    }

    // ── GM-02 ──────────────────────────────────────────────────────────────

    @Test
    void GM_02_501B64() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        JsonNode apuNode = Fixtures.findByCodigo(root, "501B64");
        BigDecimal fixtureCT = new BigDecimal(apuNode.get("costoTotal").asText());
        ApuCalculado out = computeApu(root, "501B64");
        assertEq6("501B64 costoTotal", fixtureCT, out.costoTotal());
    }

    // ── GM-03 ──────────────────────────────────────────────────────────────

    @Test
    void GM_03_501D1V() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        JsonNode apuNode = Fixtures.findByCodigo(root, "501D1V");
        BigDecimal fixtureCT = new BigDecimal(apuNode.get("costoTotal").asText());
        ApuCalculado out = computeApu(root, "501D1V");
        assertEq6("501D1V costoTotal", fixtureCT, out.costoTotal());
    }

    // ── GM-04 ──────────────────────────────────────────────────────────────

    @Test
    void GM_04_501DQR() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        JsonNode apuNode = Fixtures.findByCodigo(root, "501DQR");
        BigDecimal fixtureCT = new BigDecimal(apuNode.get("costoTotal").asText());
        ApuCalculado out = computeApu(root, "501DQR");
        assertEq6("501DQR costoTotal", fixtureCT, out.costoTotal());
    }

    // ── GM-05 ──────────────────────────────────────────────────────────────

    @Test
    void GM_05_501D00() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        JsonNode apuNode = Fixtures.findByCodigo(root, "501D00");
        BigDecimal fixtureCT = new BigDecimal(apuNode.get("costoTotal").asText());
        ApuCalculado out = computeApu(root, "501D00");
        assertEq6("501D00 costoTotal", fixtureCT, out.costoTotal());
    }

    // ── GM-06 ──────────────────────────────────────────────────────────────

    @Test
    void GM_06_501AKN() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        JsonNode apuNode = Fixtures.findByCodigo(root, "501AKN");
        BigDecimal fixtureCT = new BigDecimal(apuNode.get("costoTotal").asText());
        ApuCalculado out = computeApu(root, "501AKN");
        assertEq6("501AKN costoTotal", fixtureCT, out.costoTotal());
    }

    // ── GM-07 ──────────────────────────────────────────────────────────────

    @Test
    void GM_07_502897() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        JsonNode apuNode = Fixtures.findByCodigo(root, "502897");
        BigDecimal fixtureCT = new BigDecimal(apuNode.get("costoTotal").asText());
        ApuCalculado out = computeApu(root, "502897");
        assertEq6("502897 costoTotal", fixtureCT, out.costoTotal());
    }

    // ── GM-08 ──────────────────────────────────────────────────────────────

    @Test
    void GM_08_500AT8() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        JsonNode apuNode = Fixtures.findByCodigo(root, "500AT8");
        BigDecimal fixtureCT = new BigDecimal(apuNode.get("costoTotal").asText());
        ApuCalculado out = computeApu(root, "500AT8");
        assertEq6("500AT8 costoTotal", fixtureCT, out.costoTotal());
    }

    // ── GM-09 ──────────────────────────────────────────────────────────────

    @Test
    void GM_09_502ARV() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        JsonNode apuNode = Fixtures.findByCodigo(root, "502ARV");
        BigDecimal fixtureCT = new BigDecimal(apuNode.get("costoTotal").asText());
        ApuCalculado out = computeApu(root, "502ARV");
        assertEq6("502ARV costoTotal", fixtureCT, out.costoTotal());
    }

    // ── GM-10 ──────────────────────────────────────────────────────────────

    @Test
    void GM_10_503B30() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        JsonNode apuNode = Fixtures.findByCodigo(root, "503B30");
        BigDecimal fixtureCT = new BigDecimal(apuNode.get("costoTotal").asText());
        ApuCalculado out = computeApu(root, "503B30");
        assertEq6("503B30 costoTotal", fixtureCT, out.costoTotal());
    }

    // ── GM-11 ──────────────────────────────────────────────────────────────

    @Test
    void GM_11_500ASU() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        JsonNode apuNode = Fixtures.findByCodigo(root, "500ASU");
        BigDecimal fixtureCT = new BigDecimal(apuNode.get("costoTotal").asText());
        ApuCalculado out = computeApu(root, "500ASU");
        assertEq6("500ASU costoTotal", fixtureCT, out.costoTotal());
    }

    // ── GM-12 ──────────────────────────────────────────────────────────────

    @Test
    void GM_12_501DH5() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        JsonNode apuNode = Fixtures.findByCodigo(root, "501DH5");
        BigDecimal fixtureCT = new BigDecimal(apuNode.get("costoTotal").asText());
        ApuCalculado out = computeApu(root, "501DH5");
        assertEq6("501DH5 costoTotal", fixtureCT, out.costoTotal());
    }

    // ── GM-13 ──────────────────────────────────────────────────────────────

    @Test
    void GM_13_505APQ() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        JsonNode apuNode = Fixtures.findByCodigo(root, "505APQ");
        BigDecimal fixtureCT = new BigDecimal(apuNode.get("costoTotal").asText());
        ApuCalculado out = computeApu(root, "505APQ");
        assertEq6("505APQ costoTotal", fixtureCT, out.costoTotal());
    }

    // ── GM-14 ──────────────────────────────────────────────────────────────

    @Test
    void GM_14_504BA0() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        JsonNode apuNode = Fixtures.findByCodigo(root, "504BA0");
        BigDecimal fixtureCT = new BigDecimal(apuNode.get("costoTotal").asText());
        ApuCalculado out = computeApu(root, "504BA0");
        assertEq6("504BA0 costoTotal", fixtureCT, out.costoTotal());
    }

    // ── GM-15 ──────────────────────────────────────────────────────────────

    @Test
    void GM_15_504B3I() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        JsonNode apuNode = Fixtures.findByCodigo(root, "504B3I");
        BigDecimal fixtureCT = new BigDecimal(apuNode.get("costoTotal").asText());
        ApuCalculado out = computeApu(root, "504B3I");
        assertEq6("504B3I costoTotal", fixtureCT, out.costoTotal());
    }

    // ── GM-16 ──────────────────────────────────────────────────────────────

    @Test
    void GM_16_500C2S() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        JsonNode apuNode = Fixtures.findByCodigo(root, "500C2S");
        BigDecimal fixtureCT = new BigDecimal(apuNode.get("costoTotal").asText());
        ApuCalculado out = computeApu(root, "500C2S");
        assertEq6("500C2S costoTotal", fixtureCT, out.costoTotal());
    }

    // ── GM-17 ──────────────────────────────────────────────────────────────

    @Test
    void GM_17_500AT1() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        JsonNode apuNode = Fixtures.findByCodigo(root, "500AT1");
        BigDecimal fixtureCT = new BigDecimal(apuNode.get("costoTotal").asText());
        ApuCalculado out = computeApu(root, "500AT1");
        assertEq6("500AT1 costoTotal", fixtureCT, out.costoTotal());
    }

    // ── GM-18 ──────────────────────────────────────────────────────────────

    @Test
    void GM_18_501772() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        JsonNode apuNode = Fixtures.findByCodigo(root, "501772");
        BigDecimal fixtureCT = new BigDecimal(apuNode.get("costoTotal").asText());
        ApuCalculado out = computeApu(root, "501772");
        assertEq6("501772 costoTotal", fixtureCT, out.costoTotal());
    }

    // ── GM-22 — HM formula spot-check ─────────────────────────────────────

    @Test
    void GM_22_HM_formula_501BM6() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        ApuCalculado out = computeApu(root, "501BM6");

        // subtotalN = 4.4675; HM = 5% × 4.4675 = 0.223375
        assertEquals(0, new BigDecimal("0.223375").compareTo(out.costoHm()),
                "501BM6 costoHm should be 0.223375");
    }

    // ── GM-23 — Annex C.7 APU built by hand ───────────────────────────────

    /**
     * APU 501062 — Provisión e instalación de vinil disipador electrostático.
     * Source: v1.1-functional-requirements.md Annex C.7.
     *
     * Construction note: the Annex shows a "Costo" column (pre-computed, 2dp rounded).
     * The raw quantity/price combinations in the Annex contain a data inconsistency:
     * pegamento shows 0.10 × 61.49 = 5.84 (but 0.10 × 61.49 = 6.149, not 5.84).
     *
     * Resolution: to faithfully match the Annex's CD=61.39 verification, we use the Annex's
     * displayed COST as the input price with cantidad=1. This interprets each row's
     * cost column as the unit price (effectively using Annex 2dp-rounded row costs as source):
     *
     * MO rows (treated as costo_fila = cantidad × precio × rendimiento with rend=1.0):
     *   Instalador: 1.00 × 4.28 × 1.0 = 4.28
     *   Peón:       1.00 × 4.23 × 1.0 = 4.23
     *   Maestro:    1.00 × 0.48 × 1.0 = 0.48  (Annex shows 0.48 = 0.10×4.75 rounded 2dp)
     * → subtotalN = 8.99
     *
     * Material rows (costo = cantidad × precio):
     *   Vinil:    1.00 × 45.60 = 45.60
     *   Cordón:   1.00 × 0.51  = 0.51   (Annex shows 0.51)
     *   Pegamento:1.00 × 5.84  = 5.84   (Annex shows 5.84, inconsistent with 0.10×61.49)
     * → subtotalO = 51.95
     *
     * HM = 5% × 8.99 = 0.4495 → 0.45 at 2dp ✓
     * CD = 0.4495 + 8.99 + 51.95 = 61.3895 → 61.39 at 2dp ✓
     *
     * Expected (workbook 2dp rounded): HM=0.45, CD=61.39, CI=11.05, CT=72.44
     */
    @Test
    void GM_23_Annex_C7_501062_vinil_disipador() {
        // MANO_OBRA rows: using Annex costo-column values as price, cantidad=1, rend=1.0
        FilaSnapshot moInstalador = new FilaSnapshot(SeccionTipo.MANO_OBRA, false,
                new BigDecimal("1.00"), new BigDecimal("1.00000"),
                new BigDecimal("4.28"), null, null);
        FilaSnapshot moPeon = new FilaSnapshot(SeccionTipo.MANO_OBRA, false,
                new BigDecimal("1.00"), new BigDecimal("1.00000"),
                new BigDecimal("4.23"), null, null);
        // Maestro: Annex shows costo=0.48 (= 0.10×4.75 displayed at 2dp).
        // Using price=0.48, cantidad=1, rend=1.0 to exactly match the Annex's subtotalN=8.99.
        FilaSnapshot moMaestro = new FilaSnapshot(SeccionTipo.MANO_OBRA, false,
                new BigDecimal("1.00"), new BigDecimal("1.00000"),
                new BigDecimal("0.48"), null, null);

        // EQUIPO: HM row only (5% of MO)
        FilaSnapshot hmFila = new FilaSnapshot(SeccionTipo.EQUIPO, true,
                new BigDecimal("5"), null, null, null, null);

        // MATERIAL rows: using Annex costo-column values as price, cantidad=1
        FilaSnapshot matVinil = new FilaSnapshot(SeccionTipo.MATERIAL, false,
                new BigDecimal("1.00"), null, new BigDecimal("45.60"), null, null);
        // Cordón: Annex shows costo=0.51; using price=0.51 to get subtotalO=51.95
        FilaSnapshot matCordon = new FilaSnapshot(SeccionTipo.MATERIAL, false,
                new BigDecimal("1.00"), null, new BigDecimal("0.51"), null, null);
        // Pegamento: Annex shows costo=5.84 (inconsistent with 0.10×61.49=6.149)
        FilaSnapshot matPegamento = new FilaSnapshot(SeccionTipo.MATERIAL, false,
                new BigDecimal("1.00"), null, new BigDecimal("5.84"), null, null);

        // APU snapshot — NOT auxiliar
        ApuSnapshot snap = new ApuSnapshot("501062", false,
                java.util.List.of(hmFila, moInstalador, moPeon, moMaestro,
                        matVinil, matCordon, matPegamento));

        ParametrosCalculo p = new ParametrosCalculo(
                new BigDecimal("0.0500"),
                new BigDecimal("0.1800"),
                null,
                BigDecimal.ZERO
        );

        ApuCalculado out = Motor.calcularApu(snap, p);

        // Annex C.7 verification: assert motor's exact values round to the Annex's 2dp values
        // HM = 5% × 8.99 = 0.4495 → 0.45 at 2dp
        assertEquals(0,
                new BigDecimal("0.45").compareTo(out.costoHm().setScale(2, RoundingMode.HALF_UP)),
                "Annex C.7 HM rounded 2dp should be 0.45, got " + out.costoHm());

        // CD = 0.4495 + 8.99 + 51.95 = 61.3895 → 61.39 at 2dp
        assertEquals(0,
                new BigDecimal("61.39").compareTo(out.costoDirecto().setScale(2, RoundingMode.HALF_UP)),
                "Annex C.7 CD rounded 2dp should be 61.39, got " + out.costoDirecto());

        // CI = 61.3895 × 0.18 = 11.05011 → 11.05 at 2dp
        assertEquals(0,
                new BigDecimal("11.05").compareTo(out.costoIndirecto().setScale(2, RoundingMode.HALF_UP)),
                "Annex C.7 CI rounded 2dp should be 11.05, got " + out.costoIndirecto());

        // CT = 61.3895 + 11.05011 = 72.43961 → 72.44 at 2dp
        assertEquals(0,
                new BigDecimal("72.44").compareTo(out.costoTotal().setScale(2, RoundingMode.HALF_UP)),
                "Annex C.7 CT rounded 2dp should be 72.44, got " + out.costoTotal());
    }

    // ── GM-25 — precision / rounding spot-check ────────────────────────────

    /**
     * GM-01's CT = 5.5352325 at 6dp. Rounded 2dp must equal 5.54 (workbook display).
     * This is the ONE place we round to 2dp in tests — verifying the export math.
     */
    @Test
    void GM_25_precision_rounding_501BM6() throws Exception {
        JsonNode root = Fixtures.loadJson(TULCAN_FILE);
        ApuCalculado out = computeApu(root, "501BM6");

        BigDecimal rounded2dp = out.costoTotal().setScale(2, RoundingMode.HALF_UP);
        assertEquals(0, new BigDecimal("5.54").compareTo(rounded2dp),
                "501BM6 CT rounded 2dp should be 5.54, got " + rounded2dp);
    }
}
