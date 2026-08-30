package ec.uce.propuestas.admin.service;

import ec.uce.propuestas.admin.entity.LogActividad;
import ec.uce.propuestas.admin.repository.LogActividadRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Map;

@ApplicationScoped
public class LogService {

    @Inject
    LogActividadRepository logRepository;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void registrar(Long usuarioId, String evento, String entidad, Long entidadId, Map<String, Object> detalle) {
        LogActividad log = new LogActividad();
        log.usuarioId = usuarioId;
        log.evento = evento;
        log.entidad = entidad;
        log.entidadId = entidadId;
        log.detalle = detalle;
        logRepository.persist(log);
    }
}
