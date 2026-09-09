package ec.uce.propuestas.proyecto.repository;

import ec.uce.propuestas.proyecto.entity.ParametrosSistema;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class ParametrosSistemaRepository implements PanacheRepositoryBase<ParametrosSistema, Short> {

    public ParametrosSistema lockSingleton() {
        return findById((short) 1, LockModeType.PESSIMISTIC_WRITE);
    }
}
