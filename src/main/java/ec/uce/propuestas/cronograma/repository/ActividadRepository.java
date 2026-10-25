package ec.uce.propuestas.cronograma.repository;

import ec.uce.propuestas.common.ItemJerarquico;
import ec.uce.propuestas.cronograma.entity.Actividad;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Comparator;
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
     * Lista {@code [Actividad, Rubro, capitulo.item]} en orden canónico e
     * independiente de IDs. El item del capítulo se incluye para construir el fingerprint
     * presupuestario sin columnas espejo ni una consulta N+1.
     */
    public List<Object[]> listarConRubroPorCronograma(Long cronogramaId) {
        List<Object[]> filas = getEntityManager()
                .createQuery(
                        "select a, r, c.item from Actividad a, Rubro r, Capitulo c "
                                + "where a.cronogramaId = :cronogramaId and a.rubroId = r.id "
                                + "and r.capituloId = c.id "
                                + "order by r.codigo, r.precioTotal",
                        Object[].class)
                .setParameter("cronogramaId", cronogramaId)
                .getResultList();
        // Plan 023-A: orden natural ("1.2" antes de "1.10") en Java; ORDER BY item de SQL es lexicografico.
        filas.sort(Comparator.comparing((Object[] f) -> (String) f[2], ItemJerarquico.ORDEN)
                .thenComparing(f -> ((Rubro) f[1]).item, ItemJerarquico.ORDEN));
        return filas;
    }

    /** Filas presupuestarias completas para el fingerprint, incluso sin actividad. */
    public List<Object[]> listarSnapshotPresupuesto(Long presupuestoId) {
        List<Object[]> filas = getEntityManager()
                .createQuery(
                        "select c.item, r.item, r.codigo, r.precioTotal "
                                + "from Rubro r, Capitulo c "
                                + "where r.capituloId = c.id and c.presupuestoId = :presupuestoId "
                                + "order by r.codigo, r.precioTotal",
                        Object[].class)
                .setParameter("presupuestoId", presupuestoId)
                .getResultList();
        filas.sort(Comparator.comparing((Object[] f) -> (String) f[0], ItemJerarquico.ORDEN)
                .thenComparing(f -> (String) f[1], ItemJerarquico.ORDEN));
        return filas;
    }

    /**
     * Lista las actividades del cronograma dado (uso interno de write-through
     * y deep copy). El Long interno no se filtra al exterior — el recurso
     * 028+ expone UUIDv7.
     */
    public List<Actividad> listarPorCronograma(Long cronogramaId) {
        List<Object[]> filas = getEntityManager()
                .createQuery(
                        "select a, r.item from Actividad a, Rubro r "
                                + "where a.cronogramaId = :cronogramaId and a.rubroId = r.id "
                                + "order by a.id",
                        Object[].class)
                .setParameter("cronogramaId", cronogramaId)
                .getResultList();
        filas.sort(Comparator.comparing((Object[] f) -> (String) f[1], ItemJerarquico.ORDEN));
        return filas.stream().map(f -> (Actividad) f[0]).toList();
    }
}
