package ec.uce.propuestas.presupuesto.repository;

import ec.uce.propuestas.presupuesto.entity.Rubro;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.UUID;

/** Acceso a {@code rubro}. Reglas de negocio en los services. */
@ApplicationScoped
public class RubroRepository implements PanacheRepositoryBase<Rubro, Long> {

    /**
     * WU-03 — Resolución por {@code public_id} (UUIDv7) con scope de owner. Travesía owner:
     * Rubro → Capitulo → Presupuesto → Proyecto → caller. Cualquier fila de un proyecto
     * ajeno devuelve {@link Optional#empty()} (mapeo a 404 — nunca 403). El {@code public_id}
     * nunca se usa como FK ni como grant de autorización.
     */
    public Optional<Rubro> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return getEntityManager()
                .createQuery(
                        "select r from Rubro r, Capitulo c, Presupuesto p, Proyecto pr "
                                + "where r.publicId = :publicId and r.capituloId = c.id "
                                + "and c.presupuestoId = p.id and p.proyectoId = pr.id "
                                + "and pr.usuarioId = :caller",
                        Rubro.class)
                .setParameter("publicId", publicId)
                .setParameter("caller", callerUsuarioId)
                .getResultList()
                .stream()
                .findFirst();
    }
}
