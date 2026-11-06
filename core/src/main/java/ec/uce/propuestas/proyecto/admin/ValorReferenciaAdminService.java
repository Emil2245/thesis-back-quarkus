package ec.uce.propuestas.proyecto.admin;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.proyecto.dto.ValorReferenciaRequest;
import ec.uce.propuestas.proyecto.dto.ValorReferenciaResponse;
import ec.uce.propuestas.proyecto.entity.ValorReferencia;
import ec.uce.propuestas.proyecto.repository.ValorReferenciaRepository;
import ec.uce.propuestas.usuario.audit.EventoLogActividad;
import ec.uce.propuestas.usuario.audit.service.LogActividadService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Map;

@ApplicationScoped
public class ValorReferenciaAdminService {

    @Inject
    ValorReferenciaRepository repository;

    @Inject
    LogActividadService logActividadService;

    public Page<ValorReferenciaResponse> listar(int page, int size) {
        return Page.of(
                repository.listar(page, size).stream()
                        .map(ValorReferenciaResponse::from)
                        .toList(),
                repository.contar(),
                page,
                size);
    }

    @Transactional
    public UpsertResult upsert(Long usuarioId, String clave, ValorReferenciaRequest request) {
        ValorReferencia row = repository.findByClave(clave).orElse(null);
        boolean creada = row == null;
        if (creada) {
            row = new ValorReferencia();
            row.clave = clave;
        }
        row.valor = request.valor();
        row.descripcion = request.descripcion();
        row.fuente = request.fuente();
        row.updatedAt = Instant.now();
        if (creada) {
            repository.persist(row);
        }
        logActividadService.emitir(
                usuarioId,
                EventoLogActividad.ADMIN_PARAMETROS_EDITADOS,
                "valor_referencia",
                null,
                Map.of("operacion", creada ? "valor_referencia.insert" : "valor_referencia.update", "clave", clave));
        return new UpsertResult(ValorReferenciaResponse.from(row), creada);
    }

    @Transactional
    public void eliminar(Long usuarioId, String clave) {
        ValorReferencia row = repository
                .findByClave(clave)
                .orElseThrow(() -> ProblemaException.noEncontrado("Valor de referencia no encontrado"));
        repository.delete(row);
        logActividadService.emitir(
                usuarioId,
                EventoLogActividad.ADMIN_PARAMETROS_EDITADOS,
                "valor_referencia",
                null,
                Map.of("operacion", "valor_referencia.delete", "clave", clave));
    }

    public record UpsertResult(ValorReferenciaResponse response, boolean creada) {}
}
