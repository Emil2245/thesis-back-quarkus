package ec.uce.propuestas.presupuesto.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.presupuesto.dto.PresupuestoResponse;
import ec.uce.propuestas.presupuesto.dto.RubroCrearRequest;
import ec.uce.propuestas.presupuesto.dto.RubroPatchRequest;
import ec.uce.propuestas.presupuesto.service.RubroService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

/**
 * Plan 023 — endpoints de mutación de rubros (P-29 escritura).
 *
 * <p>Rutas (todas cuelgan de {@code /presupuestos/{presupuestoId}/capitulos/{capituloId}/rubros}):
 * <ul>
 *   <li>{@code POST   /presupuestos/{presupuestoId}/capitulos/{capituloId}/rubros}
 *       — crear rubro. Body: {@link RubroCrearRequest}. 201 +
 *       {@link PresupuestoResponse}.</li>
 *   <li>{@code PATCH  /presupuestos/{presupuestoId}/capitulos/{capituloId}/rubros/{rubroId}}
 *       — editar cantidad. Body: {@link RubroPatchRequest}. 200 +
 *       {@link PresupuestoResponse}.</li>
 *   <li>{@code DELETE /presupuestos/{presupuestoId}/capitulos/{capituloId}/rubros/{rubroId}}
 *       — eliminar rubro (el APU sobrevive, D-09). 200 +
 *       {@link PresupuestoResponse}.</li>
 * </ul>
 *
 * <p>Los path params son UUIDv7 (Plan 07 / WU-03); el {@code apuId} del body
 * también es UUIDv7 validado en frontera con {@link UuidV7#parse(String)} —
 * antes de cualquier acceso al repositorio. Roles:
 * {@code USUARIO} y {@code SUPER_ADMIN}.</p>
 *
 * <p>Reglas de error (07-api-contract.md §6 + P-29):
 * <ul>
 *   <li>400 {@code validacion}: UUIDv7 malformado, cantidad ≤ 0,
 *       APU pertenece a otro presupuesto, capítulo pertenece a otro
 *       presupuesto, rubro pertenece a otro capítulo (cross-capítulo).</li>
 *   <li>404 {@code no-encontrado}: presupuesto, capítulo, rubro o APU
 *       ajeno o inexistente (nunca 403 — RNF-05).</li>
 *   <li>409 {@code apu-referenciado}: APU ya vinculado a otro rubro del
 *       mismo presupuesto (D-09 1:1).</li>
 * </ul>
 *
 * <p>Cada mutación termina con
 * {@code RecalculoService.recalcular(new Alcance.Version(presupuestoId))} y
 * devuelve el árbol completo del presupuesto (write-through de totales raíz
 * → capítulos → rubros).</p>
 */
@Path("/presupuestos/{presupuestoId}/capitulos/{capituloId}/rubros")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class RubroResource {

    @Inject
    RubroService rubroService;

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
     * Crea un rubro bajo un capítulo. {@code 201 Created} + árbol recalculado.
     * El {@code apuId} del body se valida como UUIDv7 antes de delegar al
     * service (regla frontera Plan 07 / WU-03).
     */
    @POST
    public Response crear(
            @PathParam("presupuestoId") String presupuestoId,
            @PathParam("capituloId") String capituloId,
            @Valid RubroCrearRequest req) {
        UUID presupuestoPublicId = UuidV7.parse(presupuestoId);
        UUID capituloPublicId = UuidV7.parse(capituloId);
        // Parse temprano del apuId UUIDv7 — un UUID malformado/no-v7 devuelve
        // 400 validacion desde la frontera, antes de tocar la BD.
        UUID apuPublicId = UuidV7.parse(req.apuId().toString());
        RubroCrearRequest normalizado = new RubroCrearRequest(apuPublicId, req.cantidad());
        PresupuestoResponse body = rubroService.crear(presupuestoPublicId, capituloPublicId, normalizado, usuarioId());
        return Response.status(Response.Status.CREATED).entity(body).build();
    }

    /**
     * Edita la cantidad de un rubro. {@code 200 OK} + árbol recalculado.
     * El {@code item}, {@code codigo}, {@code descripcion}, {@code unidad} y
     * {@code apuId} no cambian.
     */
    @PATCH
    @Path("/{rubroId}")
    public PresupuestoResponse editarCantidad(
            @PathParam("presupuestoId") String presupuestoId,
            @PathParam("capituloId") String capituloId,
            @PathParam("rubroId") String rubroId,
            @Valid RubroPatchRequest req) {
        UUID presupuestoPublicId = UuidV7.parse(presupuestoId);
        UUID capituloPublicId = UuidV7.parse(capituloId);
        UUID rubroPublicId = UuidV7.parse(rubroId);
        return rubroService.editarCantidad(presupuestoPublicId, capituloPublicId, rubroPublicId, req, usuarioId());
    }

    /**
     * Elimina un rubro. {@code 200 OK} + árbol recalculado tras la
     * compactación de items hermanos. El APU sobrevive (D-09 + V001 §3).
     */
    @DELETE
    @Path("/{rubroId}")
    @Consumes(MediaType.WILDCARD)
    public PresupuestoResponse eliminar(
            @PathParam("presupuestoId") String presupuestoId,
            @PathParam("capituloId") String capituloId,
            @PathParam("rubroId") String rubroId) {
        UUID presupuestoPublicId = UuidV7.parse(presupuestoId);
        UUID capituloPublicId = UuidV7.parse(capituloId);
        UUID rubroPublicId = UuidV7.parse(rubroId);
        return rubroService.eliminar(presupuestoPublicId, capituloPublicId, rubroPublicId, usuarioId());
    }
}
