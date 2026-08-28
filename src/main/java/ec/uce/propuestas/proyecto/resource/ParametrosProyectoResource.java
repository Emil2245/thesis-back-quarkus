package ec.uce.propuestas.proyecto.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.proyecto.dto.ParametrosProyectoEditarRequest;
import ec.uce.propuestas.proyecto.dto.ParametrosProyectoResponse;
import ec.uce.propuestas.proyecto.service.ParametrosProyectoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/** Parámetros de cálculo de un proyecto (P-07). Ruta {@code /proyectos/{proyectoId}/parametros}. */
@Path("/proyectos/{proyectoId}/parametros")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class ParametrosProyectoResource {

    @Inject
    ParametrosProyectoService parametrosService;

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
    @Consumes(MediaType.WILDCARD)
    public ParametrosProyectoResponse leer(@PathParam("proyectoId") Long proyectoId) {
        return parametrosService.leer(usuarioId(), proyectoId);
    }

    @PUT
    public ParametrosProyectoResponse editar(
            @PathParam("proyectoId") Long proyectoId, @Valid ParametrosProyectoEditarRequest req) {
        return parametrosService.actualizar(usuarioId(), proyectoId, req).parametros();
    }
}
