package ec.uce.propuestas.insumo.repository;

import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.TipoBase;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class BaseInsumosRepository implements PanacheRepositoryBase<BaseInsumos, Long> {

    public Optional<BaseInsumos> findByProyecto(Long proyectoId) {
        return find("proyectoId = ?1", proyectoId).firstResultOptional();
    }

    public List<BaseInsumos> listarCentralesActivas() {
        return find("tipo = ?1 and archivada = false", TipoBase.CENTRAL).list();
    }
}