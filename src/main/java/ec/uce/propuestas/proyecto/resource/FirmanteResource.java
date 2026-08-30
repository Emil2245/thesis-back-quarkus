package ec.uce.propuestas.proyecto.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
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
import java.util.UUID;

/**
 * Firmantes del proyecto (P-11). Ruta {@code /proyectos/{proyectoId}/firmantes}.
 *
 * <p>Plan 07 — los path params ({@code proyectoId} y {@code firmanteId}) son
 * UUIDv7 (identidad externa inmutable, columna {@code public_id}). El parse se
 * hace en este resource con {@link UuidV7#parse} para que un UUID mal formado o
 * de versión incorrecta rechace con el contrato 400 {@code validacion} — antes
 * de cualquier acceso al repositorio. La resolución de ownership se cierra en
 * el service: una fila de un proyecto ajeno o una fila inexistente devuelven
 * 404 {@code no-encontrado} (nunca 403, RNF-05). El {@code BIGINT} interno se
 * retiene debajo del resource y de los services; nunca se expone.</p>
 */
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
        return usuarioRepository
                .findByEmail(email)
                .map(u -> u.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario autenticado no encontrado"));
    }

    @GET
    @Consumes(MediaType.WILDCARD)
    public List<FirmanteResponse> listar(@PathParam("proyectoId") String proyectoId) {
        UUID proyectoPublicId = UuidV7.parse(proyectoId);
        return firmanteService.listarDeProyecto(usuarioId(), proyectoPublicId);
    }

    @POST
    public Response crear(@PathParam("proyectoId") String proyectoId, @Valid FirmanteCrearRequest req) {
        UUID proyectoPublicId = UuidV7.parse(proyectoId);
        return Response.status(Response.Status.CREATED)
                .entity(firmanteService.crear(usuarioId(), proyectoPublicId, req))
                .build();
    }

    @PUT
    @Path("/{firmanteId}")
    public FirmanteResponse editar(
            @PathParam("proyectoId") String proyectoId,
            @PathParam("firmanteId") String firmanteId,
            @Valid FirmanteCrearRequest req) {
        UUID proyectoPublicId = UuidV7.parse(proyectoId);
        UUID firmantePublicId = UuidV7.parse(firmanteId);
        return firmanteService.actualizar(usuarioId(), proyectoPublicId, firmantePublicId, req);
    }

    @DELETE
    @Path("/{firmanteId}")
    public Response eliminar(@PathParam("proyectoId") String proyectoId, @PathParam("firmanteId") String firmanteId) {
        UUID proyectoPublicId = UuidV7.parse(proyectoId);
        UUID firmantePublicId = UuidV7.parse(firmanteId);
        firmanteService.eliminar(usuarioId(), proyectoPublicId, firmantePublicId);
        return Response.noContent().build();
    }
}
