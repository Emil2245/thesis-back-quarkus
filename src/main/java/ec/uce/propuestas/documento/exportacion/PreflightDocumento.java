package ec.uce.propuestas.documento.exportacion;

import ec.uce.propuestas.presupuesto.dto.RubroRefResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PreflightDocumento(
        UUID presupuestoId,
        short version,
        String documento,
        String formato,
        Map<String, String> opciones,
        boolean exportable,
        List<Detalle> bloqueos,
        List<Detalle> warnings) {
    public PreflightDocumento {
        opciones = Map.copyOf(opciones);
        bloqueos = List.copyOf(bloqueos);
        warnings = List.copyOf(warnings);
    }

    public record Detalle(String codigo, String mensaje, List<RubroRefResponse> rubros) {
        public Detalle {
            rubros = List.copyOf(rubros);
        }
    }
}
