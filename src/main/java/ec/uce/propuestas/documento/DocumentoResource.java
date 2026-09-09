package ec.uce.propuestas.documento;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.service.ProyectoService;
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
 * Documentos exportables (08-codebase-design.md §4, 07-api-contract.md §5 P-45 +
 * §8 P-37). Ruta {@code /documentos}.
 *
 * <p>Esta iteración implementa únicamente el writer de Especificaciones Técnicas
 * (DOCX) — el resto (APU/presupuesto/cronograma en xlsx/pdf) entra con P-37.</p>
 *
 * <p>El recurso emite tanto DOCX (cabecera P-45, attachment) como JSON para los
 * {@code ErrorPayload} que devuelven los exception mappers. Declarar ambos
 * Content-Type a nivel clase garantiza que REST Assured (u otro cliente) pueda
 * negociar JSON cuando el servidor responde con un 4xx de ProblemDetails.</p>
 *
 * <p>Plan 07 — el {@code presupuestoId} del path es la identidad externa
 * UUIDv7 (columna {@code presupuesto.public_id}). El parse se hace con
 * {@link UuidV7#parse}: UUID mal formado o no-v7 → 400 {@code validacion};
 * presupuesto ajeno o inexistente → 404 {@code no-encontrado}. El
 * {@code BIGINT} interno se retiene debajo del resource y de los services;
 * nunca se expone.</p>
 */
@Path("/documentos")
@Produces({MediaType.WILDCARD, MediaType.APPLICATION_JSON})
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class DocumentoResource {

    @Inject
    EspecificacionesTecnicasService especificacionesTecnicasService;

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    ProyectoService proyectoService;

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

    /**
     * GET /documentos/especificaciones-tecnicas/{presupuestoId}?formato=docx&titulo1=&titulo2=
     * (P-45, N04 §ESP + N04-bis).
     *
     * <p>La propiedad se valida por la cadena presupuesto → proyecto → usuario
     * autenticado (RNF-05). Un presupuesto ajeno responde 404. UUID mal
     * formado o no-v7 devuelve 400.</p>
     */
    @GET
    @Path("/especificaciones-tecnicas/{presupuestoId}")
    @Transactional
    public Response exportarEspecificacionesTecnicas(
            @PathParam("presupuestoId") String presupuestoId,
            @QueryParam("formato") String formato,
            @QueryParam("titulo1") String titulo1,
            @QueryParam("titulo2") String titulo2) {
        if (formato != null && !formato.isBlank() && !"docx".equalsIgnoreCase(formato)) {
            throw ProblemaException.validacion("Formato no soportado: " + formato + " (solo DOCX en esta iteración)");
        }
        UUID presupuestoPublicId = UuidV7.parse(presupuestoId);
        Long callerUsuarioId = usuarioId();
        Presupuesto presupuesto = presupuestoRepository
                .findByPublicIdAndOwnerScope(presupuestoPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
        Proyecto proyecto = proyectoService.validarPropietario(callerUsuarioId, presupuesto.proyectoId);

        ArchivoGenerado archivo = especificacionesTecnicasService.generar(presupuesto.id, proyecto, titulo1, titulo2);
        logActividadService.emitir(
                callerUsuarioId,
                EventoLogActividad.DOCUMENTO_EXPORTADO,
                "presupuesto",
                presupuestoPublicId,
                Map.of("formato", "DOCX", "bytes", (long) archivo.bytes().length, "stale", false));

        return Response.ok(archivo.bytes(), archivo.mediaType())
                .header("Content-Disposition", "attachment; filename=\"" + archivo.nombreArchivo() + "\"")
                .build();
    }
}
