package ec.uce.propuestas.presupuesto.dto;

import java.util.List;

public record ValidacionPresupuestoResponse(
        boolean exportable,
        List<RubroRefResponse> itemsPuCero,
        List<RubroRefResponse> itemsCantidadCero,
        List<RubroRefResponse> itemsSinActividad) {}
