package ec.uce.propuestas.proyecto.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.proyecto.dto.ParametrosProyectoCiRequest;
import ec.uce.propuestas.proyecto.dto.ParametrosProyectoCiResponse;
import ec.uce.propuestas.proyecto.dto.ParametrosProyectoEditarRequest;
import ec.uce.propuestas.proyecto.dto.ParametrosProyectoResponse;
import ec.uce.propuestas.proyecto.service.ParametrosProyectoService;
import ec.uce.propuestas.proyecto.service.ProyectoCiService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

/** Parámetros de cálculo de un proyecto (P-07). Ruta {@code /proyectos/{proyectoId}/parametros}. */
@Path("/proyectos/{proyectoId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class ParametrosProyectoResource {

    @Inject
    ParametrosProyectoService parametrosService;

    @Inject
    ProyectoCiService proyectoCiService;

    @Inject
    SecurityIdentity identity;

    @Inject
    UsuarioRepository usuarioRepository;

    private Long usuarioId() {
        return usuarioRepository
                .findByEmail(identity.getPrincipal().getName())
                .map(u -> u.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario autenticado no encontrado"));
    }

    @GET
    @Path("/parametros")
    @Consumes(MediaType.WILDCARD)
    public ParametrosProyectoResponse leer(@PathParam("proyectoId") String proyectoId) {
        UUID proyectoPublicId = UuidV7.parse(proyectoId);
        return parametrosService.leer(usuarioId(), proyectoPublicId);
    }

    @PUT
    @Path("/parametros")
    public ParametrosProyectoResponse editar(
            @PathParam("proyectoId") String proyectoId, @Valid ParametrosProyectoEditarRequest req) {
        UUID proyectoPublicId = UuidV7.parse(proyectoId);
        return parametrosService
                .actualizarParametrosLegado(usuarioId(), proyectoPublicId, req)
                .parametros();
    }

    @GET
    @Path("/ci")
    @Consumes(MediaType.WILDCARD)
    public ParametrosProyectoCiResponse leerCi(@PathParam("proyectoId") String proyectoId) {
        return proyectoCiService.leer(usuarioId(), UuidV7.parse(proyectoId));
    }

    @PUT
    @Path("/ci")
    public ParametrosProyectoCiResponse guardarCi(
            @PathParam("proyectoId") String proyectoId, @Valid ParametrosProyectoCiRequest request) {
        return proyectoCiService.guardar(usuarioId(), UuidV7.parse(proyectoId), request);
    }
}
