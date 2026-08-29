package ec.uce.propuestas.presupuesto.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.presupuesto.dto.CapituloCrearRequest;
import ec.uce.propuestas.presupuesto.dto.CapituloEditarRequest;
import ec.uce.propuestas.presupuesto.dto.CapituloMoverRequest;
import ec.uce.propuestas.presupuesto.dto.PresupuestoResponse;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.repository.CapituloRepository;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class CapituloService {

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    CapituloRepository capituloRepository;

    @Inject
    RecalculoService recalculoService;

    @Inject
    PresupuestoService presupuestoService;

    @Transactional
    public PresupuestoResponse crear(Long presupuestoId, CapituloCrearRequest req) {
        presupuestoService.validar(presupuestoId);

        if (req.parentId() != null) {
            Capitulo parent = capituloRepository
                    .findByPresupuestoAndId(presupuestoId, req.parentId())
                    .orElseThrow(() -> ProblemaException.noEncontrado("Capítulo padre no encontrado"));
        }

        short orden = req.orden() != null ? req.orden() : capituloRepository.nextOrden(presupuestoId, req.parentId());

        Capitulo cap = new Capitulo();
        cap.presupuestoId = presupuestoId;
        cap.parentId = req.parentId();
        cap.descripcion = req.descripcion();
        cap.orden = orden;
        cap.item = "temp";
        cap.total = BigDecimal.ZERO;
        capituloRepository.persist(cap);

        recalculoService.recalcular(presupuestoId);
        return presupuestoService.obtenerArbol(presupuestoId);
    }

    @Transactional
    public PresupuestoResponse editar(Long presupuestoId, Long capituloId, CapituloEditarRequest req) {
        Capitulo cap = capituloRepository
                .findByPresupuestoAndId(presupuestoId, capituloId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Capítulo no encontrado"));

        cap.descripcion = req.descripcion();
        capituloRepository.persist(cap);

        return presupuestoService.obtenerArbol(presupuestoId);
    }

    @Transactional
    public PresupuestoResponse mover(Long presupuestoId, Long capituloId, CapituloMoverRequest req) {
        Capitulo cap = capituloRepository
                .findByPresupuestoAndId(presupuestoId, capituloId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Capítulo no encontrado"));

        if (req.parentId() != null) {
            if (req.parentId().equals(capituloId)) {
                throw ProblemaException.validacion("Un capítulo no puede ser padre de sí mismo");
            }
            Capitulo parent = capituloRepository
                    .findByPresupuestoAndId(presupuestoId, req.parentId())
                    .orElseThrow(() -> ProblemaException.noEncontrado("Capítulo padre no encontrado"));

            // Validar que parent no es un descendiente de capituloId (prevención de ciclos)
            Long cursor = parent.parentId;
            Set<Long> visitados = new HashSet<>();
            while (cursor != null) {
                if (cursor.equals(capituloId)) {
                    throw ProblemaException.validacion("No se puede mover un capítulo a uno de sus descendientes");
                }
                if (!visitados.add(cursor)) break;
                Capitulo anc = capituloRepository.findById(cursor);
                cursor = anc != null ? anc.parentId : null;
            }
        }

        cap.parentId = req.parentId();
        cap.orden = req.orden();
        capituloRepository.persist(cap);

        recalculoService.recalcular(presupuestoId);
        return presupuestoService.obtenerArbol(presupuestoId);
    }

    @Transactional
    public PresupuestoResponse eliminar(Long presupuestoId, Long capituloId) {
        Capitulo cap = capituloRepository
                .findByPresupuestoAndId(presupuestoId, capituloId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Capítulo no encontrado"));

        capituloRepository.delete(cap);
        recalculoService.recalcular(presupuestoId);
        return presupuestoService.obtenerArbol(presupuestoId);
    }
}
