package ec.uce.propuestas.documento.exportacion;

import ec.uce.propuestas.common.GlobalExceptionMapper;
import ec.uce.propuestas.documento.ArchivoGenerado;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Core-only one-use capability proxy; the ticket is consumed before private S3 access. */
@Path("/documentos/descargas")
@PermitAll
public class PrivateDownloadResource {
    @Inject
    DownloadTicketService tickets;

    @Inject
    CorePrivateObjectReader reader;

    @GET
    @Path("{ticket}")
    @Produces({
        MediaType.APPLICATION_OCTET_STREAM,
        MediaType.APPLICATION_JSON,
        ArchivoGenerado.PDF_MEDIA_TYPE,
        ArchivoGenerado.XLSX_MEDIA_TYPE,
        ArchivoGenerado.DOCX_MEDIA_TYPE
    })
    @Blocking
    public Response download(@PathParam("ticket") String token) {
        try {
            var object = tickets.consume(token);
            byte[] bytes = reader.read(object);
            return privateResponse(Response.ok(bytes, object.mediaType())
                            .header("Content-Disposition", "attachment; filename=\"" + object.filename() + "\""))
                    .build();
        } catch (RuntimeException failure) {
            return privateResponse(Response.fromResponse(new GlobalExceptionMapper().toResponse(failure))
                            .type(MediaType.APPLICATION_JSON))
                    .build();
        }
    }

    private static Response.ResponseBuilder privateResponse(Response.ResponseBuilder builder) {
        return builder.header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff");
    }
}
