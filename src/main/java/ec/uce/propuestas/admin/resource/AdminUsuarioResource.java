package ec.uce.propuestas.admin.resource;

import ec.uce.propuestas.admin.dto.UsuarioAdminEditarRequest;
import ec.uce.propuestas.admin.dto.UsuarioAdminResponse;
import ec.uce.propuestas.admin.service.LogService;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.proyecto.repository.ProyectoRepository;
import ec.uce.propuestas.usuario.Rol;
import ec.uce.propuestas.usuario.Usuario;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/admin/usuarios")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("SUPER_ADMIN")
public class AdminUsuarioResource {

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    ProyectoRepository proyectoRepository;

    @Inject
    LogService logService;

    @Inject
    SecurityIdentity identity;

    private Long adminId() {
        String email = identity.getPrincipal().getName();
        return usuarioRepository
                .findByEmail(email)
                .map(u -> u.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Admin no encontrado"));
    }

    @GET
    @Consumes(MediaType.WILDCARD)
    public Page<UsuarioAdminResponse> listar(
            @QueryParam("q") String q,
            @QueryParam("activo") Boolean activo,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size) {
        List<Usuario> items = usuarioRepository.findAll().page(page, size).list();
        long total = usuarioRepository.count();
        return Page.of(items.stream().map(this::toResponse).toList(), total, page, size);
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public UsuarioAdminResponse editar(@PathParam("id") Long id, @Valid UsuarioAdminEditarRequest req) {
        Usuario u = usuarioRepository
                .findByIdOptional(id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario no encontrado"));
        u.nombre = req.nombre();
        if (req.email() != null && !req.email().isBlank()) {
            u.email = req.email();
        }
        if (req.rol() != null) {
            u.rol = Rol.valueOf(req.rol());
        }
        usuarioRepository.persist(u);
        logService.registrar(adminId(), "usuario.editado", "usuario", id, null);
        return toResponse(u);
    }

    @POST
    @Path("/{id}/desactivar")
    @Transactional
    @Consumes(MediaType.WILDCARD)
    public UsuarioAdminResponse desactivar(@PathParam("id") Long id) {
        Usuario u = usuarioRepository
                .findByIdOptional(id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario no encontrado"));
        u.activo = false;
        usuarioRepository.persist(u);
        logService.registrar(adminId(), "usuario.desactivado", "usuario", id, null);
        return toResponse(u);
    }

    @POST
    @Path("/{id}/reactivar")
    @Transactional
    @Consumes(MediaType.WILDCARD)
    public UsuarioAdminResponse reactivar(@PathParam("id") Long id) {
        Usuario u = usuarioRepository
                .findByIdOptional(id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario no encontrado"));
        u.activo = true;
        usuarioRepository.persist(u);
        logService.registrar(adminId(), "usuario.reactivado", "usuario", id, null);
        return toResponse(u);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @Consumes(MediaType.WILDCARD)
    public Response eliminar(@PathParam("id") Long id) {
        Usuario u = usuarioRepository
                .findByIdOptional(id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario no encontrado"));
        long proyectos = proyectoRepository.count("usuarioId", id);
        if (proyectos > 0) {
            throw ProblemaException.validacion("No se puede eliminar el usuario: tiene " + proyectos + " proyecto(s)");
        }
        logService.registrar(adminId(), "usuario.eliminado", "usuario", id, null);
        usuarioRepository.delete(u);
        return Response.noContent().build();
    }

    private UsuarioAdminResponse toResponse(Usuario u) {
        return new UsuarioAdminResponse(
                u.id, u.nombre, u.email, u.rol.name(), u.emailVerificado, u.activo, u.createdAt);
    }
}
