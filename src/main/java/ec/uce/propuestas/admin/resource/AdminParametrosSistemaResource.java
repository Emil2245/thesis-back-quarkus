package ec.uce.propuestas.admin.resource;

import ec.uce.propuestas.admin.dto.ParametrosSistemaActualizarRequest;
import ec.uce.propuestas.admin.service.LogService;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.proyecto.entity.ModoCodigoRubro;
import ec.uce.propuestas.proyecto.entity.ParametrosSistema;
import ec.uce.propuestas.proyecto.service.ParametrosProyectoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;

@Path("/admin/parametros-sistema")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("SUPER_ADMIN")
public class AdminParametrosSistemaResource {

    @Inject
    ParametrosProyectoService parametrosService;

    @Inject
    LogService logService;

    @Inject
    SecurityIdentity identity;

    @Inject
    UsuarioRepository usuarioRepository;

    private Long adminId() {
        String email = identity.getPrincipal().getName();
        return usuarioRepository
                .findByEmail(email)
                .map(u -> u.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Admin no encontrado"));
    }

    @GET
    @Consumes(MediaType.WILDCARD)
    public ParametrosSistema obtener() {
        return parametrosService.leerSistema();
    }

    @PUT
    @Transactional
    public ParametrosSistema actualizar(ParametrosSistemaActualizarRequest req) {
        ParametrosSistema ps = parametrosService.leerSistema();
        if (req.porcentajeHerramientaMenor() != null) ps.porcentajeHerramientaMenor = req.porcentajeHerramientaMenor();
        if (req.porcentajeIndirecto() != null) ps.porcentajeIndirecto = req.porcentajeIndirecto();
        if (req.iva() != null) ps.iva = req.iva();
        if (req.moneda() != null) ps.moneda = req.moneda();
        if (req.mostrarSeccionesVacias() != null) ps.mostrarSeccionesVacias = req.mostrarSeccionesVacias();
        if (req.sufijosSeccionActivos() != null) ps.sufijosSeccionActivos = req.sufijosSeccionActivos();
        if (req.mostrarSubtotalesSeccion() != null) ps.mostrarSubtotalesSeccion = req.mostrarSubtotalesSeccion();
        if (req.mostrarSubtotalesPie() != null) ps.mostrarSubtotalesPie = req.mostrarSubtotalesPie();
        if (req.mostrarNombreProyectoHeader() != null)
            ps.mostrarNombreProyectoHeader = req.mostrarNombreProyectoHeader();
        if (req.enumerarApus() != null) ps.enumerarApus = req.enumerarApus();
        if (req.mensajeFooter() != null) ps.mensajeFooter = req.mensajeFooter();
        if (req.modoCodigoRubro() != null) ps.modoCodigoRubro = ModoCodigoRubro.valueOf(req.modoCodigoRubro());
        if (req.rangoHmMin() != null) ps.rangoHmMin = req.rangoHmMin();
        if (req.rangoHmMax() != null) ps.rangoHmMax = req.rangoHmMax();
        if (req.rangoCiMin() != null) ps.rangoCiMin = req.rangoCiMin();
        if (req.rangoCiMax() != null) ps.rangoCiMax = req.rangoCiMax();
        if (req.rangoDescuentoMin() != null) ps.rangoDescuentoMin = req.rangoDescuentoMin();
        if (req.rangoDescuentoMax() != null) ps.rangoDescuentoMax = req.rangoDescuentoMax();
        if (req.rangoIvaMin() != null) ps.rangoIvaMin = req.rangoIvaMin();
        if (req.rangoIvaMax() != null) ps.rangoIvaMax = req.rangoIvaMax();
        ps.updatedAt = Instant.now();
        ps.persist();
        logService.registrar(adminId(), "parametros.actualizados", "parametros_sistema", null, null);
        return ps;
    }
}
