package ec.uce.propuestas.motor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import net.jqwik.api.*;

/**
 * Property-based tests for the motor using jqwik.
 *
 * Each property tests a universal invariant of the motor (things that must hold
 * for ALL valid inputs, not just the golden-master cases).
 *
 * Note on jqwik discovery: jqwik tests use @Property, not @Test. Surefire discovers
 * them via jqwik's JUnit 5 engine (bundled in net.jqwik:jqwik). If jqwik tests are
 * not discovered, confirm jqwik is on the test classpath and Surefire version >= 3.
 */
class MotorPropiedadesTest {

    // ── Generators ─────────────────────────────────────────────────────────

    /** Generate a simple APU with one MO row, one HM row, one material row. */
    @Provide
    Arbitrary<ApuSnapshot> apuSnapshots() {
        Arbitrary<BigDecimal> positiveDecimal = Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("1000.00"))
                .ofScale(2)
                .filter(d -> d.compareTo(BigDecimal.ZERO) > 0);

        return Combinators.combine(positiveDecimal, positiveDecimal, positiveDecimal)
                .as((jornal, rend, matPrecio) -> {
                    FilaSnapshot mo =
                            new FilaSnapshot(SeccionTipo.MANO_OBRA, false, BigDecimal.ONE, rend, jornal, null);
                    FilaSnapshot hm = new FilaSnapshot(SeccionTipo.EQUIPO, true, new BigDecimal("5"), null, null, null);
                    FilaSnapshot mat =
                            new FilaSnapshot(SeccionTipo.MATERIAL, false, BigDecimal.ONE, null, matPrecio, null);
                    // Plan 014 no-links: porcentajeIndirecto=null so CI = default (18% in baseParams).
                    return new ApuSnapshot("PROP-TEST", null, List.of(hm, mo, mat));
                });
    }

    /** Generate auxiliar APUs (esAuxiliar=true). */
    @Provide
    Arbitrary<ApuSnapshot> apuSnapshotsAuxiliares() {
        Arbitrary<BigDecimal> positiveDecimal = Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("500.00"))
                .ofScale(2)
                .filter(d -> d.compareTo(BigDecimal.ZERO) > 0);

        return positiveDecimal.map(matPrecio -> {
            FilaSnapshot mat = new FilaSnapshot(SeccionTipo.MATERIAL, false, BigDecimal.ONE, null, matPrecio, null);
            // Plan 014 no-links: CI override set to ZERO so CI = 0 regardless of default.
            return new ApuSnapshot("AUX-PROP", BigDecimal.ZERO, List.of(mat));
        });
    }

    /** Generate a valid ParametrosCalculo with the project default %CI=18% (Plan 015: no discount). */
    private ParametrosCalculo baseParams() {
        return new ParametrosCalculo(new BigDecimal("0.0500"), new BigDecimal("0.1800"));
    }

    // ── Properties ─────────────────────────────────────────────────────────

    /**
     * Plan 015: the per-APU discount seam is withdrawn. CI is applied on CD
     * directly: CI = CD × %CI aplicado. The CI depends only on the effective
     * %CI (per-APU override → default → 0) and on CD.
     */
    @Property(tries = 100)
    void costoIndirecto_aplica_sobre_CD_y_depende_de_pct_ci(@ForAll("apuSnapshots") ApuSnapshot snap) {
        ParametrosCalculo pBase = baseParams();
        ApuCalculado rBase = Motor.calcularApu(snap, pBase);

        // %CI default = 18% → CI = CD × 0.18
        BigDecimal ciEsperado =
                rBase.costoDirecto().multiply(new BigDecimal("0.1800"), new MathContext(20, RoundingMode.HALF_UP));
        assertEquals(
                0,
                ciEsperado.compareTo(rBase.costoIndirecto()),
                "CI must equal CD × %CI default (18%); got CI=" + rBase.costoIndirecto() + " CD="
                        + rBase.costoDirecto());

        // %CI default = 10% → CI = CD × 0.10 (with %HM fijo, sin MO adicional — el CD se mantiene)
        ParametrosCalculo p10 = new ParametrosCalculo(new BigDecimal("0.0500"), new BigDecimal("0.1000"));
        ApuCalculado r10 = Motor.calcularApu(snap, p10);
        BigDecimal ciEsperado10 =
                r10.costoDirecto().multiply(new BigDecimal("0.1000"), new MathContext(20, RoundingMode.HALF_UP));
        assertEquals(
                0,
                ciEsperado10.compareTo(r10.costoIndirecto()),
                "CI must equal CD × 10% when default is 10%; got CI=" + r10.costoIndirecto() + " CD="
                        + r10.costoDirecto());

        // CT = CD + CI (sin CI residual cuando pctCi = 0)
        ParametrosCalculo p0 = new ParametrosCalculo(new BigDecimal("0.0500"), BigDecimal.ZERO);
        ApuCalculado r0 = Motor.calcularApu(snap, p0);
        assertEquals(
                0,
                r0.costoTotal().compareTo(r0.costoDirecto()),
                "CT must equal CD when %CI=0; got CD=" + r0.costoDirecto() + " CT=" + r0.costoTotal());
    }

    /**
     * %CI=0 (both default and per-apu) → costoIndirecto == 0 and CT == CD.
     */
    @Property(tries = 100)
    void ci_cero_hace_CT_igual_a_CD(@ForAll("apuSnapshots") ApuSnapshot snap) {
        var p = new ParametrosCalculo(new BigDecimal("0.0500"), BigDecimal.ZERO);

        ApuCalculado r = Motor.calcularApu(snap, p);

        assertEquals(
                0,
                BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP).compareTo(r.costoIndirecto()),
                "costoIndirecto must be 0 when %CI=0");
        assertEquals(0, r.costoDirecto().compareTo(r.costoTotal()), "costoTotal must equal costoDirecto when CI=0");
    }

    /**
     * For auxiliar APUs (Plan 014: %CI override = ZERO on snapshot → CI = 0
     * regardless of project default).
     */
    @Property(tries = 100)
    void auxiliar_tiene_CI_cero(@ForAll("apuSnapshotsAuxiliares") ApuSnapshot aux) {
        var p = new ParametrosCalculo(
                new BigDecimal("0.0500"), new BigDecimal("0.2000")); // 20% CI — should still be ignored for auxiliar

        ApuCalculado r = Motor.calcularApu(aux, p);

        assertEquals(
                0,
                BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP).compareTo(r.costoIndirecto()),
                "auxiliar APU must always have costoIndirecto = 0");
    }

    /**
     * All monetary subtotals must be non-negative.
     */
    @Property(tries = 100)
    void subtotales_no_negativos(@ForAll("apuSnapshots") ApuSnapshot snap) {
        var p = baseParams();
        ApuCalculado r = Motor.calcularApu(snap, p);

        assertTrue(r.subtotalM().compareTo(BigDecimal.ZERO) >= 0, "subtotalM >= 0");
        assertTrue(r.subtotalN().compareTo(BigDecimal.ZERO) >= 0, "subtotalN >= 0");
        assertTrue(r.subtotalO().compareTo(BigDecimal.ZERO) >= 0, "subtotalO >= 0");
        assertTrue(r.subtotalP().compareTo(BigDecimal.ZERO) >= 0, "subtotalP >= 0");
        assertTrue(r.costoDirecto().compareTo(BigDecimal.ZERO) >= 0, "costoDirecto >= 0");
        assertTrue(r.costoTotal().compareTo(BigDecimal.ZERO) >= 0, "costoTotal >= 0");
    }

    /**
     * costoDirecto == subtotalM + subtotalN + subtotalO + subtotalP (additive identity).
     */
    @Property(tries = 100)
    void CD_igual_suma_subtotales(@ForAll("apuSnapshots") ApuSnapshot snap) {
        var p = baseParams();
        ApuCalculado r = Motor.calcularApu(snap, p);

        BigDecimal sumSubtotals = r.subtotalM()
                .add(r.subtotalN())
                .add(r.subtotalO())
                .add(r.subtotalP())
                .setScale(6, RoundingMode.HALF_UP);

        assertEquals(
                0,
                sumSubtotals.compareTo(r.costoDirecto()),
                "CD must equal M+N+O+P; got CD=" + r.costoDirecto() + " sum=" + sumSubtotals);
    }
}
