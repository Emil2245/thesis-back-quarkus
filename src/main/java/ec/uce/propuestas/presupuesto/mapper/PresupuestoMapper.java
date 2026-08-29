package ec.uce.propuestas.presupuesto.mapper;

import ec.uce.propuestas.presupuesto.dto.CapituloResponse;
import ec.uce.propuestas.presupuesto.dto.PresupuestoResponse;
import ec.uce.propuestas.presupuesto.dto.PresupuestoVersionResponse;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import java.util.List;

public final class PresupuestoMapper {

    private PresupuestoMapper() {}

    public static PresupuestoVersionResponse toVersionResponse(Presupuesto p) {
        return new PresupuestoVersionResponse(p.id, p.version, p.esVigente, p.origenId, p.notas, p.createdAt, p.total);
    }

    public static PresupuestoResponse toTreeResponse(Presupuesto p, List<CapituloResponse> capitulos) {
        return new PresupuestoResponse(p.id, p.version, p.esVigente, p.total, capitulos);
    }
}
