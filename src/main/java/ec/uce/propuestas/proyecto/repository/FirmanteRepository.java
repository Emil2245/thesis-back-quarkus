package ec.uce.propuestas.proyecto.repository;

import ec.uce.propuestas.proyecto.entity.Firmante;
import ec.uce.propuestas.proyecto.entity.RolFirmante;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class FirmanteRepository implements PanacheRepositoryBase<Firmante, Long> {

    public List<Firmante> listarDeProyecto(Long proyectoId) {
        return find("proyectoId = ?1 order by orden", proyectoId).list();
    }

    /** Variante interna (BIGINT) — usada por código interno y tests legacy. */
    public Optional<Firmante> findByIdYProyecto(Long id, Long proyectoId) {
        return find("id = ?1 and proyectoId = ?2", id, proyectoId).firstResultOptional();
    }

    /**
     * Plan 07 — resolución por {@code publicId} (UUIDv7) dentro del scope del
     * proyecto (ya validado por owner). La junta al {@code BIGINT} interno es la
     * única ruta usada para escribir/editar a partir de este punto.
     */
    public Optional<Firmante> findByPublicIdAndProyecto(UUID publicId, Long proyectoId) {
        return find(
                        "publicId = :publicId and proyectoId = :proyectoId",
                        Parameters.with("publicId", publicId).and("proyectoId", proyectoId))
                .firstResultOptional();
    }

    public boolean existeRolOrden(Long proyectoId, RolFirmante rol, Short orden) {
        return count("proyectoId = ?1 and rol = ?2 and orden = ?3", proyectoId, rol, orden) > 0;
    }

    /**
     * WU-03 — Resolución por {@code public_id} (UUIDv7) con scope de owner. El firmante pertenece
     * al proyecto dueño; el caller debe ser el dueño del proyecto que contiene al firmante. Filas
     * de proyectos ajenos devuelven {@link Optional#empty()}.
     */
    public Optional<Firmante> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return getEntityManager()
                .createQuery(
                        "select f from Firmante f, Proyecto p "
                                + "where f.publicId = :publicId and f.proyectoId = p.id and p.usuarioId = :caller",
                        Firmante.class)
                .setParameter("publicId", publicId)
                .setParameter("caller", callerUsuarioId)
                .getResultList()
                .stream()
                .findFirst();
    }
}
