package ec.uce.propuestas.proyecto.repository;

import ec.uce.propuestas.proyecto.entity.PlantillaProyecto;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class PlantillaProyectoRepository implements PanacheRepositoryBase<PlantillaProyecto, Long> {

    public List<PlantillaProyecto> listByUsuario(Long usuarioId) {
        return find("usuarioId = ?1 order by createdAt desc", usuarioId).list();
    }
}
