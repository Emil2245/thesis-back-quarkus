package ec.uce.propuestas.plantilla.repository;

import ec.uce.propuestas.plantilla.entity.PlantillaApu;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PlantillaApuRepository implements PanacheRepositoryBase<PlantillaApu, Long> {

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

    /**
     * Plan 04 (P-26) — Listado de plantillas por tipo, orden estable por nombre.
     */
    public List<PlantillaApu> listarPorTipo(PlantillaApu.Tipo tipo) {
        return find("tipo = :tipo order by nombre", Parameters.with("tipo", tipo)).list();
    }

    /**
     * Plan 04 (P-26) — Listado de plantillas PERSONAL del caller, orden estable
     * por nombre.
     */
    public List<PlantillaApu> listarPorTipoYDuenno(PlantillaApu.Tipo tipo, Long usuarioId) {
        return find(
                        "tipo = :tipo and usuarioId = :uid order by nombre",
                        Parameters.with("tipo", tipo).and("uid", usuarioId))
                .list();
    }
}