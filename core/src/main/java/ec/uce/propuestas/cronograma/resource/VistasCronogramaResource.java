package ec.uce.propuestas.cronograma.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.cronograma.dto.CronogramaResponse;
import ec.uce.propuestas.cronograma.dto.CronogramaVistasResponse;
import ec.uce.propuestas.cronograma.service.VistasCronogramaService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

/**
 * Plan 030 (P-35/P-36) — rutas canónicas de las vistas de cronograma y de la
 * revisión:
 *
 * <ul>
 *   <li>{@code GET /cronogramas/{id}/vistas} — respuesta única con bloques
 *       {@code gantt}, {@code valorizado} y {@code curvaS} sobre una sola
 *       proyección; raíz con sólo {@code cronogramaId} (forma exacta del
 *       canon 07-api-contract §7 Apéndice B).</li>
 *   <li>{@code POST /cronogramas/{id}/revisado} — sin body; captura el
 *       snapshot canónico (total + fingerprint + fecha) y devuelve el
 *       {@link CronogramaResponse} canónico. Idempotente: una repetición con
 *       los mismos datos no muta {@code fechaRevision}.</li>
 * </ul>
 *
 * <p>El UUID del path se valida en la frontera con {@link UuidV7#parse(String)}:
 * malformado o no-v7 → 400 {@code validacion} antes de tocar la base.
 * Inexistente o ajeno → 404 {@code no-encontrado} (owner-to-404, nunca 403).
 * Roles funcionales: {@code USUARIO} y {@code SUPER_ADMIN}. Ninguna ruta
 * muta los avances ni los segmentos; la lectura es read-only y no
 * actualiza {@code fechaRevision}.</p>
 */
@Path("/cronogramas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class VistasCronogramaResource {

    @Inject
    VistasCronogramaService vistasCronogramaService;

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
     * Lectura de la proyección común con tres bloques. El cronograma es
     * owner-scoped y la consulta no escribe.
     */
    @GET
    @Path("/{id}/vistas")
    @Consumes(MediaType.WILDCARD)
    public CronogramaVistasResponse obtenerVistas(@PathParam("id") String id) {
        UUID publicId = UuidV7.parse(id);
        return vistasCronogramaService.obtenerVistas(publicId, usuarioId());
    }

    /**
     * Marca el cronograma como revisado. Sin body; la respuesta es el
     * {@link CronogramaResponse} canónico (mismo shape que
     * {@code GET /presupuestos/{id}/cronograma}). Repetición con los mismos
     * datos → idempotente, sin nuevas escrituras.
     */
    @POST
    @Path("/{id}/revisado")
    @Consumes(MediaType.WILDCARD)
    public CronogramaResponse marcarRevisado(@PathParam("id") String id) {
        UUID publicId = UuidV7.parse(id);
        return vistasCronogramaService.marcarRevisado(publicId, usuarioId());
    }
}
