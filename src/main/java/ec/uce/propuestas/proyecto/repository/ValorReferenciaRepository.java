package ec.uce.propuestas.proyecto.repository;

import ec.uce.propuestas.proyecto.entity.ValorReferencia;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ValorReferenciaRepository implements PanacheRepositoryBase<ValorReferencia, String> {

    public List<ValorReferencia> listar(int page, int size) {
        return find("order by clave").page(Page.of(page, size)).list();
    }

    public long contar() {
        return count();
    }

    public Optional<ValorReferencia> findByClave(String clave) {
        return findByIdOptional(clave);
    }
}
