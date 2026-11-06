package ec.uce.propuestas.presupuesto.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.presupuesto.dto.ValidacionPresupuestoResponse;
import ec.uce.propuestas.presupuesto.service.ValidacionPresupuestoService;
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
 * Plan 025 (P-32) — endpoint REST de validación de integridad del presupuesto.
 * Ruta {@code GET /presupuestos/{presupuestoId}/validacion} → devuelve tres
 * listas planas e independientes de referencias a rubros con defectos
 * (PU=0, cantidad=0, sin actividad) y un flag {@code exportable} derivado del
 * tamaño agregado de esas listas.
 *
 * <p>Decisiones locked (07-api-contract.md §6 + P-32):
 * <ul>
 *   <li>UUIDv7 parseado en la frontera con {@link UuidV7#parse(String)};
 *       cualquier entrada malformada o de versión incorrecta rechaza con 400
 *       {@code validacion} antes de tocar la BD (RNF-05; TC-P32-08/09).</li>
 *   <li>Presupuesto ajeno o inexistente → 404 {@code no-encontrado}
 *       (owner-to-404 — nunca 403; TC-P32-10/11).</li>
 *   <li>Roles: {@code USUARIO} y {@code SUPER_ADMIN}; el patrón CDI
 *       {@code SecurityIdentity + UsuarioRepository} se reutiliza del resto del
 *       módulo {@code presupuesto/resource}.</li>
 *   <li>La ruta convive sin colisión con {@link PresupuestoResource} y
 *       {@link ComparacionResource}: el class-level {@code @Path("/presupuestos")}
 *       es el mismo prefijo y el method-level
 *       {@code @Path("/{presupuestoId}/validacion")} no choca con
 *       {@code /{presupuestoId}} ni con {@code /{presupuestoId}/comparar}.</li>
 *   <li>Read-only: ninguna escritura, ningún flush, ninguna mutación
 *       (TC-P32-13). El método se sirve sin {@code @Transactional} explícito,
 *       igual que {@code PresupuestoResource.obtenerArbol}.</li>
 * </ul>
 */
@Path("/presupuestos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class PresupuestoValidacionResource {

    @Inject
    ValidacionPresupuestoService validacionService;

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
     * Devuelve las listas de defectos del presupuesto del path y el flag
     * derivado {@code exportable}. Los defectos son ortogonales: un mismo
     * rubro puede aparecer en varias listas si acumula varios defectos
     * (TC-P32-05).
     */
    @GET
    @Path("/{presupuestoId}/validacion")
    @Consumes(MediaType.WILDCARD)
    public ValidacionPresupuestoResponse validar(@PathParam("presupuestoId") String presupuestoId) {
        UUID presupuestoPublicId = UuidV7.parse(presupuestoId);
        return validacionService.validar(presupuestoPublicId, usuarioId());
    }
}
