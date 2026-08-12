package ec.uce.propuestas.apu.repository;

import ec.uce.propuestas.apu.entity.ApuSeccion;
import ec.uce.propuestas.motor.SeccionTipo;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

/** Acceso a {@code apu_seccion}. Reglas de negocio en los services. */
@ApplicationScoped
public class ApuSeccionRepository implements PanacheRepositoryBase<ApuSeccion, Long> {

    public List<ApuSeccion> listarDeApu(Long apuId) {
        return find("apuId = :apuId order by orden", Parameters.with("apuId", apuId))
                .list();
    }

    public Optional<ApuSeccion> findByApuYTipo(Long apuId, SeccionTipo tipo) {
        return find(
                        "apuId = :apuId and tipo = :tipo",
                        Parameters.with("apuId", apuId).and("tipo", tipo))
                .firstResultOptional();
    }
}
