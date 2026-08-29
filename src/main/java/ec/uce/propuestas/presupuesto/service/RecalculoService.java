package ec.uce.propuestas.presupuesto.service;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.service.ApuCalculoService;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.repository.CapituloRepository;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;

@ApplicationScoped
public class RecalculoService {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    CapituloRepository capituloRepository;

    @Inject
    RubroRepository rubroRepository;

    @Inject
    ApuRepository apuRepository;

    @Inject
    ApuCalculoService apuCalculoService;

    @Transactional
    public void recalcular(Long presupuestoId) {
        Presupuesto presupuesto = presupuestoRepository
                .findByIdOptional(presupuestoId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));

        // 1. Recalcular todos los APUs del presupuesto
        List<Apu> apus = apuRepository.list("presupuestoId", presupuestoId);
        Map<Long, Apu> apuMap = new HashMap<>();
        for (Apu apu : apus) {
            apuCalculoService.recalcular(apu);
            apuMap.put(apu.id, apu);
        }

        // 2. Renumerar capítulos y rubros
        renumerarJerarquia(presupuestoId);

        // 3. Recalcular rubros con precios de APUs
        List<Rubro> rubros = rubroRepository.listByPresupuesto(presupuestoId);
        for (Rubro rubro : rubros) {
            Apu apu = apuMap.get(rubro.apuId);
            if (apu == null) {
                apu = apuRepository.findById(rubro.apuId);
            }
            if (apu != null) {
                rubro.codigo = apu.codigo;
                rubro.descripcion = apu.descripcion;
                rubro.unidad = apu.unidad;
                rubro.precioUnitario = apu.costoTotal;
                rubro.precioTotal = rubro.cantidad.multiply(apu.costoTotal, MC).setScale(6, RoundingMode.HALF_UP);
                rubroRepository.persist(rubro);
            }
        }

        // 4. Recalcular totales de capítulos (bottom-up)
        List<Capitulo> todosCaps = capituloRepository.listByPresupuesto(presupuestoId);
        Map<Long, List<Capitulo>> hijosPorPadre = new HashMap<>();
        for (Capitulo c : todosCaps) {
            hijosPorPadre.computeIfAbsent(c.parentId, k -> new ArrayList<>()).add(c);
        }

        BigDecimal totalGeneral = BigDecimal.ZERO;
        List<Capitulo> raices = hijosPorPadre.getOrDefault(null, Collections.emptyList());
        for (Capitulo raiz : raices) {
            BigDecimal totalCap = calcularTotalSubarbol(raiz, hijosPorPadre);
            totalGeneral = totalGeneral.add(totalCap);
        }

        presupuesto.total = totalGeneral.setScale(6, RoundingMode.HALF_UP);
        presupuestoRepository.persist(presupuesto);
    }

    private BigDecimal calcularTotalSubarbol(Capitulo cap, Map<Long, List<Capitulo>> hijosPorPadre) {
        BigDecimal total = BigDecimal.ZERO;

        // Subcapítulos
        List<Capitulo> hijos = hijosPorPadre.getOrDefault(cap.id, Collections.emptyList());
        for (Capitulo hijo : hijos) {
            total = total.add(calcularTotalSubarbol(hijo, hijosPorPadre));
        }

        // Rubros directos
        List<Rubro> rubros = rubroRepository.listByCapitulo(cap.id);
        for (Rubro r : rubros) {
            if (r.precioTotal != null) {
                total = total.add(r.precioTotal);
            }
        }

        cap.total = total.setScale(6, RoundingMode.HALF_UP);
        capituloRepository.persist(cap);
        return cap.total;
    }

    @Transactional
    public void renumerarJerarquia(Long presupuestoId) {
        List<Capitulo> todosCaps = capituloRepository.listByPresupuesto(presupuestoId);
        Map<Long, List<Capitulo>> hijosPorPadre = new HashMap<>();
        for (Capitulo c : todosCaps) {
            hijosPorPadre.computeIfAbsent(c.parentId, k -> new ArrayList<>()).add(c);
        }

        for (List<Capitulo> list : hijosPorPadre.values()) {
            list.sort(Comparator.comparingInt(c -> c.orden == null ? 0 : c.orden));
        }

        List<Capitulo> raices = hijosPorPadre.getOrDefault(null, Collections.emptyList());
        for (int i = 0; i < raices.size(); i++) {
            Capitulo raiz = raices.get(i);
            raiz.orden = (short) (i + 1);
            renumerarNodo(raiz, String.valueOf(i + 1), hijosPorPadre);
        }
    }

    private void renumerarNodo(Capitulo cap, String prefix, Map<Long, List<Capitulo>> hijosPorPadre) {
        cap.item = prefix;
        capituloRepository.persist(cap);

        List<Rubro> rubros = rubroRepository.listByCapitulo(cap.id);
        for (int i = 0; i < rubros.size(); i++) {
            Rubro r = rubros.get(i);
            r.item = prefix + "." + (i + 1);
            rubroRepository.persist(r);
        }

        List<Capitulo> hijos = hijosPorPadre.getOrDefault(cap.id, Collections.emptyList());
        for (int i = 0; i < hijos.size(); i++) {
            Capitulo hijo = hijos.get(i);
            hijo.orden = (short) (i + 1);
            renumerarNodo(hijo, prefix + "." + (i + 1), hijosPorPadre);
        }
    }
}
