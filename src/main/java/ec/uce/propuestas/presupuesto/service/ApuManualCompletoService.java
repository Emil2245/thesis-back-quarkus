package ec.uce.propuestas.presupuesto.service;

import ec.uce.propuestas.apu.dto.ApuCrearRequest;
import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.service.ApuCalculoService;
import ec.uce.propuestas.apu.service.ApuCrudService;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.presupuesto.dto.ApuManualCompletoRequest;
import ec.uce.propuestas.presupuesto.dto.ApuManualCompletoResponse;
import ec.uce.propuestas.presupuesto.dto.PresupuestoResponse;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.repository.CapituloRepository;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.recalculo.Alcance;
import ec.uce.propuestas.recalculo.RecalculoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;

/** Atomic orchestration for creating a complete manual APU and its rubro. */
@ApplicationScoped
public class ApuManualCompletoService {

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    CapituloRepository capituloRepository;

    @Inject
    ApuCrudService apuService;

    @Inject
    ApuCalculoService apuCalculoService;

    @Inject
    RubroService rubroService;

    @Inject
    RecalculoService recalculoService;

    @Transactional
    public ApuManualCompletoResponse crear(
            UUID presupuestoPublicId, ApuManualCompletoRequest req, Long callerUsuarioId) {
        if (req == null || req.detalles() == null || req.detalles().isEmpty()) {
            throw ProblemaException.validacion("detalles debe contener al menos una fila");
        }

        UUID presupuestoId = UuidV7.parse(presupuestoPublicId.toString());
        UUID capituloPublicId =
                req.capituloId() == null ? null : UuidV7.parse(req.capituloId().toString());
        Presupuesto presupuesto = presupuestoRepository
                .findByPublicIdOwnerScopeForUpdate(presupuestoId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
        Capitulo destino = resolverDestino(presupuesto.id, capituloPublicId, callerUsuarioId);

        ApuCrearRequest cabecera = new ApuCrearRequest(req.codigo(), req.descripcion(), req.unidad());
        Apu apu = apuService.crearApuVacioSinRecalculo(presupuesto.id, cabecera, req.porcentajeIndirecto());
        for (var detalle : req.detalles()) {
            apuService.agregarDetalleEnLote(apu.id, detalle, callerUsuarioId);
        }

        rubroService.crearRubroAppendOnlySinRecalculo(presupuesto, destino, apu);
        // The APU cache must be write-through before the version consolidation so
        // both GET /apus/{id} and the rubro use the same calculated values.
        apuCalculoService.recalcular(apu);
        recalculoService.recalcular(new Alcance.Version(presupuesto.id));
        apuService.emitirCreacionManual(apu);

        PresupuestoResponse presupuestoResponse = rubroService.cargarArbol(presupuesto.id);
        return new ApuManualCompletoResponse(apuService.respuestaCompleta(apu), presupuestoResponse);
    }

    private Capitulo resolverDestino(Long presupuestoId, UUID capituloPublicId, Long callerUsuarioId) {
        if (capituloPublicId != null) {
            return capituloRepository
                    .findByPublicIdEnPresupuesto(capituloPublicId, presupuestoId)
                    .orElseGet(() -> {
                        if (capituloRepository.existeEnOtroPresupuestoDelOwner(
                                capituloPublicId, presupuestoId, callerUsuarioId)) {
                            throw ProblemaException.validacion("El capítulo pertenece a otro presupuesto");
                        }
                        throw ProblemaException.noEncontrado("Capítulo no encontrado");
                    });
        }

        Capitulo cursor = capituloRepository
                .ultimoHijo(presupuestoId, null)
                .orElseThrow(() ->
                        ProblemaException.conflicto("presupuesto-sin-capitulos", "El presupuesto no tiene capítulos"));
        while (true) {
            Capitulo siguiente =
                    capituloRepository.ultimoHijo(presupuestoId, cursor.id).orElse(null);
            if (siguiente == null) {
                return cursor;
            }
            cursor = siguiente;
        }
    }
}
