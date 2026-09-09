package ec.uce.propuestas.presupuesto.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.presupuesto.dto.ResumenComponentesResponse;
import ec.uce.propuestas.presupuesto.service.ResumenComponentesService;
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
 * Plan 023 — endpoint de lectura del resumen por componente del presupuesto
 * (P-30). Ruta {@code GET /presupuestos/{presupuestoId}/resumen}.
 *
 * <p>Forma JSON estable:
 * <ul>
 *   <li>{@code porComponente}: mapa de las 4 secciones canónicas
 *       ({@code EQUIPO, MANO_OBRA, MATERIAL, TRANSPORTE}) con la
 *       contribución directa de cada componente, en escala 6.</li>
 *   <li>{@code totalGeneral}: total persistido del presupuesto (write-through
 *       incluye CI), en escala 6.</li>
 *   <li>{@code ivaReferencial}: {@code totalGeneral × ParametrosProyecto.iva}
 *       a precisión natural {@code BigDecimal}, redondeado a 6 sólo en
 *       serialización.</li>
 *   <li>{@code totalConIva}: suma natural {@code totalGeneral + ivaReferencial}.</li>
 * </ul>
 *
 * <p>El servicio es estrictamente read-only — no muta BD. La respuesta se
 * calcula al vuelo (sin cache de {@code ApuCalculado}; ver stop condition
 * (A) del plan: si el árbol IESS tarda > 1s, I-08 introducirá cache).</p>
 *
 * <p>Path param {@code presupuestoId} es UUIDv7 validado en frontera (Plan 07
 * / WU-03); un UUID malformado o de versión incorrecta devuelve 400
 * {@code validacion} sin tocar la BD. Roles: {@code USUARIO} y
 * {@code SUPER_ADMIN}.</p>
 */
@Path("/presupuestos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class ResumenComponentesResource {

    @Inject
    ResumenComponentesService resumenComponentesService;

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
     * Resumen por componente del presupuesto. Read-only (RNF-05: ajeno →
     * 404). 400 si la UUIDv7 del path es malformada.
     */
    @GET
    @Path("/{presupuestoId}/resumen")
    @Consumes(MediaType.WILDCARD)
    public ResumenComponentesResponse obtenerResumen(@PathParam("presupuestoId") String presupuestoId) {
        UUID presupuestoPublicId = UuidV7.parse(presupuestoId);
        return resumenComponentesService.obtenerResumen(presupuestoPublicId, usuarioId());
    }
}
