package ec.uce.propuestas.documento;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.cronograma.dto.BloqueoExportDetalle;
import ec.uce.propuestas.cronograma.dto.CronogramaExportPreflightResponse;
import ec.uce.propuestas.cronograma.export.CronogramaDescargaService;
import ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService;
import ec.uce.propuestas.cronograma.export.FormatoExportacion;
import ec.uce.propuestas.usuario.UsuarioRepository;
import ec.uce.propuestas.usuario.audit.EventoLogActividad;
import ec.uce.propuestas.usuario.audit.service.LogActividadService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;

/**
 * Plan 031 (P-37) — endpoint REST de exportación documental del cronograma.
 * Rutas:
 *
 * <ul>
 *   <li>{@code GET /documentos/cronograma/{presupuestoId}/preflight?formato=xlsx|pdf|mspdi}</li>
 *   <li>{@code GET /documentos/cronograma/{presupuestoId}?formato=xlsx|pdf|mspdi}</li>
 * </ul>
 *
 * <p>Reglas del canon:
 * <ul>
 *   <li>UUID mal formado o no-v7 → 400 {@code validacion}.</li>
 *   <li>Formato no soportado → 400 {@code validacion}.</li>
 *   <li>Presupuesto ajeno o inexistente → 404 {@code no-encontrado}.</li>
 *   <li>Descarga bloqueada → 409 {@code export-bloqueado} con cuerpo tipado
 *       {@link BloqueoExportDetalle} que conserva los arreglos
 *       {@code bloqueos[]} y {@code warnings[]} del MISMO snapshot que vio el
 *       writer (audit closure §409 payload loss).</li>
 *   <li>Warning stale: header {@code X-Cronograma-Desactualizado: true|false}.</li>
 * </ul>
 *
 * <p>El método de generación está marcado {@code @Blocking} para evitar que se
 * ejecute en el event loop de RESTEasy Reactive cuando la proyección
 * completa es pesada — Plan 031 §NFR.</p>
 */
@Path("/documentos/cronograma")
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class CronogramaDocumentoResource {

    @Inject
    CronogramaExportPreflightService preflightService;

    @Inject
    CronogramaDescargaService descargaService;

    @Inject
    SecurityIdentity identity;

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    LogActividadService logActividadService;

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

    /**
     * Preflight: devuelve la respuesta canónica con bloqueos y warnings. No
     * genera bytes; permite al cliente decidir antes de la descarga.
     */
    @GET
    @Path("/{presupuestoId}/preflight")
    @Produces(MediaType.APPLICATION_JSON)
    public CronogramaExportPreflightResponse preflight(
            @PathParam("presupuestoId") String presupuestoId, @QueryParam("formato") String formato) {
        UUID publicId = UuidV7.parse(presupuestoId);
        FormatoExportacion f = parsearFormato(formato);
        try {
            return preflightService.evaluarPreflight(publicId, usuarioId(), f);
        } catch (IllegalArgumentException e) {
            throw ProblemaException.noEncontrado(e.getMessage());
        }
    }

    /**
     * Descarga: delega una sola vez al {@link CronogramaDescargaService} que
     * mantiene una transacción única REQUIRED desde el lock pesimista del
     * presupuesto hasta la materialización de bytes (Plan 031 §TOCTOU). El
     * método se marca explícitamente como {@code @Blocking} (Quarkus REST)
     * porque la serialización XLSX/PDF no debe ejecutarse en el event loop.
     */
    @GET
    @Path("/{presupuestoId}")
    @Produces({
        ArchivoGenerado.XLSX_MEDIA_TYPE,
        ArchivoGenerado.PDF_MEDIA_TYPE,
        ArchivoGenerado.MSPDI_MEDIA_TYPE,
        MediaType.APPLICATION_JSON
    })
    @io.smallrye.common.annotation.Blocking
    @Transactional
    public Response descargar(@PathParam("presupuestoId") String presupuestoId, @QueryParam("formato") String formato) {
        UUID publicId = UuidV7.parse(presupuestoId);
        FormatoExportacion f = parsearFormato(formato);
        Long callerUsuarioId = usuarioId();

        CronogramaDescargaService.ResultadoDescarga gen;
        try {
            gen = descargaService.generar(publicId, callerUsuarioId, f);
        } catch (IllegalArgumentException e) {
            // Mapeo canónico: cualquier IAE del seam preflight/descarga es
            // semánticamente «no-encontrado» (404 — preserva RNF-05 owner-to-404).
            throw ProblemaException.noEncontrado(e.getMessage());
        }

        if (!gen.exportable()) {
            // 409 export-bloqueado con cuerpo tipado que conserva los arreglos
            // bloqueos[]/warnings[] del MISMO snapshot que vio el writer
            // (TOCTOU cerrado — audit closure §409 payload loss). El resource
            // nunca recalcula ni omite información: el cuerpo es exactamente
            // el snapshot congelado por el service.
            BloqueoExportDetalle body = new BloqueoExportDetalle(
                    publicId,
                    f.token(),
                    "export-bloqueado",
                    "Exportación bloqueada: " + gen.bloqueos().bloqueos().size() + " bloqueo(s)",
                    gen.bloqueos().bloqueos(),
                    gen.bloqueos().advertencias());
            return Response.status(409)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(body)
                    .header("X-Cronograma-Desactualizado", String.valueOf(gen.desactualizado()))
                    .build();
        }

        logActividadService.emitir(
                callerUsuarioId,
                EventoLogActividad.DOCUMENTO_EXPORTADO,
                "presupuesto",
                publicId,
                Map.of("formato", f.name(), "bytes", (long) gen.bytes().length, "stale", gen.desactualizado()));
        return Response.ok(gen.bytes(), gen.mediaType())
                .header("Content-Disposition", "attachment; filename=\"" + gen.filename() + "\"")
                .header("X-Cronograma-Desactualizado", String.valueOf(gen.desactualizado()))
                .build();
    }
}
