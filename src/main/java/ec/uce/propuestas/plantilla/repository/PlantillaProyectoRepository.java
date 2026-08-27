package ec.uce.propuestas.plantilla.repository;

import ec.uce.propuestas.plantilla.entity.PlantillaProyecto;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PlantillaProyectoRepository implements PanacheRepositoryBase<PlantillaProyecto, Long> {

    /**
     * WU-03 — Resolución por {@code public_id} (UUIDv7) con scope de owner. El caller debe
     * ser el {@code usuarioId} de la plantilla. Cualquier otra fila devuelve
     * {@link Optional#empty()} (mapeo a 404).
     */
    public Optional<PlantillaProyecto> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return find(
                        "publicId = :publicId and usuarioId = :caller",
                        Parameters.with("publicId", publicId).and("caller", callerUsuarioId))
                .firstResultOptional();
    }
}
