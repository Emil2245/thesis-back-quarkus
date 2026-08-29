package ec.uce.propuestas.presupuesto.service;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.presupuesto.dto.PresupuestoResponse;
import ec.uce.propuestas.presupuesto.dto.RubroCrearRequest;
import ec.uce.propuestas.presupuesto.dto.RubroPatchRequest;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.repository.CapituloRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.MathContext;
import java.math.RoundingMode;

@ApplicationScoped
public class RubroService {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    @Inject
    CapituloRepository capituloRepository;

    @Inject
    RubroRepository rubroRepository;

    @Inject
    ApuRepository apuRepository;

    @Inject
    RecalculoService recalculoService;

    @Inject
    PresupuestoService presupuestoService;

    @Transactional
    public PresupuestoResponse crear(Long presupuestoId, Long capituloId, RubroCrearRequest req) {
        Capitulo cap = capituloRepository
                .findByPresupuestoAndId(presupuestoId, capituloId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Capítulo no encontrado"));

        Apu apu = apuRepository
                .findByIdOptional(req.apuId())
                .orElseThrow(() -> ProblemaException.noEncontrado("APU no encontrado"));

        if (!apu.presupuestoId.equals(presupuestoId)) {
            throw ProblemaException.validacion("El APU debe pertenecer a la misma versión del presupuesto");
        }

        if (rubroRepository.findByApuId(apu.id).isPresent()) {
            throw ProblemaException.apuReferenciado("El APU ya está vinculado a otro rubro en esta versión");
        }

        Rubro r = new Rubro();
        r.capituloId = cap.id;
        r.apuId = apu.id;
        r.item = "temp";
        r.codigo = apu.codigo;
        r.descripcion = apu.descripcion;
        r.unidad = apu.unidad;
        r.cantidad = req.cantidad();
        r.precioUnitario = apu.costoTotal;
        r.precioTotal = req.cantidad().multiply(apu.costoTotal, MC).setScale(6, RoundingMode.HALF_UP);
        rubroRepository.persist(r);

        recalculoService.recalcular(presupuestoId);
        return presupuestoService.obtenerArbol(presupuestoId);
    }

    @Transactional
    public PresupuestoResponse editar(Long presupuestoId, Long capituloId, Long rubroId, RubroPatchRequest req) {
        capituloRepository
                .findByPresupuestoAndId(presupuestoId, capituloId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Capítulo no encontrado"));

        Rubro r = rubroRepository
                .findByCapituloAndId(capituloId, rubroId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Rubro no encontrado"));

        r.cantidad = req.cantidad();
        rubroRepository.persist(r);

        recalculoService.recalcular(presupuestoId);
        return presupuestoService.obtenerArbol(presupuestoId);
    }

    @Transactional
    public PresupuestoResponse eliminar(Long presupuestoId, Long capituloId, Long rubroId) {
        capituloRepository
                .findByPresupuestoAndId(presupuestoId, capituloId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Capítulo no encontrado"));

        Rubro r = rubroRepository
                .findByCapituloAndId(capituloId, rubroId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Rubro no encontrado"));

        rubroRepository.delete(r);
        recalculoService.recalcular(presupuestoId);
        return presupuestoService.obtenerArbol(presupuestoId);
    }
}
