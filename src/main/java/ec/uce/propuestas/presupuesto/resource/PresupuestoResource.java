package ec.uce.propuestas.presupuesto.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.presupuesto.dto.PresupuestoResponse;
import ec.uce.propuestas.presupuesto.service.PresupuestoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

/**
 * Plan 021 — endpoints de lectura del agregado
 * {@code Presupuesto → Capitulo → Rubro} (P-28/P-29/P-30 read model +
 * P-31 listado de versiones).
 *
 * <p>Rutas:
 * <ul>
 *   <li>{@code GET /proyectos/{proyectoId}/presupuestos} — versiones del
 *       proyecto (orden descendente por {@code version}).</li>
 *   <li>{@code GET /presupuestos/{presupuestoId}} — read model completo
 *       del árbol (cabecera + capítulos recursivos + rubros).</li>
 * </ul>
 *
 * <p>Las mutaciones del árbol (POST/PUT/PATCH/DELETE capítulos y rubros)
 * viven en los planes 022 y 023 — este recurso es GET-only.</p>
 *
 * <p>Plan 07 — los path params son UUIDv7 (identidad externa inmutable).
 * UUID malformado o no-v7 → 400 {@code validacion} (RNF-05). Proyecto o
 * presupuesto ajeno o inexistente → 404 {@code no-encontrado} (nunca 403).
 * Roles: USUARIO y SUPER_ADMIN.</p>
 */
@Path("/presupuestos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class PresupuestoResource {

    @Inject
    PresupuestoService presupuestoService;

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

    /** Read model completo del árbol (cabecera + capítulos recursivos + rubros). */
    @GET
    @Path("/{presupuestoId}")
    @Consumes(MediaType.WILDCARD)
    public PresupuestoResponse obtenerArbol(@PathParam("presupuestoId") String presupuestoId) {
        UUID presupuestoPublicId = UuidV7.parse(presupuestoId);
        return presupuestoService.obtenerArbol(presupuestoPublicId, usuarioId());
    }
}
