package ec.uce.propuestas.proyecto.service;

import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.proyecto.dto.ParametrosProyectoCiRequest;
import ec.uce.propuestas.proyecto.dto.ParametrosProyectoCiRequest.PoliticaOverrides;
import ec.uce.propuestas.proyecto.dto.ParametrosProyectoCiResponse;
import ec.uce.propuestas.proyecto.entity.ParametrosProyecto;
import ec.uce.propuestas.proyecto.entity.ParametrosSistema;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.repository.ParametrosProyectoRepository;
import ec.uce.propuestas.recalculo.RecalculoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

/** Transactional orchestration for project-level CI settings and affected APUs. */
@ApplicationScoped
public class ProyectoCiService {

    @Inject
    ProyectoService proyectoService;

    @Inject
    ParametrosProyectoService parametrosService;

    @Inject
    ParametrosProyectoRepository parametrosRepository;

    @Inject
    ApuRepository apuRepository;

    @Inject
    RecalculoService recalculoService;

    @Transactional
    public ParametrosProyectoCiResponse leer(Long usuarioId, UUID proyectoPublicId) {
        Proyecto proyecto = proyectoService.validarPropietario(usuarioId, proyectoPublicId);
        ParametrosProyecto parametros = parametrosService.obtenerOCrear(proyecto.id);
        return respuesta(proyecto, parametros);
    }

    @Transactional
    public ParametrosProyectoCiResponse guardar(
            Long usuarioId, UUID proyectoPublicId, ParametrosProyectoCiRequest request) {
        Proyecto proyecto = proyectoService.validarPropietario(usuarioId, proyectoPublicId);
        apuRepository.lockProyectoCi(proyecto.id);
        ParametrosSistema sistema = parametrosService.leerSistema();
        validarRangoCi(request.porcentajeIndirecto(), sistema);

        ParametrosProyecto parametros = parametrosService.obtenerOCrear(proyecto.id);
        long overridesPrevios = apuRepository.contarOverridesCiDeProyecto(proyecto.id);
        if (overridesPrevios > 0 && request.politicaOverrides() == null) {
            throw ProblemaException.validacion("politicaOverrides debe ser PRESERVAR o RESTABLECER si hay overrides");
        }

        BigDecimal ciPrevia = parametros.porcentajeIndirecto;
        boolean cambioCi = cambioNumerico(ciPrevia, request.porcentajeIndirecto());
        parametros.porcentajeIndirecto = request.porcentajeIndirecto();
        parametros.ciIndividualHabilitado = request.ciIndividualHabilitado();
        parametrosRepository.persist(parametros);

        boolean restablecer =
                !request.ciIndividualHabilitado() || request.politicaOverrides() == PoliticaOverrides.RESTABLECER;
        int overridesRestablecidos = restablecer ? apuRepository.limpiarOverridesCiDeProyecto(proyecto.id) : 0;
        if (cambioCi || overridesRestablecidos > 0) {
            recalculoService.recalcularCiProyecto(proyecto.id);
        }
        return respuesta(proyecto, parametros);
    }

    private ParametrosProyectoCiResponse respuesta(Proyecto proyecto, ParametrosProyecto parametros) {
        return new ParametrosProyectoCiResponse(
                proyecto.publicId,
                parametros.porcentajeIndirecto,
                parametros.ciIndividualHabilitado,
                apuRepository.contarOverridesCiDeProyecto(proyecto.id));
    }

    private static void validarRangoCi(BigDecimal valor, ParametrosSistema sistema) {
        if (valor == null) return;
        if (sistema.rangoCiMin != null && valor.compareTo(sistema.rangoCiMin) < 0) {
            throw ProblemaException.validacion(
                    "porcentajeIndirecto está por debajo del mínimo permitido (" + sistema.rangoCiMin + ")");
        }
        if (sistema.rangoCiMax != null && valor.compareTo(sistema.rangoCiMax) > 0) {
            throw ProblemaException.validacion(
                    "porcentajeIndirecto supera el máximo permitido (" + sistema.rangoCiMax + ")");
        }
    }

    private static boolean cambioNumerico(BigDecimal previo, BigDecimal solicitado) {
        if (previo == null) return solicitado != null;
        return solicitado == null || previo.compareTo(solicitado) != 0;
    }
}
