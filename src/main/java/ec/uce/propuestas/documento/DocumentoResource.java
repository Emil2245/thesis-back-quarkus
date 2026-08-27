package ec.uce.propuestas.documento;

import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.service.ProyectoService;
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
import jakarta.ws.rs.core.Response;

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
 */
@Path("/documentos")
@Produces({MediaType.WILDCARD, MediaType.APPLICATION_JSON})
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class DocumentoResource {

    @Inject
    EspecificacionesTecnicasService especificacionesTecnicasService;

    @Inject
    ApuRepository apuRepository;

    @Inject
    ProyectoService proyectoService;

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
     * GET /documentos/especificaciones-tecnicas/{presupuestoId}?formato=docx&titulo1=&titulo2=
     * (P-45, N04 §ESP + N04-bis). Solo Long IDs en esta iteración.
     *
     * <p>La propiedad se valida por la cadena presupuesto → proyecto → usuario
     * autenticado (RNF-05). Un presupuesto ajeno responde 404.</p>
     */
    @GET
    @Path("/especificaciones-tecnicas/{presupuestoId}")
    public Response exportarEspecificacionesTecnicas(
            @PathParam("presupuestoId") Long presupuestoId,
            @QueryParam("formato") String formato,
            @QueryParam("titulo1") String titulo1,
            @QueryParam("titulo2") String titulo2) {
        if (formato != null && !formato.isBlank() && !"docx".equalsIgnoreCase(formato)) {
            throw ProblemaException.validacion("Formato no soportado: " + formato + " (solo DOCX en esta iteración)");
        }
        Long proyectoId = apuRepository
                .proyectoDePresupuesto(presupuestoId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
        Proyecto proyecto = proyectoService.validarPropietario(usuarioId(), proyectoId);

        ArchivoGenerado archivo = especificacionesTecnicasService.generar(presupuestoId, proyecto, titulo1, titulo2);

        return Response.ok(archivo.bytes(), archivo.mediaType())
                .header("Content-Disposition", "attachment; filename=\"" + archivo.nombreArchivo() + "\"")
                .build();
    }
}
