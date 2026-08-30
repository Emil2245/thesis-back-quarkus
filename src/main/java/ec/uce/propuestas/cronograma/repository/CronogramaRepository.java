package ec.uce.propuestas.cronograma.repository;

import ec.uce.propuestas.cronograma.entity.Cronograma;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class CronogramaRepository implements PanacheRepositoryBase<Cronograma, Long> {

    public Optional<Cronograma> findByPresupuesto(Long presupuestoId) {
        return find("presupuestoId", presupuestoId).firstResultOptional();
    }
}
