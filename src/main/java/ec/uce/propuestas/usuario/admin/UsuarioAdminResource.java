package ec.uce.propuestas.usuario.admin;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.usuario.admin.dto.UsuarioAdminEditarRequest;
import ec.uce.propuestas.usuario.admin.dto.UsuarioAdminResponse;
import ec.uce.propuestas.usuario.admin.dto.UsuarioInvitarRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

/**
 * Plan 034 — Recurso JAX-RS del ciclo de vida administrativo de usuarios
 * (P-38 / US-35 / TC-P38-01..03) bajo la ruta canónica
 * {@code /admin/usuarios}.
 *
 * <p>Decisión 30 del plan 034 (DTOs canónicos) y decisión 32 (emisión D-13).
 * Todos los endpoints exigen rol {@code SUPER_ADMIN}; los usuarios
 * {@code USUARIO} reciben 403 sin pistas.
 *
 * <p>Decisiones deferidas explícitamente a I-12 (no se codifican):
 * self-delete, last-active SUPER_ADMIN, cambio de email admin y
 * bootstrap del primer SUPER_ADMIN (acta 032, decisiones 5/6/7).
 */
@Path("/admin/usuarios")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("SUPER_ADMIN")
public class UsuarioAdminResource {

    private static final int PAGE_SIZE_MAX = 200;

    @Inject
    UsuarioAdminService service;

    // =========================================================================
    // GET /admin/usuarios — listado paginado (q, activo, page, size)
    // =========================================================================

    @GET
    public Page<UsuarioAdminResponse> listar(
            @QueryParam("q") String q,
            @QueryParam("activo") Boolean activo,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size) {
        validarPaginacion(page, size);
        var items = service.listar(q, activo, page, size);
        long total = service.contar(q, activo);
        return Page.of(items.stream().map(UsuarioAdminResponse::from).toList(), total, page, size);
    }

    // =========================================================================
    // GET /admin/usuarios/{id} — lectura individual
    // =========================================================================

    @GET
    @Path("/{id}")
    public UsuarioAdminResponse leer(@PathParam("id") String id) {
        UUID publicId = UuidV7.parse(id);
        return UsuarioAdminResponse.from(service.buscarPorPublicId(publicId));
    }

    // =========================================================================
    // POST /admin/usuarios — invitación 72 h sin contraseña temporal
    // =========================================================================

    @POST
    public Response invitar(@Valid UsuarioInvitarRequest req) {
        var u = service.invitar(req.nombre(), req.email(), req.rol());
        return Response.status(201).entity(UsuarioAdminResponse.from(u)).build();
    }

    // =========================================================================
    // PUT /admin/usuarios/{id} — edición (sin email)
    // =========================================================================

    @PUT
    @Path("/{id}")
    public UsuarioAdminResponse editar(@PathParam("id") String id, @Valid UsuarioAdminEditarRequest req) {
        UUID publicId = UuidV7.parse(id);
        var u = service.editar(publicId, req.nombre(), req.rol(), req.activo());
        return UsuarioAdminResponse.from(u);
    }

    // =========================================================================
    // POST /admin/usuarios/{id}/desactivar
    // =========================================================================

    @POST
    @Path("/{id}/desactivar")
    @Consumes(MediaType.WILDCARD)
    public UsuarioAdminResponse desactivar(@PathParam("id") String id) {
        UUID publicId = UuidV7.parse(id);
        return UsuarioAdminResponse.from(service.desactivar(publicId));
    }

    // =========================================================================
    // POST /admin/usuarios/{id}/reactivar
    // =========================================================================

    @POST
    @Path("/{id}/reactivar")
    @Consumes(MediaType.WILDCARD)
    public UsuarioAdminResponse reactivar(@PathParam("id") String id) {
        UUID publicId = UuidV7.parse(id);
        return UsuarioAdminResponse.from(service.reactivar(publicId));
    }

    // =========================================================================
    // DELETE /admin/usuarios/{id} — 409 si tiene proyectos propios
    // =========================================================================

    @DELETE
    @Path("/{id}")
    public Response eliminar(@PathParam("id") String id) {
        UUID publicId = UuidV7.parse(id);
        service.eliminar(publicId);
        return Response.noContent().build();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void validarPaginacion(int page, int size) {
        if (page < 0) {
            throw ProblemaException.validacion("pagina-invalida");
        }
        if (size < 1 || size > PAGE_SIZE_MAX) {
            throw new ProblemaException(
                    400, "tamano-pagina-invalido", "El parámetro size debe estar entre 1 y " + PAGE_SIZE_MAX);
        }
    }
}
