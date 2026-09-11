package ec.uce.propuestas.presupuesto.service;

import ec.uce.propuestas.apu.dto.ApuCrearRequest;
import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.service.ApuCrudService;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.plantilla.entity.PlantillaApu;
import ec.uce.propuestas.plantilla.service.PlantillaApuService;
import ec.uce.propuestas.presupuesto.dto.PlantillaLoteRequest;
import ec.uce.propuestas.presupuesto.dto.PlantillaLoteResponse;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.repository.CapituloRepository;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.recalculo.Alcance;
import ec.uce.propuestas.recalculo.RecalculoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PlantillaLoteService {
    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    CapituloRepository capituloRepository;

    @Inject
    PlantillaApuService plantillaService;

    @Inject
    ApuCrudService apuService;

    @Inject
    RubroService rubroService;

    @Inject
    RecalculoService recalculoService;

    @Transactional
    public PlantillaLoteResponse aplicar(UUID presupuestoPublicId, PlantillaLoteRequest req, Long callerUsuarioId) {
        if (req == null
                || req.plantillaIds() == null
                || req.plantillaIds().isEmpty()
                || req.plantillaIds().size() > 20) {
            throw ProblemaException.validacion("plantillaIds debe contener entre 1 y 20 elementos");
        }
        if (new HashSet<>(req.plantillaIds()).size() != req.plantillaIds().size()) {
            throw ProblemaException.validacion("plantillaIds no puede contener duplicados");
        }
        Presupuesto presupuesto = presupuestoRepository
                .findByPublicIdOwnerScopeForUpdate(presupuestoPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
        Capitulo destino = resolverDestino(presupuesto.id, req.capituloId(), callerUsuarioId);
        List<PlantillaApu> plantillas = new java.util.ArrayList<>();
        for (int i = 0; i < req.plantillaIds().size(); i++) {
            UUID id = req.plantillaIds().get(i);
            if (id == null) {
                throw ProblemaException.conDetalles(
                        400, "validacion", "plantillaId inválido", java.util.Map.of("indice", i));
            }
            try {
                plantillas.add(plantillaService.cargarPorOwner(id, callerUsuarioId));
            } catch (ProblemaException e) {
                throw fallo(i, id, e);
            }
        }

        List<PlantillaLoteResponse.Resultado> resultados = new java.util.ArrayList<>();
        for (int i = 0; i < plantillas.size(); i++) {
            PlantillaApu plantilla = plantillas.get(i);
            try {
                ApuCrudService.ResultadoCrear creado = apuService.crearSinRecalculo(
                        presupuesto.id,
                        new ApuCrearRequest(
                                null,
                                plantilla.descripcionRubro == null ? plantilla.nombre : plantilla.descripcionRubro,
                                plantilla.unidad == null ? "u" : plantilla.unidad,
                                plantilla.publicId),
                        callerUsuarioId);
                // Resolve the managed APU by its public id through the response identity.
                Apu entidad = apuService.buscarPorPublicId(creado.apu().id());
                rubroService.crearRubroAppendOnlySinRecalculo(presupuesto, destino, entidad);
                resultados.add(new PlantillaLoteResponse.Resultado(
                        plantilla.publicId,
                        plantilla.nombre,
                        creado.apu().id(),
                        creado.apu().codigo(),
                        creado.advertencias()));
            } catch (ProblemaException e) {
                throw fallo(i, plantilla.publicId, e);
            } catch (RuntimeException e) {
                throw ProblemaException.conDetalles(
                        400,
                        "plantilla-lote-fallida",
                        "No se pudo agregar la plantilla seleccionada en la posición " + (i + 1) + ".",
                        java.util.Map.of("indice", i, "plantillaId", plantilla.publicId.toString()));
            }
        }
        recalculoService.recalcular(new Alcance.Version(presupuesto.id));
        return new PlantillaLoteResponse(rubroService.cargarArbol(presupuesto.id), resultados);
    }

    private ProblemaException fallo(int indice, UUID plantillaId, ProblemaException causa) {
        Object entity = causa.getResponse().getEntity();
        String codigo =
                entity instanceof ec.uce.propuestas.common.ErrorPayload p ? p.codigo() : "plantilla-lote-fallida";
        int status = causa.getResponse().getStatus();
        return ProblemaException.conDetalles(
                status,
                codigo,
                "No se pudo agregar la plantilla seleccionada en la posición " + (indice + 1) + ".",
                java.util.Map.of("indice", indice, "plantillaId", plantillaId.toString()));
    }

    private Capitulo resolverDestino(Long presupuestoId, UUID capituloPublicId, Long callerUsuarioId) {
        if (capituloPublicId != null) {
            return capituloRepository
                    .findByPublicIdEnPresupuesto(capituloPublicId, presupuestoId)
                    .orElseThrow(() -> ProblemaException.noEncontrado("Capítulo no encontrado"));
        }
        Capitulo cursor = capituloRepository
                .ultimoHijo(presupuestoId, null)
                .orElseThrow(() ->
                        ProblemaException.conflicto("presupuesto-sin-capitulos", "El presupuesto no tiene capítulos"));
        while (true) {
            Capitulo siguiente =
                    capituloRepository.ultimoHijo(presupuestoId, cursor.id).orElse(null);
            if (siguiente == null) return cursor;
            cursor = siguiente;
        }
    }
}
