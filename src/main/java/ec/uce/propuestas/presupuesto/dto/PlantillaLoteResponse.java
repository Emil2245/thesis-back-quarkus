package ec.uce.propuestas.presupuesto.dto;

import ec.uce.propuestas.plantilla.dto.AdvertenciaPlantillaResponse;
import java.util.List;
import java.util.UUID;

public record PlantillaLoteResponse(PresupuestoResponse presupuesto, List<Resultado> resultados) {
    public record Resultado(
            UUID plantillaId,
            String plantillaNombre,
            UUID apuId,
            String codigo,
            List<AdvertenciaPlantillaResponse> advertencias) {}
}
