package ec.uce.propuestas.apu.resource;

import ec.uce.propuestas.apu.dto.ApuCrearRequest;
import ec.uce.propuestas.apu.dto.ApuResponse;
import ec.uce.propuestas.apu.dto.ApuResumenResponse;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.service.ApuCrudService;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.proyecto.service.ProyectoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

/**
 * APUs de una versión de presupuesto (07-api-contract.md §5, P-19/P-20).
 * Ruta {@code /presupuestos/{presupuestoId}/apus}.
 *
 * <p>Plan 07 — el {@code presupuestoId} del path es la identidad externa
 * UUIDv7 (columna {@code presupuesto.public_id}). El parse se hace con
 * {@link UuidV7#parse}: UUID mal formado o no-v7 → 400 {@code validacion};
 * presupuesto ajeno o inexistente → 404 {@code no-encontrado}. El
 * {@code BIGINT} interno se retiene debajo del resource y de los services;
 * nunca se expone.</p>
 *
 * <p>Plan 04 (P-26) — {@code POST} acepta un {@code plantillaId} opcional
 * (UUIDv7). Cuando viene:
 * <ul>
 *   <li>{@code 201 Created} — sin advertencias (todos los insumos resueltos).</li>
 *   <li>{@code 200 OK} — con {@code advertencias[]} no vacío (códigos no
 *       resueltos en PROYECTO/CENTRAL/PERSONAL visible).</li>
 * </ul>
 * Sin plantillaId el comportamiento es el previo (siempre 201).
 */
@Path("/presupuestos/{presupuestoId}/apus")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class PresupuestoApuResource {

    @Inject
    ApuCrudService apuService;

    @Inject
    ApuRepository apuRepository;

    @Inject
    PresupuestoRepository presupuestoRepository;

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

    private Long resolverPresupuestoInterno(String presupuestoIdStr) {
        UUID presupuestoPublicId = UuidV7.parse(presupuestoIdStr);
        Presupuesto presupuesto = presupuestoRepository
                .findByPublicIdAndOwnerScope(presupuestoPublicId, usuarioId())
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
        Long proyectoId = presupuesto.proyectoId;
        proyectoService.validarPropietario(usuarioId(), proyectoId);
        return presupuesto.id;
    }

    @GET
    @Consumes(MediaType.WILDCARD)
    public Page<ApuResumenResponse> listar(
            @PathParam("presupuestoId") String presupuestoId,
            @QueryParam("q") String q,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size) {
        Long presupuestoInternoId = resolverPresupuestoInterno(presupuestoId);
        return apuService.listar(presupuestoInternoId, q, page, size);
    }

    /**
     * Plan 04 (P-26) — Crea un APU. Si el body trae {@code plantillaId}, el
     * resultado se devuelve como {@code 200 OK} cuando hay advertencias
     * (insumos no resueltos) y como {@code 201 Created} cuando todo se
     * resuelve. Sin plantillaId sigue siendo 201.
     */
    @POST
    public Response crear(@PathParam("presupuestoId") String presupuestoId, @Valid ApuCrearRequest req) {
        Long presupuestoInternoId = resolverPresupuestoInterno(presupuestoId);
        Long caller = usuarioId();
        ApuCrearRequest normalizado = normalizarPlantillaId(req);
        ApuCrudService.ResultadoCrear resultado = apuService.crear(presupuestoInternoId, normalizado, caller);

        ApuResponse conAdvertencias = normalizado.tienePlantilla()
                ? conAdvertencias(resultado.apu(), resultado.advertencias())
                : resultado.apu();

        if (resultado.tieneAdvertencias()) {
            return Response.ok(conAdvertencias).build();
        }
        return Response.status(Response.Status.CREATED).entity(conAdvertencias).build();
    }

    /**
     * Si viene {@code plantillaId}, se valida como UUIDv7 (frontera REST).
     * Si no, deja el body intacto. Esto mantiene la compatibilidad con los
     * callers existentes que no envían plantillaId.
     */
    private ApuCrearRequest normalizarPlantillaId(ApuCrearRequest req) {
        if (req == null || req.plantillaId() == null) {
            return req;
        }
        // Validar v7 con UuidV7 para fallar temprano con 400 validacion.
        UUID v7 = UuidV7.parse(req.plantillaId().toString());
        if (!v7.equals(req.plantillaId())) {
            throw ProblemaException.validacion("Identificador público inválido: se requiere UUIDv7");
        }
        return req;
    }

    /**
     * Construye una copia del response con {@code advertencias} poblado. La
     * lista inmutable de {@code secciones} y derivados se reutiliza — sólo
     * añadimos el campo opcional.
     */
    private ApuResponse conAdvertencias(
            ApuResponse base,
            java.util.List<ec.uce.propuestas.plantilla.dto.AdvertenciaPlantillaResponse> advertencias) {
        if (advertencias == null || advertencias.isEmpty()) {
            return base;
        }
        return new ApuResponse(
                base.id(),
                base.codigo(),
                base.descripcion(),
                base.unidad(),
                base.costoDirecto(),
                base.costoIndirecto(),
                base.costoTotal(),
                base.porcentajeIndirecto(),
                base.porcentajeIndirectoEfectivo(),
                base.porcentajeDescuento(),
                base.secciones(),
                advertencias);
    }
}
