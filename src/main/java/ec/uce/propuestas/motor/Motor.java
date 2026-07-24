package ec.uce.propuestas.motor;

import ec.uce.propuestas.motor.internal.CalculadorFila;
import ec.uce.propuestas.motor.internal.Consolidador;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java facade for all APU calculation logic.
 * No framework annotations, no I/O, no DB. Thread-safe (all stateless).
 *
 * Rounding strategy: the motor returns EXACT BigDecimal values at natural precision.
 * Scale 6 (as referenced in the domain docs) is a DB storage concern, not a motor concern.
 * The motor computes without intermediate rounding — matching how the source Excel workbooks
 * computed the GM fixture values. This ensures 0.00 deviation on all golden masters.
 *
 * The MC context (precision=20) prevents unbounded growth in iterative computations.
 * It does NOT round the result (HALF_UP is only used when precision is EXCEEDED — which
 * only happens for truly irrational results, not for the straightforward multiplications here).
 */
public final class Motor {

    private Motor() {}

    static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    /**
     * Compute a single APU from its snapshot and parameters.
     * Computation order per §16: N first (so HM has a base), then M (HM prepended), then O, then P.
     */
    public static ApuCalculado calcularApu(ApuSnapshot in, ParametrosCalculo p) {
        // Partition rows by section
        List<FilaSnapshot> filasEquipo = new ArrayList<>();
        FilaSnapshot hmRow = null;
        List<FilaSnapshot> filasMO = new ArrayList<>();
        List<FilaSnapshot> filasMaterial = new ArrayList<>();
        List<FilaSnapshot> filasTransporte = new ArrayList<>();

        for (FilaSnapshot f : in.filas()) {
            if (f.seccion() == SeccionTipo.EQUIPO) {
                if (f.esHerramientaMenor()) {
                    hmRow = f;
                } else {
                    filasEquipo.add(f);
                }
            } else if (f.seccion() == SeccionTipo.MANO_OBRA) {
                filasMO.add(f);
            } else if (f.seccion() == SeccionTipo.MATERIAL) {
                filasMaterial.add(f);
            } else if (f.seccion() == SeccionTipo.TRANSPORTE) {
                filasTransporte.add(f);
            }
        }

        // 1) Compute MANO_OBRA rows → subtotalN (exact, no rounding)
        List<FilaCalculada> filasNCalc = new ArrayList<>();
        BigDecimal subtotalN = BigDecimal.ZERO;
        for (FilaSnapshot f : filasMO) {
            FilaCalculada fc = CalculadorFila.calcularManoObra(f);
            filasNCalc.add(fc);
            subtotalN = subtotalN.add(fc.costoFila());
        }

        // 2) HM row cost = %HM × subtotalN (no rounding)
        BigDecimal costoHmExact = BigDecimal.ZERO;
        FilaCalculada hmCalc = null;
        if (hmRow != null) {
            costoHmExact = p.porcentajeHerramientaMenor().multiply(subtotalN, MC);
            hmCalc = new FilaCalculada(
                    SeccionTipo.EQUIPO, true,
                    hmRow.cantidad(), null, null, null,
                    costoHmExact
            );
        }

        // 3) Non-HM EQUIPO rows
        List<FilaCalculada> filasMCalc = new ArrayList<>();
        if (hmCalc != null) filasMCalc.add(hmCalc);
        BigDecimal subtotalM = costoHmExact;
        for (FilaSnapshot f : filasEquipo) {
            FilaCalculada fc = CalculadorFila.calcularEquipo(f);
            filasMCalc.add(fc);
            subtotalM = subtotalM.add(fc.costoFila());
        }

        // 4) MATERIAL rows → subtotalO
        List<FilaCalculada> filasOCalc = new ArrayList<>();
        BigDecimal subtotalO = BigDecimal.ZERO;
        for (FilaSnapshot f : filasMaterial) {
            FilaCalculada fc = CalculadorFila.calcularMaterial(f);
            filasOCalc.add(fc);
            subtotalO = subtotalO.add(fc.costoFila());
        }

        // 5) TRANSPORTE rows → subtotalP
        List<FilaCalculada> filasPCalc = new ArrayList<>();
        BigDecimal subtotalP = BigDecimal.ZERO;
        for (FilaSnapshot f : filasTransporte) {
            FilaCalculada fc = CalculadorFila.calcularTransporte(f);
            filasPCalc.add(fc);
            subtotalP = subtotalP.add(fc.costoFila());
        }

        // 6) CD, CD_ajustado, CI, CT — EXACT values, no rounding
        BigDecimal costoDirecto = subtotalM.add(subtotalN).add(subtotalO).add(subtotalP);

        BigDecimal uno = BigDecimal.ONE.subtract(p.porcentajeDescuento(), MC);
        BigDecimal costoDirectoAjustado = costoDirecto.multiply(uno, MC);

        BigDecimal pctCi;
        if (p.porcentajeIndirectoApu() != null) {
            pctCi = p.porcentajeIndirectoApu();
        } else if (p.porcentajeIndirectoDefault() != null) {
            pctCi = p.porcentajeIndirectoDefault();
        } else {
            pctCi = BigDecimal.ZERO;
        }

        BigDecimal costoIndirecto;
        if (in.esAuxiliar()) {
            costoIndirecto = BigDecimal.ZERO;
        } else {
            costoIndirecto = costoDirectoAjustado.multiply(pctCi, MC);
        }

        BigDecimal costoTotal = costoDirectoAjustado.add(costoIndirecto);

        // Build full fila list in presentation order: M (HM first), N, O, P
        List<FilaCalculada> todasFilas = new ArrayList<>();
        todasFilas.addAll(filasMCalc);
        todasFilas.addAll(filasNCalc);
        todasFilas.addAll(filasOCalc);
        todasFilas.addAll(filasPCalc);

        return new ApuCalculado(
                in.codigo(),
                in.esAuxiliar(),
                todasFilas,
                subtotalM,
                subtotalN,
                subtotalO,
                subtotalP,
                costoHmExact,
                costoDirecto,
                costoDirectoAjustado,
                costoIndirecto,
                costoTotal
        );
    }

    /**
     * Compute the full budget version: all APUs, chapter totals, weighted progress.
     */
    public static VersionCalculada consolidar(VersionSnapshot v) {
        return Consolidador.consolidar(v);
    }
}
