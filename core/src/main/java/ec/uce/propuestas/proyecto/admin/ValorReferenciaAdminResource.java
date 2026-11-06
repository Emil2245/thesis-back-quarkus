package ec.uce.propuestas.proyecto.admin;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.proyecto.dto.ValorReferenciaRequest;
import ec.uce.propuestas.proyecto.dto.ValorReferenciaResponse;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/admin/valores-referencia")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("SUPER_ADMIN")
public class ValorReferenciaAdminResource {

    private static final int PAGE_SIZE_MAX = 200;
    private static final int CLAVE_MAX = 50;

    @Inject
    ValorReferenciaAdminService service;

    @Inject
    SecurityIdentity identity;

    @Inject
    UsuarioRepository usuarioRepository;

    @GET
    @Consumes(MediaType.WILDCARD)
    public Page<ValorReferenciaResponse> listar(
            @QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("25") int size) {
        validarPaginacion(page, size);
        return service.listar(page, size);
    }

    @PUT
    @Path("/{clave}")
    public Response upsert(@PathParam("clave") String clave, @Valid ValorReferenciaRequest request) {
        validarClave(clave);
        ValorReferenciaAdminService.UpsertResult result = service.upsert(usuarioId(), clave, request);
        return Response.status(result.creada() ? Response.Status.CREATED : Response.Status.OK)
                .entity(result.response())
                .build();
    }

    @DELETE
    @Path("/{clave}")
    @Consumes(MediaType.WILDCARD)
    public Response eliminar(@PathParam("clave") String clave) {
        validarClave(clave);
        service.eliminar(usuarioId(), clave);
        return Response.noContent().build();
    }

    private Long usuarioId() {
        return usuarioRepository
                .findByEmail(identity.getPrincipal().getName())
                .map(u -> u.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario autenticado no encontrado"));
    }

    private static void validarPaginacion(int page, int size) {
        if (page < 0) {
            throw ProblemaException.validacion("pagina-invalida");
        }
        if (size < 1 || size > PAGE_SIZE_MAX) {
            throw new ProblemaException(
                    400, "tamano-pagina-invalido", "El parámetro size debe estar entre 1 y " + PAGE_SIZE_MAX);
        }
    }

    private static void validarClave(String clave) {
        if (clave == null || clave.isBlank()) {
            throw ProblemaException.validacion("clave-requerida");
        }
        if (clave.length() > CLAVE_MAX) {
            throw new ProblemaException(400, "clave-excedida", "La clave no puede exceder 50 caracteres");
        }
    }
}
