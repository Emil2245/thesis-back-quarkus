package ec.uce.propuestas.motor.internal;

import ec.uce.propuestas.motor.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;

/**
 * Package-private helper. Consolidates a full VersionSnapshot into a VersionCalculada.
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

        // 2. Build rubros with computed prices
        List<RubroConPrecio> rubros = new ArrayList<>();
        collectRubros(v.capitulosRaiz(), apusCalculados, rubros);

        // 3. Compute chapter totals (flat, in-order traversal)
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
                    : r.precioTotal().multiply(new BigDecimal("100"), MC)
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

        return new VersionCalculada(
                apusCalculados,
                rubros,
                capitulos,
                totalGeneral,
                pesos,
                avances
        );
    }

    private static void collectAndComputeApus(List<CapituloSnapshot> caps,
                                               ParametrosCalculo params,
                                               Map<String, ApuCalculado> result) {
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

    private static void collectRubros(List<CapituloSnapshot> caps,
                                       Map<String, ApuCalculado> apus,
                                       List<RubroConPrecio> result) {
        for (CapituloSnapshot cap : caps) {
            collectRubros(cap.subcapitulos(), apus, result);
            for (RubroSnapshot r : cap.rubros()) {
                ApuCalculado apu = apus.get(r.apu().codigo());
                BigDecimal precioUnitario = apu.costoTotal();
                BigDecimal precioTotal = r.cantidad()
                        .multiply(precioUnitario, MC)
                        .setScale(SCALE, RoundingMode.HALF_UP);
                result.add(new RubroConPrecio(r.codigo(), r.cantidad(), precioUnitario, precioTotal));
            }
        }
    }

    /**
     * Recursively compute chapter total (sum of all rubro precioTotal under this chapter).
     * Appends this chapter (and sub-chapters) to the flat list.
     */
    private static BigDecimal computeCapituloTotal(CapituloSnapshot cap,
                                                    Map<String, ApuCalculado> apus,
                                                    List<CapituloConTotal> result) {
        BigDecimal total = BigDecimal.ZERO;

        // Sub-chapters first
        for (CapituloSnapshot sub : cap.subcapitulos()) {
            BigDecimal subTotal = computeCapituloTotal(sub, apus, result);
            total = total.add(subTotal);
        }

        // Direct rubros
        for (RubroSnapshot r : cap.rubros()) {
            ApuCalculado apu = apus.get(r.apu().codigo());
            BigDecimal precioTotal = r.cantidad()
                    .multiply(apu.costoTotal(), MC)
                    .setScale(SCALE, RoundingMode.HALF_UP);
            total = total.add(precioTotal);
        }

        total = total.setScale(SCALE, RoundingMode.HALF_UP);
        result.add(new CapituloConTotal(cap.item(), cap.depth(), total));
        return total;
    }
}
