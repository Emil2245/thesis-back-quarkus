package ec.uce.propuestas.motor;

import net.jqwik.api.*;
import net.jqwik.api.constraints.Positive;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
                    FilaSnapshot mo = new FilaSnapshot(
                            SeccionTipo.MANO_OBRA, false,
                            BigDecimal.ONE,
                            rend,
                            jornal, null, null);
                    FilaSnapshot hm = new FilaSnapshot(
                            SeccionTipo.EQUIPO, true,
                            new BigDecimal("5"), null, null, null, null);
                    FilaSnapshot mat = new FilaSnapshot(
                            SeccionTipo.MATERIAL, false,
                            BigDecimal.ONE,
                            null,
                            matPrecio, null, null);
                    return new ApuSnapshot("PROP-TEST", false, List.of(hm, mo, mat));
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
            FilaSnapshot mat = new FilaSnapshot(
                    SeccionTipo.MATERIAL, false,
                    BigDecimal.ONE, null, matPrecio, null, null);
            return new ApuSnapshot("AUX-PROP", true, List.of(mat));
        });
    }

    /** Generate a valid ParametrosCalculo with no per-apu override, no discount. */
    private ParametrosCalculo baseParams() {
        return new ParametrosCalculo(
                new BigDecimal("0.0500"),
                new BigDecimal("0.1800"),
                null,
                BigDecimal.ZERO
        );
    }

    // ── Properties ─────────────────────────────────────────────────────────

    /**
     * descuento=0 → CD does not change; descuento=10% → CD_ajustado < CD.
     * The costoDirecto is independent of descuento.
     */
    @Property(tries = 100)
    void descuento_no_cambia_CD(@ForAll("apuSnapshots") ApuSnapshot snap) {
        var p0 = new ParametrosCalculo(
                new BigDecimal("0.0500"), new BigDecimal("0.1800"), null, BigDecimal.ZERO);
        var pDisc = new ParametrosCalculo(
                new BigDecimal("0.0500"), new BigDecimal("0.1800"), null, new BigDecimal("0.1000"));

        ApuCalculado r0 = Motor.calcularApu(snap, p0);
        ApuCalculado rD = Motor.calcularApu(snap, pDisc);

        // CD is independent of descuento
        assertEquals(0, r0.costoDirecto().compareTo(rD.costoDirecto()),
                "costoDirecto must not change when only descuento changes");

        // CD_ajustado decreases with discount
        assertTrue(rD.costoDirectoAjustado().compareTo(r0.costoDirectoAjustado()) < 0,
                "CD_ajustado with 10% descuento must be less than without descuento");
    }

    /**
     * %CI=0 (both default and per-apu) → costoIndirecto == 0 and CT == CD_ajustado.
     */
    @Property(tries = 100)
    void ci_cero_hace_CT_igual_a_CDajustado(@ForAll("apuSnapshots") ApuSnapshot snap) {
        var p = new ParametrosCalculo(
                new BigDecimal("0.0500"),
                BigDecimal.ZERO,   // porcentajeIndirectoDefault = 0
                null,
                BigDecimal.ZERO
        );

        ApuCalculado r = Motor.calcularApu(snap, p);

        assertEquals(0, BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP).compareTo(r.costoIndirecto()),
                "costoIndirecto must be 0 when %CI=0");
        assertEquals(0, r.costoDirectoAjustado().compareTo(r.costoTotal()),
                "costoTotal must equal costoDirectoAjustado when CI=0");
    }

    /**
     * For auxiliar APUs: costoIndirecto == 0 always, regardless of %CI.
     */
    @Property(tries = 100)
    void auxiliar_tiene_CI_cero(@ForAll("apuSnapshotsAuxiliares") ApuSnapshot aux) {
        var p = new ParametrosCalculo(
                new BigDecimal("0.0500"),
                new BigDecimal("0.2000"),  // 20% CI — should still be ignored for auxiliar
                null,
                BigDecimal.ZERO
        );

        ApuCalculado r = Motor.calcularApu(aux, p);

        assertEquals(0, BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP).compareTo(r.costoIndirecto()),
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

        assertEquals(0, sumSubtotals.compareTo(r.costoDirecto()),
                "CD must equal M+N+O+P; got CD=" + r.costoDirecto() + " sum=" + sumSubtotals);
    }
}
