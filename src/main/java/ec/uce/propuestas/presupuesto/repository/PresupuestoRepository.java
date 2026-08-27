package ec.uce.propuestas.presupuesto.repository;

import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PresupuestoRepository implements PanacheRepositoryBase<Presupuesto, Long> {

    /**
     * WU-03 — Resolución por {@code public_id} (UUIDv7) con scope de owner. Travesía owner:
     * Presupuesto → Proyecto → caller. Cualquier fila de un proyecto ajeno devuelve
     * {@link Optional#empty()} (mapeo a 404 — nunca 403). El {@code public_id} nunca se
     * usa como FK ni como grant de autorización.
     */
    public Optional<Presupuesto> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return getEntityManager()
                .createQuery(
                        "select p from Presupuesto p, Proyecto pr "
                                + "where p.publicId = :publicId and p.proyectoId = pr.id "
                                + "and pr.usuarioId = :caller",
                        Presupuesto.class)
                .setParameter("publicId", publicId)
                .setParameter("caller", callerUsuarioId)
                .getResultList()
                .stream()
                .findFirst();
    }

    /** Variante con proyecto resuelto (sigue siendo owner-scoped). */
    public Optional<Presupuesto> findByPublicIdAndProyecto(Long proyectoId, UUID publicId, Long callerUsuarioId) {
        return getEntityManager()
                .createQuery(
                        "select p from Presupuesto p, Proyecto pr "
                                + "where p.publicId = :publicId and p.proyectoId = pr.id "
                                + "and pr.id = :proyectoId and pr.usuarioId = :caller",
                        Presupuesto.class)
                .setParameter("publicId", publicId)
                .setParameter("proyectoId", proyectoId)
                .setParameter("caller", callerUsuarioId)
                .getResultList()
                .stream()
                .findFirst();
    }

    public Optional<Presupuesto> findVigenteDeProyecto(Long proyectoId) {
        return find("proyectoId = :proyectoId and esVigente = true", Parameters.with("proyectoId", proyectoId))
                .firstResultOptional();
    }
}
