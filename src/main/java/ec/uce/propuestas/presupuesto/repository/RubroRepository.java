package ec.uce.propuestas.presupuesto.repository;

import ec.uce.propuestas.presupuesto.entity.Rubro;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class RubroRepository implements PanacheRepositoryBase<Rubro, Long> {

    public List<Rubro> listByCapitulo(Long capituloId) {
        return find("capituloId = ?1 order by id asc", capituloId).list();
    }

    public List<Rubro> listByPresupuesto(Long presupuestoId) {
        return find(
                        "select r from Rubro r join Capitulo c on r.capituloId = c.id where c.presupuestoId = ?1 order by r.item asc",
                        presupuestoId)
                .list();
    }

    public Optional<Rubro> findByApuId(Long apuId) {
        return find("apuId", apuId).firstResultOptional();
    }

    public Optional<Rubro> findByCapituloAndId(Long capituloId, Long id) {
        return find("capituloId = ?1 and id = ?2", capituloId, id).firstResultOptional();
    }

    public long countByApuId(Long apuId) {
        return count("apuId", apuId);
    }
}
