package ec.uce.propuestas.proyecto.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.proyecto.dto.ParametrosProyectoEditarRequest;
import ec.uce.propuestas.proyecto.dto.ParametrosProyectoResponse;
import ec.uce.propuestas.proyecto.dto.ParametrosSistemaEditarRequest;
import ec.uce.propuestas.proyecto.entity.ParametrosProyecto;
import ec.uce.propuestas.proyecto.entity.ParametrosSistema;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.mapper.ParametrosProyectoMapper;
import ec.uce.propuestas.proyecto.repository.ParametrosProyectoRepository;
import ec.uce.propuestas.proyecto.repository.ParametrosSistemaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class ParametrosProyectoService {

    @Inject
    ParametrosProyectoRepository parametrosRepository;

    @Inject
    ParametrosSistemaRepository sistemaRepository;

    @Inject
    ProyectoService proyectoService;

    /** Plan 07 — el caller llega con el {@code publicId} UUIDv7 del proyecto. */
    @Transactional
    public ParametrosProyectoResponse leer(Long usuarioId, UUID proyectoPublicId) {
        Proyecto proyecto = proyectoService.validarPropietario(usuarioId, proyectoPublicId);
        ParametrosProyecto p = obtenerOCrear(proyecto.id);
        return ParametrosProyectoMapper.toResponse(p, proyecto);
    }

    @Transactional
    public ParametrosProyectoCambio actualizar(
            Long usuarioId, UUID proyectoPublicId, ParametrosProyectoEditarRequest req) {
        Proyecto proyecto = proyectoService.validarPropietario(usuarioId, proyectoPublicId);
        ParametrosSistema sistema = leerSistema();
        validarRangoDinamico(
                req.porcentajeHerramientaMenor(), sistema.rangoHmMin, sistema.rangoHmMax, "porcentajeHerramientaMenor");
        if (req.porcentajeIndirecto() != null) {
            validarRangoDinamico(
                    req.porcentajeIndirecto(), sistema.rangoCiMin, sistema.rangoCiMax, "porcentajeIndirecto");
        }
        validarRangoDinamico(req.iva(), sistema.rangoIvaMin, sistema.rangoIvaMax, "iva");

        ParametrosProyecto p = obtenerOCrear(proyecto.id);
        // Snapshot numérico previo: escala-insensible (compareTo) y null-safe.
        BigDecimal hmPrevio = p.porcentajeHerramientaMenor;
        BigDecimal ciPrevio = p.porcentajeIndirecto;
        p.porcentajeHerramientaMenor = req.porcentajeHerramientaMenor();
        p.porcentajeIndirecto = req.porcentajeIndirecto();
        p.iva = req.iva();
        p.moneda = req.moneda();
        parametrosRepository.persist(p);
        return new ParametrosProyectoCambio(
                proyecto.publicId,
                cambioNumerico(ciPrevio, p.porcentajeIndirecto),
                cambioNumerico(hmPrevio, p.porcentajeHerramientaMenor),
                ParametrosProyectoMapper.toResponse(p, proyecto));
    }

    /** True si {@code solicitado} difiere numéricamente de {@code previo}. Null-safe. */
    private static boolean cambioNumerico(BigDecimal previo, BigDecimal solicitado) {
        if (previo == null && solicitado == null) return false;
        if (previo == null || solicitado == null) return true;
        return previo.compareTo(solicitado) != 0;
    }

    /** Crea la fila de parámetros con defaults si aún no existe (tabla de uno-a-uno). */
    @Transactional
    public ParametrosProyecto obtenerOCrear(Long proyectoId) {
        ParametrosProyecto p = parametrosRepository.findById(proyectoId);
        if (p == null) {
            p = new ParametrosProyecto();
            p.proyectoId = proyectoId;
            parametrosRepository.persist(p);
        }
        return p;
    }

    /** Parámetros globales — lectura pública. */
    public ParametrosSistema leerSistema() {
        ParametrosSistema s = sistemaRepository.findById((short) 1);
        if (s == null) throw ProblemaException.noEncontrado("Parámetros de sistema no inicializados");
        return s;
    }

    /** Mutación restringida a SUPER_ADMIN de los parámetros globales, incluidos los rangos configurables. */
    @Transactional
    public ParametrosSistema actualizarSistema(ParametrosSistemaEditarRequest req) {
        validarParesDeRangos(req);
        ParametrosSistema s = leerSistema();
        s.porcentajeHerramientaMenor = req.porcentajeHerramientaMenor();
        s.porcentajeIndirecto = req.porcentajeIndirecto();
        s.iva = req.iva();
        s.rangoHmMin = req.rangoHmMin();
        s.rangoHmMax = req.rangoHmMax();
        s.rangoCiMin = req.rangoCiMin();
        s.rangoCiMax = req.rangoCiMax();
        s.rangoDescuentoMin = req.rangoDescuentoMin();
        s.rangoDescuentoMax = req.rangoDescuentoMax();
        s.rangoIvaMin = req.rangoIvaMin();
        s.rangoIvaMax = req.rangoIvaMax();
        if (req.moneda() != null && !req.moneda().isBlank()) {
            s.moneda = req.moneda();
        }
        s.updatedAt = Instant.now();
        sistemaRepository.persist(s);
        return s;
    }

    private static void validarParesDeRangos(ParametrosSistemaEditarRequest req) {
        validarMinNoMayorQueMax(req.rangoHmMin(), req.rangoHmMax(), "rangoHm");
        validarMinNoMayorQueMax(req.rangoCiMin(), req.rangoCiMax(), "rangoCi");
        validarMinNoMayorQueMax(req.rangoDescuentoMin(), req.rangoDescuentoMax(), "rangoDescuento");
        validarMinNoMayorQueMax(req.rangoIvaMin(), req.rangoIvaMax(), "rangoIva");
    }

    private static void validarMinNoMayorQueMax(BigDecimal min, BigDecimal max, String campo) {
        if (min == null || max == null) {
            throw ProblemaException.validacion("Rango " + campo + " incompleto");
        }
        if (min.compareTo(max) > 0) {
            throw ProblemaException.validacion("Rango " + campo + ": min no puede ser mayor que max");
        }
    }

    private static void validarRangoDinamico(BigDecimal valor, BigDecimal min, BigDecimal max, String campo) {
        if (valor == null) return;
        if (min != null && valor.compareTo(min) < 0) {
            throw ProblemaException.validacion(campo + " está por debajo del mínimo permitido (" + min + ")");
        }
        if (max != null && valor.compareTo(max) > 0) {
            throw ProblemaException.validacion(campo + " supera el máximo permitido (" + max + ")");
        }
    }
}
