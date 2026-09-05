package ec.uce.propuestas.cronograma.repository;

import ec.uce.propuestas.cronograma.entity.Actividad;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Plan 027 — acceso a {@code actividad}. Travesía owner:
 * {@code Actividad → Cronograma → Presupuesto → Proyecto → caller}.
 */
@ApplicationScoped
public class ActividadRepository implements PanacheRepositoryBase<Actividad, Long> {

    /**
     * Resolución por {@code publicId} (UUIDv7) con scope de owner. Una fila
     * de un proyecto ajeno devuelve {@link Optional#empty()}.
     */
    public Optional<Actividad> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return findByPublicIdAndCronogramaAndOwnerScope(publicId, null, callerUsuarioId);
    }

    /**
     * Resolución owner-scoped que además restringe la actividad al UUID público
     * del cronograma anidado en el path. Si {@code cronogramaPublicId} es
     * {@code null}, conserva la variante de resolución global usada internamente.
     */
    public Optional<Actividad> findByPublicIdAndCronogramaAndOwnerScope(
            UUID publicId, UUID cronogramaPublicId, Long callerUsuarioId) {
        String cronogramaScope = cronogramaPublicId == null ? "" : "and cr.publicId = :cronogramaPublicId ";
        var query = getEntityManager()
                .createQuery(
                        "select a from Actividad a, Cronograma cr, Presupuesto p, Proyecto pr "
                                + "where a.publicId = :publicId and a.cronogramaId = cr.id "
                                + cronogramaScope
                                + "and cr.presupuestoId = p.id and p.proyectoId = pr.id "
                                + "and pr.usuarioId = :caller",
                        Actividad.class)
                .setParameter("publicId", publicId)
                .setParameter("caller", callerUsuarioId);
        if (cronogramaPublicId != null) {
            query.setParameter("cronogramaPublicId", cronogramaPublicId);
        }
        return query.getResultList().stream().findFirst();
    }

    /**
     * Lista las actividades del cronograma dado (uso interno de write-through
     * y deep copy). El Long interno no se filtra al exterior — el recurso
     * 028+ expone UUIDv7.
     */
    public List<Actividad> listarPorCronograma(Long cronogramaId) {
        return getEntityManager()
                .createQuery(
                        "select a from Actividad a, Rubro r "
                                + "where a.cronogramaId = :cronogramaId and a.rubroId = r.id "
                                + "order by r.item, a.id",
                        Actividad.class)
                .setParameter("cronogramaId", cronogramaId)
                .getResultList();
    }
}
