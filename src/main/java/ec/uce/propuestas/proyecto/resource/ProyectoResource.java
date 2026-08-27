package ec.uce.propuestas.proyecto.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.proyecto.dto.ParametrosSistemaEditarRequest;
import ec.uce.propuestas.proyecto.dto.ProyectoCrearRequest;
import ec.uce.propuestas.proyecto.dto.ProyectoEditarRequest;
import ec.uce.propuestas.proyecto.dto.ProyectoResponse;
import ec.uce.propuestas.proyecto.entity.EstadoProyecto;
import ec.uce.propuestas.proyecto.entity.ParametrosSistema;
import ec.uce.propuestas.proyecto.mapper.ProyectoMapper;
import ec.uce.propuestas.proyecto.service.ParametrosProyectoService;
import ec.uce.propuestas.proyecto.service.ProyectoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Proyectos (07-api-contract.md §3). Ruta {@code /proyectos}. El usuario solo
 * opera sobre sus propios proyectos (RNF-05).
 */
@Path("/proyectos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class ProyectoResource {

    @Inject
    ProyectoService proyectoService;

    @Inject
    ParametrosProyectoService parametrosService;

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
    public Page<ProyectoResponse> listar(
            @QueryParam("q") String q,
            @QueryParam("estado") EstadoProyecto estado,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size) {
        return proyectoService.listarDeUsuario(usuarioId(), q, estado, page, size);
    }

    @POST
    public Response crear(@Valid ProyectoCrearRequest req) {
        return Response.status(Response.Status.CREATED)
                .entity(proyectoService.crear(usuarioId(), req))
                .build();
    }

    @GET
    @Path("/{proyectoId}")
    @Consumes(MediaType.WILDCARD)
    public ProyectoResponse obtener(@PathParam("proyectoId") Long id) {
        var p = proyectoService.validarPropietario(usuarioId(), id);
        return ProyectoMapper.toResponse(p);
    }

    @PUT
    @Path("/{proyectoId}")
    public ProyectoResponse editar(@PathParam("proyectoId") Long id, @Valid ProyectoEditarRequest req) {
        return proyectoService.actualizar(usuarioId(), id, req);
    }

    @DELETE
    @Path("/{proyectoId}")
    public Response eliminar(@PathParam("proyectoId") Long id) {
        proyectoService.eliminar(usuarioId(), id);
        return Response.noContent().build();
    }

    /** Parámetros globales (lectura). */
    @GET
    @Path("/parametros-sistema")
    @Consumes(MediaType.WILDCARD)
    public ParametrosSistema parametrosSistema() {
        return parametrosService.leerSistema();
    }

    /**
     * Parámetros globales — escritura restringida a SUPER_ADMIN. Edita defaults y
     * rangos configurables de HM/CI/IVA/descuento (N04 §A6).
     */
    @PUT
    @Path("/parametros-sistema")
    @RolesAllowed("SUPER_ADMIN")
    public ParametrosSistema editarParametrosSistema(@Valid ParametrosSistemaEditarRequest req) {
        return parametrosService.actualizarSistema(req);
    }
}
