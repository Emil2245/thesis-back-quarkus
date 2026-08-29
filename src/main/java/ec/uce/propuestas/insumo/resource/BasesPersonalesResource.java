package ec.uce.propuestas.insumo.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.insumo.dto.BasePersonalCrearRequest;
import ec.uce.propuestas.insumo.dto.BasePersonalResponse;
import ec.uce.propuestas.insumo.service.BasesPersonalesService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

/**
 * WU-05 — Bases PERSONALES del usuario autenticado (N04 §A9).
 *
 * <p>Ruta {@code /bases-personales}. La identidad del dueño, el tipo y la
 * ausencia de proyecto padre se fijan siempre desde el JWT en el servidor:
 * el cliente nunca puede elegir dueño, tipo, proyecto ni {@code publicId}
 * (RNF-05). Solo se exponen las bases PERSONALES del caller; las filas
 * CENTRAL/PROYECTO nunca aparecen aquí.</p>
 *
 * <p>La seam de lookup por {@code publicId} vive en el servicio para que el
 * siguiente bloque de copia-al-usar la reutilice sin reabrir el seam aquí.</p>
 */
@Path("/bases-personales")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class BasesPersonalesResource {

    @Inject
    BasesPersonalesService basesPersonalesService;

    @Inject
    SecurityIdentity identity;

    @Inject
    UsuarioRepository usuarioRepository;

    /** Lista bases PERSONALES del caller. CENTRAL/PROYECTO nunca aparecen. */
    @GET
    @Consumes(MediaType.WILDCARD)
    public List<BasePersonalResponse> listar() {
        return basesPersonalesService.listar(usuarioId());
    }

    /** Crea una base PERSONAL a nombre del caller. */
    @POST
    public Response crear(@Valid BasePersonalCrearRequest req) {
        BasePersonalResponse body = basesPersonalesService.crear(usuarioId(), req);
        return Response.status(Response.Status.CREATED).entity(body).build();
    }

    /**
     * Plan 05 — Borrado físico con owner-to-404. Devuelve 204 si la base
     * existe y pertenece al caller; 404 en cualquier otro caso (base
     * ajena, CENTRAL o PROYECTO — nunca 403, RNF-05). Los insumos asociados
     * se eliminan por la FK CASCADE de V001.
     */
    @DELETE
    @Path("/{id}")
    public Response borrar(@PathParam("id") UUID publicId) {
        boolean borrada = basesPersonalesService.borrar(publicId, usuarioId());
        if (!borrada) {
            throw ProblemaException.noEncontrado("Base personal no encontrada");
        }
        return Response.noContent().build();
    }

    private Long usuarioId() {
        String email = identity.getPrincipal().getName();
        return usuarioRepository
                .findByEmail(email)
                .map(u -> u.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario autenticado no encontrado"));
    }
}
