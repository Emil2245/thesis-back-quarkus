package ec.uce.propuestas.proyecto.repository;

import ec.uce.propuestas.proyecto.entity.ParametrosSistema;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ParametrosSistemaRepository implements PanacheRepositoryBase<ParametrosSistema, Short> {

    // Singleton id=1 (V002 seed). Solo lectura.
}
