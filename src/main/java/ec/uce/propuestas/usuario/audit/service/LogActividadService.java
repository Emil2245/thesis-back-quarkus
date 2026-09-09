package ec.uce.propuestas.usuario.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.usuario.audit.EventoLogActividad;
import ec.uce.propuestas.usuario.audit.LogActividadDetalleValidator;
import ec.uce.propuestas.usuario.audit.dto.LogActividadFiltros;
import ec.uce.propuestas.usuario.audit.dto.LogActividadResponse;
import ec.uce.propuestas.usuario.audit.entity.LogActividad;
import ec.uce.propuestas.usuario.audit.repository.LogActividadRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Transaction-bound emitter and read facade for D-13 activity events. */
@ApplicationScoped
public class LogActividadService {

    private static final TypeReference<Map<String, Object>> DETALLE_TYPE = new TypeReference<>() {};

    @Inject
    LogActividadRepository repository;

    @Inject
    ObjectMapper objectMapper;

    @Transactional(Transactional.TxType.MANDATORY)
    public LogActividad emitir(
            Long usuarioId, EventoLogActividad evento, String entidad, UUID entidadId, Map<String, Object> detalle) {
        if (evento == null) {
            throw new IllegalArgumentException("Evento de log requerido");
        }
        Map<String, Object> detalleSeguro = detalle == null ? Map.of() : new LinkedHashMap<>(detalle);
        LogActividadDetalleValidator.validar(evento, detalleSeguro);

        LogActividad row = new LogActividad();
        row.usuarioId = usuarioId;
        row.evento = evento.value();
        row.entidad = entidad;
        row.entidadPublicId = entidadId;
        row.detalle = serializarDetalle(detalleSeguro);
        repository.persist(row);
        return row;
    }

    @Transactional
    public Page<LogActividadResponse> listar(LogActividadFiltros filtros) {
        LogActividadRepository.ResultadoPagina resultado = repository.listar(filtros);
        return Page.of(
                resultado.items().stream()
                        .map(log -> LogActividadResponse.from(log, deserializarDetalle(log.detalle)))
                        .toList(),
                resultado.total(),
                filtros.page(),
                filtros.size());
    }

    private String serializarDetalle(Map<String, Object> detalle) {
        try {
            return objectMapper.writeValueAsString(detalle);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el detalle del log", ex);
        }
    }

    private Map<String, Object> deserializarDetalle(String detalle) {
        if (detalle == null || detalle.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(detalle, DETALLE_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo leer el detalle del log", ex);
        }
    }
}
