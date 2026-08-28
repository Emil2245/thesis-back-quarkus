package ec.uce.propuestas.motor.internal;

import ec.uce.propuestas.motor.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package-private helper. Consolidates a full VersionSnapshot into a VersionCalculada.
 *
 * <p><b>Rounding policy (Plan 014, workbook-consistent 2026-08-28):</b>
 * the motor returns EXACT {@code BigDecimal} values at natural precision.
 * The unico rounding inside the motor es la <b>frontera APU -&gt; Rubro</b>
 * aplicada en {@link #precioTotalFrontera}:
 * <ul>
 *   <li>{@code precioUnitario = apu.costoTotal().setScale(2, RoundingMode.DOWN)}
 *       (unica aplicacion de {@code DOWN}; reproduce el workbook IESS).</li>
 *   <li>{@code precioTotal = cantidad * precioUnitario} retenido a
 *       <b>escala 6 {@code HALF_UP}</b> (escala de persistencia
 *       {@code NUMERIC(14,6)}); <b>no</b> se trunca cada {@code precioTotal}
 *       a 2 dp.</li>
 * </ul>
 * Capitulo y {@code totalGeneral} agregan esos {@code precioTotal} a
 * escala 6 (sin recomputar desde {@code apu.costoTotal} sin redondear); la
 * presentacion/assertion canonica a 2 dp {@code HALF_UP} ocurre solo en
 * display/assertion.
 */
public final class Consolidador {

    private Consolidador() {}

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);
    private static final int SCALE = 6;
    private static final int SCALE_PCT = 4;

    public static VersionCalculada consolidar(VersionSnapshot v) {
        ParametrosCalculo params = v.parametrosProyecto();

        // 1. Collect all unique APUs from rubros in the version (auxiliares first by snapshot contract)
        //    Compute each APU once.
        Map<String, ApuCalculado> apusCalculados = new LinkedHashMap<>();
        collectAndComputeApus(v.capitulosRaiz(), params, apusCalculados);

        // 2. Build rubros with computed prices (frontera APU→Rubro DOWN 2dp).
        List<RubroConPrecio> rubros = new ArrayList<>();
        collectRubros(v.capitulosRaiz(), apusCalculados, rubros);

        // 3. Compute chapter totals (flat, in-order traversal).
        //    Aggregate the same already-rounded rubro precioTotal (no recomputation
        //    from raw apu.costoTotal).
        List<CapituloConTotal> capitulos = new ArrayList<>();
        BigDecimal totalGeneral = BigDecimal.ZERO;
        for (CapituloSnapshot cap : v.capitulosRaiz()) {
            BigDecimal capTotal = computeCapituloTotal(cap, apusCalculados, capitulos);
            totalGeneral = totalGeneral.add(capTotal);
        }
        totalGeneral = totalGeneral.setScale(SCALE, RoundingMode.HALF_UP);

        // 4. Compute peso ponderado per rubro
        List<PesoPonderado> pesos = new ArrayList<>();
        for (RubroConPrecio r : rubros) {
            BigDecimal peso = totalGeneral.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : r.precioTotal()
                                .multiply(new BigDecimal("100"), MC)
                                .divide(totalGeneral, SCALE_PCT, RoundingMode.HALF_UP);
            pesos.add(new PesoPonderado(r.codigo(), peso));
        }

        // 5. Compute schedule advances if cronograma present
        List<AvancePeriodo> avances = new ArrayList<>();
        if (v.cronograma() != null) {
            CronogramaSnapshot cron = v.cronograma();
            // Build a lookup: rubroCodigo → pesoPonderado
            Map<String, BigDecimal> pesoPorRubro = new LinkedHashMap<>();
            for (PesoPonderado pp : pesos) {
                pesoPorRubro.put(pp.rubroCodigo(), pp.pesoPct());
            }
            for (int t = 1; t <= cron.numeroPeriodos(); t++) {
                BigDecimal avanceAcumulado = BigDecimal.ZERO;
                for (ActividadSnapshot act : cron.actividades()) {
                    BigDecimal avancePct = act.avancePorPeriodo().getOrDefault(t, BigDecimal.ZERO);
                    avanceAcumulado = avanceAcumulado.add(avancePct);
                }
                avances.add(new AvancePeriodo(t, avanceAcumulado.setScale(SCALE_PCT, RoundingMode.HALF_UP)));
            }
        }

        return new VersionCalculada(apusCalculados, rubros, capitulos, totalGeneral, pesos, avances);
    }

    private static void collectAndComputeApus(
            List<CapituloSnapshot> caps, ParametrosCalculo params, Map<String, ApuCalculado> result) {
        for (CapituloSnapshot cap : caps) {
            collectAndComputeApus(cap.subcapitulos(), params, result);
            for (RubroSnapshot rubro : cap.rubros()) {
                String cod = rubro.apu().codigo();
                if (!result.containsKey(cod)) {
                    result.put(cod, Motor.calcularApu(rubro.apu(), params));
                }
            }
        }
    }

    /**
     * Shared helper for the frontera APU → Rubro — regla workbook-consistent
     * (corrección plan 014, 2026-08-28).
     *
     * <ul>
     *   <li>{@code precioUnitario = apu.costoTotal().setScale(2, RoundingMode.DOWN)}
     *       (única aplicación de {@code DOWN}; reproduce el workbook IESS donde
     *       el precio unitario tipeado se trunca a 2 dp).</li>
     *   <li>{@code precioTotal = cantidad × precioUnitario}, retenido a la escala
     *       de persistencia 6 ({@code NUMERIC(14,6)}) con {@code HALF_UP} aplicado
     *       únicamente en esta frontera de resultado. <b>No</b> se trunca cada
     *       {@code precioTotal} a 2 dp: el workbook IESS multiplica
     *       {@code cantidad × PU_2dp} a precisión completa y suma a escala
     *       completa antes de presentar a 2 dp.</li>
     * </ul>
     *
     * <p>Usado por {@link #collectRubros} (para poblar {@code RubroConPrecio})
     * y por {@link #computeCapituloTotal} (para agregar totales de capítulo),
     * garantizando que la suma de los totales de capítulo sea exactamente la
     * suma de los mismos {@code precioTotal} a escala 6.
     */
    private static BigDecimal precioTotalFrontera(RubroSnapshot r, ApuCalculado apu) {
        BigDecimal precioUnitario = apu.costoTotal().setScale(2, RoundingMode.DOWN);
        return r.cantidad().multiply(precioUnitario, MC).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static void collectRubros(
            List<CapituloSnapshot> caps, Map<String, ApuCalculado> apus, List<RubroConPrecio> result) {
        for (CapituloSnapshot cap : caps) {
            collectRubros(cap.subcapitulos(), apus, result);
            for (RubroSnapshot r : cap.rubros()) {
                ApuCalculado apu = apus.get(r.apu().codigo());
                BigDecimal precioUnitario = apu.costoTotal().setScale(2, RoundingMode.DOWN);
                BigDecimal precioTotal = precioTotalFrontera(r, apu);
                result.add(new RubroConPrecio(r.codigo(), r.cantidad(), precioUnitario, precioTotal));
            }
        }
    }

    /**
     * Recursively compute chapter total aggregating the SAME {@code precioTotal}
     * (derived from the DOWN-rounded unit price and retained at scale 6)
     * that was produced by the frontera in
     * {@link #collectRubros}. This guarantees that {@code totalGeneral} = sum of
     * chapter totals = sum of rubro {@code precioTotal}, all at the same scale.
     *
     * <p>Appends this chapter (and sub-chapters) to the flat list.
     */
    private static BigDecimal computeCapituloTotal(
            CapituloSnapshot cap, Map<String, ApuCalculado> apus, List<CapituloConTotal> result) {
        BigDecimal total = BigDecimal.ZERO;

        // Sub-chapters first
        for (CapituloSnapshot sub : cap.subcapitulos()) {
            BigDecimal subTotal = computeCapituloTotal(sub, apus, result);
            total = total.add(subTotal);
        }

        // Direct rubros — use the SAME frontera formula so the sum matches
        // the per-rubro RubroConPrecio produced by collectRubros.
        for (RubroSnapshot r : cap.rubros()) {
            ApuCalculado apu = apus.get(r.apu().codigo());
            BigDecimal precioTotal = precioTotalFrontera(r, apu);
            total = total.add(precioTotal);
        }

        total = total.setScale(SCALE, RoundingMode.HALF_UP);
        result.add(new CapituloConTotal(cap.item(), cap.depth(), total));
        return total;
    }
}
