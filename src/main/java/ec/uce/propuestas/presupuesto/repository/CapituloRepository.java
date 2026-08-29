package ec.uce.propuestas.presupuesto.repository;

import ec.uce.propuestas.presupuesto.entity.Capitulo;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class CapituloRepository implements PanacheRepositoryBase<Capitulo, Long> {

    public List<Capitulo> listByPresupuesto(Long presupuestoId) {
        return find("presupuestoId = ?1 order by orden asc", presupuestoId).list();
    }

    public List<Capitulo> listByPresupuestoAndParent(Long presupuestoId, Long parentId) {
        if (parentId == null) {
            return find("presupuestoId = ?1 and parentId is null order by orden asc", presupuestoId)
                    .list();
        }
        return find("presupuestoId = ?1 and parentId = ?2 order by orden asc", presupuestoId, parentId)
                .list();
    }

    public Optional<Capitulo> findByPresupuestoAndId(Long presupuestoId, Long id) {
        return find("presupuestoId = ?1 and id = ?2", presupuestoId, id).firstResultOptional();
    }

    public short nextOrden(Long presupuestoId, Long parentId) {
        Short max = parentId == null
                ? find(
                                "select coalesce(max(orden), 0) from Capitulo where presupuestoId = ?1 and parentId is null",
                                presupuestoId)
                        .project(Short.class)
                        .firstResult()
                : find(
                                "select coalesce(max(orden), 0) from Capitulo where presupuestoId = ?1 and parentId = ?2",
                                presupuestoId,
                                parentId)
                        .project(Short.class)
                        .firstResult();
        return (short) ((max == null ? 0 : max) + 1);
    }
}
