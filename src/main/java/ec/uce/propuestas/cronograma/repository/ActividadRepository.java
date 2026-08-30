package ec.uce.propuestas.cronograma.repository;

import ec.uce.propuestas.cronograma.entity.Actividad;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class ActividadRepository implements PanacheRepositoryBase<Actividad, Long> {

    public List<Actividad> listByCronograma(Long cronogramaId) {
        return find("cronogramaId", cronogramaId).list();
    }
}
