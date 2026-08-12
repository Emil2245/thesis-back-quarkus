package ec.uce.propuestas.proyecto.repository;

import ec.uce.propuestas.proyecto.entity.Firmante;
import ec.uce.propuestas.proyecto.entity.RolFirmante;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FirmanteRepository implements PanacheRepositoryBase<Firmante, Long> {

    public List<Firmante> listarDeProyecto(Long proyectoId) {
        return find("proyectoId = ?1 order by orden", proyectoId).list();
    }

    public Optional<Firmante> findByIdYProyecto(Long id, Long proyectoId) {
        return find("id = ?1 and proyectoId = ?2", id, proyectoId).firstResultOptional();
    }

    public boolean existeRolOrden(Long proyectoId, RolFirmante rol, Short orden) {
        return count("proyectoId = ?1 and rol = ?2 and orden = ?3", proyectoId, rol, orden) > 0;
    }
}
