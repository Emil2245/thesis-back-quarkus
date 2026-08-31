package ec.uce.propuestas.presupuesto.repository;

import ec.uce.propuestas.presupuesto.entity.Capitulo;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.UUID;

/** Acceso a {@code capitulo}. Reglas de negocio en los services. */
@ApplicationScoped
public class CapituloRepository implements PanacheRepositoryBase<Capitulo, Long> {

    /**
     * WU-03 — Resolución por {@code public_id} (UUIDv7) con scope de owner. Travesía owner:
     * Capitulo → Presupuesto → Proyecto → caller. Cualquier fila de un proyecto ajeno devuelve
     * {@link Optional#empty()} (mapeo a 404 — nunca 403). El {@code public_id} nunca se
     * usa como FK ni como grant de autorización.
     */
    public Optional<Capitulo> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return getEntityManager()
                .createQuery(
                        "select c from Capitulo c, Presupuesto p, Proyecto pr "
                                + "where c.publicId = :publicId and c.presupuestoId = p.id "
                                + "and p.proyectoId = pr.id and pr.usuarioId = :caller",
                        Capitulo.class)
                .setParameter("publicId", publicId)
                .setParameter("caller", callerUsuarioId)
                .getResultList()
                .stream()
                .findFirst();
    }
}
