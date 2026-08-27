package ec.uce.propuestas.plantilla.repository;

import ec.uce.propuestas.plantilla.entity.PlantillaApu;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PlantillaApuRepository implements PanacheRepositoryBase<PlantillaApu, Long> {

    public Optional<PlantillaApu> findByCodigo(String codigo) {
        return find("codigo = :codigo", Parameters.with("codigo", codigo)).firstResultOptional();
    }

    /**
     * WU-03 — Resolución por {@code public_id} (UUIDv7) con scope de owner. Reglas de
     * visibilidad:
     * <ul>
     *   <li>{@code SISTEMA} → visible para todos los usuarios autenticados (no lleva owner)</li>
     *   <li>{@code PERSONAL} → caller == {@code usuarioId}</li>
     * </ul>
     * Una fila ajena devuelve {@link Optional#empty()} (mapeo a 404).
     */
    public Optional<PlantillaApu> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return getEntityManager()
                .createQuery(
                        "select pl from PlantillaApu pl "
                                + "where pl.publicId = :publicId "
                                + "and (pl.tipo = :tipoSistema or "
                                + "     (pl.tipo = :tipoPersonal and pl.usuarioId = :caller))",
                        PlantillaApu.class)
                .setParameter("publicId", publicId)
                .setParameter("caller", callerUsuarioId)
                .setParameter("tipoSistema", PlantillaApu.Tipo.SISTEMA)
                .setParameter("tipoPersonal", PlantillaApu.Tipo.PERSONAL)
                .getResultList()
                .stream()
                .findFirst();
    }
}
