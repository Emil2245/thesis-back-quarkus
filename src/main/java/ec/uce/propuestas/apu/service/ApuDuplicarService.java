package ec.uce.propuestas.apu.service;

import ec.uce.propuestas.apu.dto.ApuResponse;
import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.apu.entity.ApuSeccion;
import ec.uce.propuestas.apu.repository.ApuDetalleRepository;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.repository.ApuSeccionRepository;
import ec.uce.propuestas.common.ProblemaException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;

/**
 * Duplicación profunda de un APU dentro de la misma versión de presupuesto (dossier §B.7 opción a).
 * Preserva cabecera (descripción, unidad, override %CI, %descuento, ET si {@code copiarET}),
 * las 4 secciones M/N/O/P y todas las filas (orden, cantidades, rendimiento, overrides, insumo IDs,
 * fila HM). El código se regenera como {@code APU-{n}} único dentro del presupuesto.
 *
 * <p>El ownership lo valida {@link ec.uce.propuestas.apu.resource.ApuResource#validarAcceso}
 * antes de invocar este servicio (RNF-05).
 *
 * <p>Tras persistir, recalcula write-through vía {@link ApuCalculoService} para que los
 * totales reflejen las mismas filas que el original (mismas filas → mismo resultado).
 */
@ApplicationScoped
public class ApuDuplicarService {

    @Inject
    ApuRepository apuRepository;

    @Inject
    ApuSeccionRepository seccionRepository;

    @Inject
    ApuDetalleRepository detalleRepository;

    @Inject
    ApuCrudService apuCrudService;

    @Inject
    ApuCalculoService calculoService;

    @Transactional
    public ApuResponse duplicar(Long apuOrigenId, Boolean copiarETRaw) {
        if (apuOrigenId == null) {
            throw ProblemaException.noEncontrado("APU no encontrado");
        }
        Apu origen = apuRepository.findById(apuOrigenId);
        if (origen == null) {
            throw ProblemaException.noEncontrado("APU no encontrado");
        }
        boolean copiarET = copiarETRaw != null && copiarETRaw;

        Apu copia = new Apu();
        copia.presupuestoId = origen.presupuestoId;
        copia.codigo = generarCodigoUnico(origen.presupuestoId);
        copia.descripcion = origen.descripcion;
        copia.unidad = origen.unidad;
        copia.porcentajeIndirecto = origen.porcentajeIndirecto;
        copia.porcentajeDescuento = origen.porcentajeDescuento == null ? BigDecimal.ZERO : origen.porcentajeDescuento;
        copia.especificacionTecnica = copiarET ? origen.especificacionTecnica : null;
        apuRepository.persist(copia);

        copiarSeccionesYFilas(origen.id, copia.id);
        calculoService.recalcular(copia);
        return apuCrudService.respuestaCompleta(copia);
    }

    private void copiarSeccionesYFilas(Long apuOrigenId, Long apuCopiaId) {
        List<ApuSeccion> secciones = seccionRepository.listarDeApu(apuOrigenId);
        for (ApuSeccion src : secciones) {
            ApuSeccion dst = new ApuSeccion();
            dst.apuId = apuCopiaId;
            dst.tipo = src.tipo;
            dst.orden = src.orden;
            seccionRepository.persist(dst);

            List<ApuDetalle> detalles = detalleRepository.listarDeSeccion(src.id);
            for (ApuDetalle d : detalles) {
                ApuDetalle copia = new ApuDetalle();
                copia.seccionId = dst.id;
                copia.insumoId = d.insumoId;
                copia.descripcion = d.descripcion;
                copia.orden = d.orden;
                copia.esHerramientaMenor = d.esHerramientaMenor;
                copia.cantidad = d.cantidad;
                copia.rendimiento = d.rendimiento;
                copia.tarifaJornal = d.tarifaJornal;
                copia.precioUnitarioTarifa = d.precioUnitarioTarifa;
                copia.unidad = d.unidad;
                detalleRepository.persist(copia);
            }
        }
    }

    /**
     * Genera {@code APU-{n}} dentro del mismo presupuesto, incrementando {@code n} hasta
     * encontrar un código libre (provisión de I-05 documentada en 03-apu.md §1).
     */
    String generarCodigoUnico(Long presupuestoId) {
        long n = apuRepository.contarDePresupuesto(presupuestoId, null) + 1;
        String codigo;
        do {
            codigo = String.format("APU-%03d", n);
            if (apuRepository.findByPresupuestoYCodigo(presupuestoId, codigo).isEmpty()) {
                return codigo;
            }
            n++;
        } while (true);
    }
}
