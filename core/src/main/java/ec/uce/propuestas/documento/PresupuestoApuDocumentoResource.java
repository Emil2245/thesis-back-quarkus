package ec.uce.propuestas.documento;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.documento.exportacion.OpcionesDocumento;
import ec.uce.propuestas.documento.exportacion.PreflightDocumento;
import ec.uce.propuestas.documento.exportacion.PreflightDocumentoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

/** Preflights de presupuesto y APUs; la descarga usa {@code POST …/exportaciones} (etapa 05). */
@Path("/documentos")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class PresupuestoApuDocumentoResource {
    @Inject
    PreflightDocumentoService preflight;

    @Inject
    SecurityIdentity identity;

    @Inject
    UsuarioRepository usuarios;

    @GET
    @Path("/presupuesto/{id}/preflight")
    public PreflightDocumento presupuesto(@PathParam("id") String id, @Context UriInfo uri) {
        return evaluar(id, "presupuesto", uri);
    }

    @GET
    @Path("/apus/{id}/preflight")
    public PreflightDocumento apus(@PathParam("id") String id, @Context UriInfo uri) {
        return evaluar(id, "apus", uri);
    }

    private PreflightDocumento evaluar(String id, String documento, UriInfo uri) {
        var uuid = UuidV7.parse(id);
        var opciones = OpcionesDocumento.parsear(documento, uri.getQueryParameters());
        Long caller = usuarios.findByEmail(identity.getPrincipal().getName())
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario autenticado no encontrado"))
                .id;
        return preflight.evaluar(uuid, caller, documento, opciones);
    }
}
