package ec.uce.propuestas.proyecto.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.proyecto.dto.FirmanteCrearRequest;
import ec.uce.propuestas.proyecto.dto.FirmanteResponse;
import ec.uce.propuestas.proyecto.service.FirmanteService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/** Firmantes del proyecto (P-11). Ruta {@code /proyectos/{proyectoId}/firmantes}. */
@Path("/proyectos/{proyectoId}/firmantes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class FirmanteResource {

    @Inject
    FirmanteService firmanteService;
    @Inject
    SecurityIdentity identity;
    @Inject
    UsuarioRepository usuarioRepository;

    private Long usuarioId() {
        String email = identity.getPrincipal().getName();
        return usuarioRepository.findByEmail(email)
                .map(u -> u.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario autenticado no encontrado"));
    }

    @GET
    @Consumes(MediaType.WILDCARD)
    public List<FirmanteResponse> listar(@PathParam("proyectoId") Long proyectoId) {
        return firmanteService.listarDeProyecto(usuarioId(), proyectoId);
    }

    @POST
    public Response crear(@PathParam("proyectoId") Long proyectoId, @Valid FirmanteCrearRequest req) {
        return Response.status(Response.Status.CREATED)
                .entity(firmanteService.crear(usuarioId(), proyectoId, req)).build();
    }

    @PUT
    @Path("/{firmanteId}")
    public FirmanteResponse editar(@PathParam("proyectoId") Long proyectoId,
                                   @PathParam("firmanteId") Long firmanteId,
                                   @Valid FirmanteCrearRequest req) {
        return firmanteService.actualizar(usuarioId(), proyectoId, firmanteId, req);
    }

    @DELETE
    @Path("/{firmanteId}")
    public Response eliminar(@PathParam("proyectoId") Long proyectoId,
                             @PathParam("firmanteId") Long firmanteId) {
        firmanteService.eliminar(usuarioId(), proyectoId, firmanteId);
        return Response.noContent().build();
    }
}