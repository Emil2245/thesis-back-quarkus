package ec.uce.propuestas.documento;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.cronograma.dto.CronogramaExportPreflightResponse;
import ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService;
import ec.uce.propuestas.cronograma.export.FormatoExportacion;
import ec.uce.propuestas.cronograma.export.PapelCronograma;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

/**
 * Plan 031 (P-37) — preflight REST de exportación del cronograma. La descarga
 * usa {@code POST …/exportaciones} ({@link CronogramaExportPostResource}); el GET
 * de bytes se retiró en la etapa 05.
 * Ruta:
 *
 * <ul>
 *   <li>{@code GET /documentos/cronograma/{presupuestoId}/preflight?formato=xlsx|pdf|mspdi}</li>
 * </ul>
 *
 * <p>Reglas del canon:
 * <ul>
 *   <li>UUID mal formado o no-v7 → 400 {@code validacion}.</li>
 *   <li>Formato no soportado → 400 {@code validacion}.</li>
 *   <li>Presupuesto ajeno o inexistente → 404 {@code no-encontrado}.</li>
 * </ul>
 */
@Path("/documentos/cronograma")
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class CronogramaDocumentoResource {

    @Inject
    CronogramaExportPreflightService preflightService;

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

    private FormatoExportacion parsearFormato(String formato) {
        FormatoExportacion f = FormatoExportacion.parsear(formato);
        if (f == null) {
            throw ProblemaException.validacion("Formato no soportado: "
                    + (formato == null ? "(ausente)" : formato)
                    + " (permitidos: xlsx | pdf | mspdi)");
        }
        return f;
    }

    @jakarta.ws.rs.core.Context
    jakarta.ws.rs.core.UriInfo uriInfo;

    private PapelCronograma parsearPapel(String papel, FormatoExportacion formato) {
        // RESTEasy convierte el valor vacío a null; distinguirlo de la ausencia.
        boolean presente = uriInfo.getQueryParameters().containsKey("papel");
        if (!presente) {
            return PapelCronograma.A4;
        }
        if (formato != FormatoExportacion.PDF
                || uriInfo.getQueryParameters().get("papel").size() != 1
                || !("a4".equals(papel) || "a3".equals(papel))) {
            throw ProblemaException.validacion("Papel no soportado: solo PDF permite papel=a4 | a3");
        }
        return papel.equals("a3") ? PapelCronograma.A3 : PapelCronograma.A4;
    }

    /**
     * Preflight: devuelve la respuesta canónica con bloqueos y warnings. No
     * genera bytes; permite al cliente decidir antes de la descarga.
     */
    @GET
    @Path("/{presupuestoId}/preflight")
    @Produces(MediaType.APPLICATION_JSON)
    public CronogramaExportPreflightResponse preflight(
            @PathParam("presupuestoId") String presupuestoId,
            @QueryParam("formato") String formato,
            @QueryParam("papel") String papel) {
        UUID publicId = UuidV7.parse(presupuestoId);
        FormatoExportacion f = parsearFormato(formato);
        parsearPapel(papel, f);
        try {
            return preflightService.evaluarPreflight(publicId, usuarioId(), f);
        } catch (IllegalArgumentException e) {
            throw ProblemaException.noEncontrado(e.getMessage());
        }
    }
}
