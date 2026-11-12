package ec.uce.propuestas.cronograma.repository;

import ec.uce.propuestas.cronograma.entity.Cronograma;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Plan 027 — acceso a {@code cronograma}. El seam se mantiene finito:
 * consultas de datos aquí, reglas de negocio en el futuro service de
 * cronograma (I-08/028).
 *
 * * Owner-scope traversal: {@code Cronograma → Presupuesto → Proyecto →
 *   caller}. Cualquier fila ajena devuelve {@link Optional#empty()} (404
 *   semantics, nunca 403).
 * * El {@code publicId} nunca se reutiliza como FK ni como grant.
 * * Las firmas internas (Long) sirven a {@code recalculo}, al deep copy
 *   de versiones y a la query de cobertura de P-32.
 */
@ApplicationScoped
public class CronogramaRepository implements PanacheRepositoryBase<Cronograma, Long> {

    /**
     * Resolución por {@code publicId} (UUIDv7) con scope de owner. Una fila
     * de un proyecto ajeno devuelve {@link Optional#empty()}.
     */
    public Optional<Cronograma> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return getEntityManager()
                .createQuery(
                        "select cr from Cronograma cr, Presupuesto p, Proyecto pr "
                                + "where cr.publicId = :publicId and cr.presupuestoId = p.id "
                                + "and p.proyectoId = pr.id and pr.usuarioId = :caller",
                        Cronograma.class)
                .setParameter("publicId", publicId)
                .setParameter("caller", callerUsuarioId)
                .getResultList()
                .stream()
                .findFirst();
    }

    /**
     * Resolución por presupuesto interno (Long) con scope de owner — uso
     * interno (deep copy, write-through). Devuelve {@code Optional.empty()}
     * cuando el presupuesto pertenece a otro caller.
     */
    public Optional<Cronograma> findByPresupuestoAndOwnerScope(Long presupuestoId, Long callerUsuarioId) {
        return getEntityManager()
                .createQuery(
                        "select cr from Cronograma cr, Presupuesto p, Proyecto pr "
                                + "where cr.presupuestoId = :presupuestoId and cr.presupuestoId = p.id "
                                + "and p.proyectoId = pr.id and pr.usuarioId = :caller",
                        Cronograma.class)
                .setParameter("presupuestoId", presupuestoId)
                .setParameter("caller", callerUsuarioId)
                .getResultList()
                .stream()
                .findFirst();
    }

    /**
     * Lista de cronogramas para un conjunto de presupuestos (uso interno
     * de {@code recalculo} y de sincronía). El {@code List} resultante
     * mantiene la identidad administrada por Hibernate; el caller debe
     * tener cuidado con el flush.
     */
    public List<Cronograma> listarPorPresupuestos(List<Long> presupuestoIds) {
        if (presupuestoIds == null || presupuestoIds.isEmpty()) {
            return List.of();
        }
        return find("presupuestoId in :ids", Parameters.with("ids", presupuestoIds))
                .list();
    }
}
