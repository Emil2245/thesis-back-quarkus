package ec.uce.propuestas.motor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Fully computed result for a budget version. All monetary values at scale 6. */
public record VersionCalculada(
        Map<String, ApuCalculado> apus,            // keyed by codigo, auxiliares computed first
        List<RubroConPrecio> rubros,
        List<CapituloConTotal> capitulos,           // in-order flat, with depth
        BigDecimal totalGeneral,
        List<PesoPonderado> pesosPonderados,
        List<AvancePeriodo> avancesAcumulados       // one per periodo; empty if no cronograma
) {
    public VersionCalculada {
        Objects.requireNonNull(apus, "apus must not be null");
        Objects.requireNonNull(rubros, "rubros must not be null");
        Objects.requireNonNull(capitulos, "capitulos must not be null");
        Objects.requireNonNull(totalGeneral, "totalGeneral must not be null");
        Objects.requireNonNull(pesosPonderados, "pesosPonderados must not be null");
        Objects.requireNonNull(avancesAcumulados, "avancesAcumulados must not be null");
        apus = Map.copyOf(apus);
        rubros = List.copyOf(rubros);
        capitulos = List.copyOf(capitulos);
        pesosPonderados = List.copyOf(pesosPonderados);
        avancesAcumulados = List.copyOf(avancesAcumulados);
    }
}
