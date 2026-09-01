package ec.uce.propuestas.presupuesto.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.presupuesto.dto.CapituloCrearRequest;
import ec.uce.propuestas.presupuesto.dto.CapituloEditarRequest;
import ec.uce.propuestas.presupuesto.dto.CapituloMoverRequest;
import ec.uce.propuestas.presupuesto.dto.PresupuestoResponse;
import ec.uce.propuestas.presupuesto.service.CapituloService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

/**
 * Plan 022 — endpoints de mutación del árbol de capítulos (P-28 escritura).
 *
 * <p>Rutas (todas cuelgan de {@code /presupuestos/{presupuestoId}/capitulos}):
 * <ul>
 *   <li>{@code POST   /presupuestos/{presupuestoId}/capitulos}
 *       — crear capítulo raíz o subcapítulo. Body:
 *       {@link CapituloCrearRequest}. 201 + {@link PresupuestoResponse}.</li>
 *   <li>{@code PUT    /presupuestos/{presupuestoId}/capitulos/{capituloId}}
 *       — editar descripción. Body: {@link CapituloEditarRequest}. 200 +
 *       {@link PresupuestoResponse}.</li>
 *   <li>{@code PATCH  /presupuestos/{presupuestoId}/capitulos/{capituloId}/mover}
 *       — mover a otro padre / posición. Body:
 *       {@link CapituloMoverRequest}. 200 + {@link PresupuestoResponse}.</li>
 *   <li>{@code DELETE /presupuestos/{presupuestoId}/capitulos/{capituloId}}
 *       — eliminar subárbol (cascade). 200 + {@link PresupuestoResponse}
 *       (árbol recalculado tras la cascade).</li>
 * </ul>
 *
 * <p>Path params son UUIDv7 (identidad externa inmutable, columna
 * {@code public_id}, OpenSpec WU-03). El parse se hace en este resource con
 * {@link UuidV7#parse(String)} para que un UUID malformado o de versión
 * incorrecta rechace con el contrato 400 {@code validacion} — antes de
 * cualquier acceso al repositorio. Roles: {@code USUARIO} y
 * {@code SUPER_ADMIN}.</p>
 *
 * <p>Reglas de error (07-api-contract.md §6 + P-28):
 * <ul>
 *   <li>400 {@code validacion}: UUIDv7 malformado, descripción vacía o
 *       &gt; 255, orden fuera de [1, hermanos+1], {@code parentId} que
 *       pertenece a otro presupuesto, self/descendant cycle.</li>
 *   <li>404 {@code no-encontrado}: presupuesto o capítulo ajeno /
 *       inexistente (nunca 403 — RNF-05).</li>
 * </ul>
 * </p>
 *
 * <p>Los endpoints de rubros viven en Plan 023; este recurso no maneja
 * rubros.</p>
 */
@Path("/presupuestos/{presupuestoId}/capitulos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class CapituloResource {

    @Inject
    CapituloService capituloService;

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

    /**
     * Crea un capítulo. {@code 201 Created} + árbol recalculado del
     * presupuesto (shape estable introducido en Plan 021).
     */
    @POST
    public Response crear(@PathParam("presupuestoId") String presupuestoId, @Valid CapituloCrearRequest req) {
        UUID presupuestoPublicId = UuidV7.parse(presupuestoId);
        PresupuestoResponse body = capituloService.crear(presupuestoPublicId, req, usuarioId());
        return Response.status(Response.Status.CREATED).entity(body).build();
    }

    /**
     * Edita la descripción de un capítulo. {@code 200 OK} + árbol
     * recalculado. El {@code item} / {@code orden} / {@code parentId} no
     * cambian — para reposicionar se usa el endpoint {@code PATCH …/mover}.
     */
    @PUT
    @Path("/{capituloId}")
    public PresupuestoResponse editar(
            @PathParam("presupuestoId") String presupuestoId,
            @PathParam("capituloId") String capituloId,
            @Valid CapituloEditarRequest req) {
        UUID presupuestoPublicId = UuidV7.parse(presupuestoId);
        UUID capituloPublicId = UuidV7.parse(capituloId);
        return capituloService.editarDescripcion(presupuestoPublicId, capituloPublicId, req, usuarioId());
    }

    /**
     * Mueve un capítulo a un nuevo padre y/o posición. {@code 200 OK} +
     * árbol recalculado con los nuevos {@code item}s. Rechaza self /
     * descendant cycle (400) y rango de orden fuera de
     * {@code [1, hermanos destino + 1]} (400).
     */
    @PATCH
    @Path("/{capituloId}/mover")
    public PresupuestoResponse mover(
            @PathParam("presupuestoId") String presupuestoId,
            @PathParam("capituloId") String capituloId,
            @Valid CapituloMoverRequest req) {
        UUID presupuestoPublicId = UuidV7.parse(presupuestoId);
        UUID capituloPublicId = UuidV7.parse(capituloId);
        return capituloService.mover(presupuestoPublicId, capituloPublicId, req, usuarioId());
    }

    /**
     * Elimina un capítulo y todo su subárbol. Los rubros del subárbol se
     * borran por cascade (FK {@code rubro.capitulo_id}); los APUs
     * sobreviven (D-09 + V001 §3). {@code 200 OK} + árbol recalculado tras
     * la cascade.
     */
    @DELETE
    @Path("/{capituloId}")
    @Consumes(MediaType.WILDCARD)
    public PresupuestoResponse eliminar(
            @PathParam("presupuestoId") String presupuestoId, @PathParam("capituloId") String capituloId) {
        UUID presupuestoPublicId = UuidV7.parse(presupuestoId);
        UUID capituloPublicId = UuidV7.parse(capituloId);
        return capituloService.eliminar(presupuestoPublicId, capituloPublicId, usuarioId());
    }
}
