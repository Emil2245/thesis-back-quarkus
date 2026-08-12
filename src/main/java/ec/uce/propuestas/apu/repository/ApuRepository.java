package ec.uce.propuestas.apu.repository;

import ec.uce.propuestas.apu.entity.Apu;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

/** Acceso a {@code apu}. Reglas de negocio en los services. */
@ApplicationScoped
public class ApuRepository implements PanacheRepositoryBase<Apu, Long> {

    public Optional<Apu> findByIdYPresupuesto(Long id, Long presupuestoId) {
        return find("id = ?1 and presupuestoId = ?2", id, presupuestoId).firstResultOptional();
    }

    public Optional<Apu> findByPresupuestoYCodigo(Long presupuestoId, String codigo) {
        return find("presupuestoId = ?1 and codigo = ?2", presupuestoId, codigo).firstResultOptional();
    }

    public List<Apu> listarDePresupuesto(Long presupuestoId, String q, int pageIndex, int pageSize) {
        StringBuilder ql = new StringBuilder("presupuestoId = ?1");
        if (q != null && !q.isBlank()) {
            ql.append(" and (lower(codigo) like ?2 or lower(descripcion) like ?2)");
            return find(ql.toString(), presupuestoId, "%" + q.toLowerCase() + "%")
                    .page(Page.of(pageIndex, pageSize))
                    .list();
        }
        return find(ql.toString(), presupuestoId)
                .page(Page.of(pageIndex, pageSize))
                .list();
    }

    public long contarDePresupuesto(Long presupuestoId, String q) {
        if (q != null && !q.isBlank()) {
            return count(
                    "presupuestoId = ?1 and (lower(codigo) like ?2 or lower(descripcion) like ?2)",
                    presupuestoId,
                    "%" + q.toLowerCase() + "%");
        }
        return count("presupuestoId = ?1", presupuestoId);
    }

    /** D-09: el APU está vinculado a un rubro del presupuesto (1:1) sí/no. */
    public boolean estaVinculado(Long apuId) {
        Long n = (Long) getEntityManager()
                .createNativeQuery("select count(*) from rubro where apu_id = ?1")
                .setParameter(1, apuId)
                .getSingleResult();
        return n != null && n > 0;
    }

    /** Resuelve el proyecto dueño de una versión de presupuesto (RNF-05). */
    public Optional<Long> proyectoDePresupuesto(Long presupuestoId) {
        return getEntityManager()
                .createNativeQuery("select proyecto_id from presupuesto where id = ?1")
                .setParameter(1, presupuestoId)
                .getResultStream()
                .map(o -> ((Number) o).longValue())
                .findFirst();
    }

    /** Resuelve el proyecto dueño de un APU (vía su versión). */
    public Optional<Long> proyectoDeApu(Long apuId) {
        return getEntityManager()
                .createNativeQuery(
                        "select p.proyecto_id from apu a join presupuesto p on p.id = a.presupuesto_id where a.id = ?1")
                .setParameter(1, apuId)
                .getResultStream()
                .map(o -> ((Number) o).longValue())
                .findFirst();
    }
}
