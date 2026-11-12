package ec.uce.propuestas.documento.exportacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.documento.EspecificacionesCapturaService;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import ec.uce.propuestas.documentos.contract.ExportUploadV1;
import ec.uce.propuestas.documentos.contract.SpecificationProjectionV1;
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

/** Core public POST: capture and reserve before sending an immutable projection to Documentos. */
@Path("/documentos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class ExportPostResource {
    @Inject
    SecurityIdentity identity;

    @Inject
    UsuarioRepository users;

    @Inject
    ObjectMapper json;

    @Inject
    CapturaDocumentoService capture;

    @Inject
    EspecificacionesCapturaService specifications;

    @Inject
    ExportReservationService reservations;

    @Inject
    RemoteExportUploadGateway uploads;

    @Inject
    DownloadTicketService tickets;

    @POST
    @Path("presupuesto/{id}/exportaciones")
    @io.smallrye.common.annotation.Blocking
    public Response budget(@PathParam("id") String id, @HeaderParam("Idempotency-Key") String key, byte[] body) {
        return export(id, key, body, "presupuesto");
    }

    @POST
    @Path("apus/{id}/exportaciones")
    @io.smallrye.common.annotation.Blocking
    public Response apus(@PathParam("id") String id, @HeaderParam("Idempotency-Key") String key, byte[] body) {
        return export(id, key, body, "apus");
    }

    @POST
    @Path("especificaciones-tecnicas/{id}/exportaciones")
    @io.smallrye.common.annotation.Blocking
    public Response technicalSpecifications(
            @PathParam("id") String id, @HeaderParam("Idempotency-Key") String key, byte[] body) {
        return export(id, key, body, "especificaciones-tecnicas");
    }

    private Response export(String rawId, String rawKey, byte[] body, String document) {
        UUID budgetId = UuidV7.parse(rawId);
        UUID idempotencyKey = UuidV7.parse(rawKey);
        var request = ExportRequestParser.parse(document, body, json);
        long caller = users.findByEmail(identity.getPrincipal().getName())
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario autenticado no encontrado"))
                .id;

        DocumentProjectionV1 projection = null;
        SpecificationProjectionV1 specification = null;
        List<PreflightDocumento.Detalle> warnings = List.of();
        String filename;
        if ("especificaciones-tecnicas".equals(document)) {
            specification = specifications
                    .capturar(budgetId, identity.getPrincipal().getName(), request.title1(), request.title2())
                    .projection();
            filename = "especificaciones-tecnicas-" + budgetId + ".docx";
        } else {
            var snapshot = capture.capturar(budgetId, caller, document, request.options());
            if (!snapshot.preflight().exportable()) {
                return Response.status(409)
                        .entity(snapshot.preflight())
                        .header("Cache-Control", "no-store")
                        .header("Referrer-Policy", "no-referrer")
                        .build();
            }
            projection = DocumentoContratoMapper.proyectar(snapshot, document, request.options());
            warnings = snapshot.preflight().warnings();
            filename = document + "-" + budgetId + "-v" + snapshot.version() + "."
                    + request.options().formato();
        }
        String requestHash = ExportFingerprint.hash(json, request.requestFields());
        String revisionHash = projection != null
                ? ExportFingerprint.hash(json, projection)
                : ExportRevision.hash(json, specification);
        var reserved = reservations.claim(caller, idempotencyKey, budgetId, document, requestHash, revisionHash);
        if ("RESERVADA".equals(reserved.state())) {
            var upload = new ExportUploadV1(
                    ExportUploadV1.VERSION,
                    reserved.objectKey(),
                    reserved.operationId().toString(),
                    ExportFingerprint.bind(reserved.operationId(), requestHash, revisionHash),
                    filename,
                    projection,
                    specification);
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
        // A failed specification audit leaves SUBIDA durable; retry resumes without rerendering.
        reservations.finalizeAudit(caller, reserved.operationId(), reserved.fence());
        var confirmed = reservations.confirmedMetadata(caller, reserved.operationId());
        var issued = tickets.issue(caller, reserved.operationId());
        var response = new DocumentoExportacionResponse(
                reserved.operationId(),
                "/api/v1/documentos/descargas/" + issued.token(),
                issued.expiresAt(),
                issued.objectExpiresAt(),
                confirmed.filename(),
                confirmed.mediaType(),
                confirmed.sizeBytes(),
                confirmed.sha256(),
                warnings);
        return Response.ok(response)
                .header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    public record DocumentoExportacionResponse(
            UUID operationId,
            String downloadUrl,
            Instant expiresAt,
            Instant objectExpiresAt,
            String filename,
            String mediaType,
            long sizeBytes,
            String sha256,
            List<PreflightDocumento.Detalle> warnings) {}
}
