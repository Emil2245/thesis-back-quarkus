package ec.uce.propuestas.insumo.repository;

import ec.uce.propuestas.insumo.entity.UnidadCatalogo;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class UnidadCatalogoRepository implements PanacheRepositoryBase<UnidadCatalogo, String> {

    public List<UnidadCatalogo> listarTodos() {
        return find("order by codigo").list();
    }

    public List<UnidadCatalogo> buscarPorTexto(String q) {
        return find("codigo like ?1 or lower(descripcion) like ?2 order by codigo",
                "%" + q + "%", "%" + q.toLowerCase() + "%").list();
    }
}