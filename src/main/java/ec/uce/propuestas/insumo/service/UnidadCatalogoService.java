package ec.uce.propuestas.insumo.service;

import ec.uce.propuestas.insumo.entity.UnidadCatalogo;
import ec.uce.propuestas.insumo.repository.UnidadCatalogoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/** Catálogo de unidades (P-13/P-16). Solo lectura. */
@ApplicationScoped
public class UnidadCatalogoService {

    @Inject
    UnidadCatalogoRepository unidadCatalogoRepository;

    public List<String> listarCodigos(String q) {
        List<UnidadCatalogo> unidades = (q == null || q.isBlank())
                ? unidadCatalogoRepository.listarTodos()
                : unidadCatalogoRepository.buscarPorTexto(q);
        return unidades.stream().map(u -> u.codigo + " - " + u.descripcion).toList();
    }
}