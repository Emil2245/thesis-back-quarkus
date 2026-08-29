package ec.uce.propuestas.presupuesto.repository;

import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PresupuestoRepository implements PanacheRepositoryBase<Presupuesto, Long> {

    public List<Presupuesto> listByProyecto(Long proyectoId) {
        return find("proyectoId = ?1 order by version asc", proyectoId).list();
    }

    public Optional<Presupuesto> findVigente(Long proyectoId) {
        return find("proyectoId = ?1 and esVigente = true", proyectoId).firstResultOptional();
    }

    public Optional<Presupuesto> findByProyectoYId(Long proyectoId, Long id) {
        return find("proyectoId = ?1 and id = ?2", proyectoId, id).firstResultOptional();
    }

    public short nextVersion(Long proyectoId) {
        Short max = find("select coalesce(max(version), 0) from Presupuesto where proyectoId = ?1", proyectoId)
                .project(Short.class)
                .firstResult();
        return (short) ((max == null ? 0 : max) + 1);
    }
}
