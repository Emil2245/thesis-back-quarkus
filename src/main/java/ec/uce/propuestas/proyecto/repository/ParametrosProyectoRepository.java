package ec.uce.propuestas.proyecto.repository;

import ec.uce.propuestas.proyecto.entity.ParametrosProyecto;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ParametrosProyectoRepository implements PanacheRepositoryBase<ParametrosProyecto, Long> {

    // PK propia: proyecto_id. PanacheRepositoryBase<ParametrosProyecto, Long> lo maneja.
}