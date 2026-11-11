package ec.uce.propuestas.documento;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.cronograma.dto.BloqueoExportDetalle;
import ec.uce.propuestas.cronograma.dto.WarningExportResponse;
import ec.uce.propuestas.cronograma.export.CronogramaCaptureService;
import ec.uce.propuestas.documento.exportacion.DocumentosExportClient;
import ec.uce.propuestas.documento.exportacion.DownloadTicketService;
import ec.uce.propuestas.documento.exportacion.ExportFingerprint;
import ec.uce.propuestas.documento.exportacion.ExportReservationService;
import ec.uce.propuestas.documento.exportacion.RemoteExportUploadGateway;
import ec.uce.propuestas.documentos.contract.ExportUploadV1;
import ec.uce.propuestas.documentos.contract.StoredObjectV1;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Path("/documentos/cronograma/{presupuestoId}/exportaciones")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class CronogramaExportPostResource {
    @Inject
    SecurityIdentity identity;

    @Inject
    UsuarioRepository users;

    @Inject
    ObjectMapper json;

    @Inject
    CronogramaCaptureService capture;

    @Inject
    ExportReservationService reservations;

    @Inject
    RemoteExportUploadGateway uploads;

    @Inject
    DownloadTicketService tickets;

    @POST
    @io.smallrye.common.annotation.Blocking
    public Response export(
            @PathParam("presupuestoId") String rawId, @HeaderParam("Idempotency-Key") String rawKey, byte[] body) {
        UUID budgetId = UuidV7.parse(rawId);
        UUID key = UuidV7.parse(rawKey);
        var request = CronogramaExportRequestParser.parse(body, json);
        long caller = users.findByEmail(identity.getPrincipal().getName())
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario autenticado no encontrado"))
                .id;
        CronogramaCaptureService.Capture captured;
        try {
            captured = capture.capture(budgetId, caller, request.format(), request.paper());
        } catch (IllegalArgumentException missing) {
            throw ProblemaException.noEncontrado(missing.getMessage());
        }
        if (!captured.preflight().exportable()) {
            var result = captured.preflight();
            var detail = new BloqueoExportDetalle(
                    budgetId,
                    request.format().token(),
                    "export-bloqueado",
                    "Exportación bloqueada: " + result.bloqueos().size() + " bloqueo(s)",
                    result.bloqueos(),
                    result.advertencias());
            return Response.status(409)
                    .entity(detail)
                    .header("Cache-Control", "no-store")
                    .header("Referrer-Policy", "no-referrer")
                    .build();
        }
        String requestHash = ExportFingerprint.hash(json, request.requestFields());
        String revisionHash = captured.revisionHash();
        var reserved = reservations.claim(caller, key, budgetId, "cronograma", requestHash, revisionHash);
        if ("RESERVADA".equals(reserved.state())) {
            var upload = new ExportUploadV1(
                    ExportUploadV1.VERSION,
                    reserved.objectKey(),
                    reserved.operationId().toString(),
                    ExportFingerprint.bind(reserved.operationId(), requestHash, revisionHash),
                    "cronograma-" + budgetId + "."
                            + ("mspdi".equals(request.format().token())
                                    ? "xml"
                                    : request.format().token()),
                    null,
                    null,
                    captured.projection());
            StoredObjectV1 stored;
            try {
                stored = uploads.upload(upload);
            } catch (DocumentosExportClient.UploadUncertainException uncertain) {
                stored = uploads.reconcile(upload);
            }
            reservations.confirmUpload(
                    caller,
                    reserved.operationId(),
                    reserved.fence(),
                    stored.key(),
                    stored.checksumSha256(),
                    stored.size(),
                    stored.mimeType(),
                    stored.filename());
        }
        reservations.finalizeCronogramaAudit(
                caller,
                reserved.operationId(),
                reserved.fence(),
                request.format().name(),
                captured.stale());
        var confirmed = reservations.confirmedMetadata(caller, reserved.operationId());
        var issued = tickets.issue(caller, reserved.operationId());
        return Response.ok(new ExportResponse(
                        reserved.operationId(),
                        "/api/v1/documentos/descargas/" + issued.token(),
                        issued.expiresAt(),
                        issued.objectExpiresAt(),
                        confirmed.filename(),
                        confirmed.mediaType(),
                        confirmed.sizeBytes(),
                        confirmed.sha256(),
                        captured.preflight().advertencias()))
                .header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    public record ExportResponse(
            UUID operationId,
            String downloadUrl,
            Instant expiresAt,
            Instant objectExpiresAt,
            String filename,
            String mediaType,
            long sizeBytes,
            String sha256,
            List<WarningExportResponse> warnings) {}
}
