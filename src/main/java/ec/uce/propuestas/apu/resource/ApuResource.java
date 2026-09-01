package ec.uce.propuestas.apu.resource;

import ec.uce.propuestas.apu.dto.ApuCalculoResponse;
import ec.uce.propuestas.apu.dto.ApuDetalleCrearRequest;
import ec.uce.propuestas.apu.dto.ApuDetallePatchRequest;
import ec.uce.propuestas.apu.dto.ApuDuplicarRequest;
import ec.uce.propuestas.apu.dto.ApuPatchRequest;
import ec.uce.propuestas.apu.dto.ApuResponse;
import ec.uce.propuestas.apu.dto.EspecificacionTecnicaRequest;
import ec.uce.propuestas.apu.dto.EspecificacionTecnicaResponse;
import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.apu.repository.ApuDetalleRepository;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.service.ApuCalculoService;
import ec.uce.propuestas.apu.service.ApuCrudService;
import ec.uce.propuestas.apu.service.ApuDuplicarService;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Agregado {@code apu} (07-api-contract.md §5, P-21/P-22). Ruta
 * {@code /apus/{apuId}}. El usuario solo opera sobre sus propios recursos (RNF-05).
 *
 * <p>El {@code apuId} y el {@code detalleId} de los path params son UUIDv7
 * (identidad externa inmutable, columna {@code public_id}, OpenSpec WU-03). El parse
 * se hace en este resource con {@link UuidV7#parse(String)} para que un UUID mal
 * formado o de versión incorrecta rechace con el contrato 400 {@code validacion} —
 * antes de cualquier acceso al repositorio.
 *
 * <p>La resolución de ownership usa {@link ApuRepository#findByPublicIdAndOwnerScope}
 * y {@link ApuDetalleRepository#findByPublicIdAndOwnerScope}; una fila de un proyecto
 * ajeno, o una fila inexistente, devuelven {@code Optional.empty()} que aquí se traduce
 * al contrato 404 {@code no-encontrado} (sin filtrar si el UUID existe en otro
 * proyecto). El {@code BIGINT} interno se retiene debajo de este resource y de los
 * services; nunca se expone.
 */
@Path("/apus/{apuId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class ApuResource {

    @Inject
    ApuCrudService apuService;

    @Inject
    ApuDuplicarService duplicarService;

    @Inject
    ApuCalculoService calculoService;

    @Inject
    ApuRepository apuRepository;

    @Inject
    ApuDetalleRepository apuDetalleRepository;

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
     * Resuelve el APU por su UUID público + scope de owner. Devuelve la entidad interna
     * (Long id, UUID publicId) lista para pasar al service. Cualquier UUID que no
     * pertenezca al caller (404 — RNF-05), que esté mal formado o que no sea v7 se
     * rechaza antes de tocar la base de datos.
     */
    private Apu resolverApu(String apuIdStr) {
        UUID apuPublicId = UuidV7.parse(apuIdStr);
        return apuRepository
                .findByPublicIdAndOwnerScope(apuPublicId, usuarioId())
                .orElseThrow(() -> ProblemaException.noEncontrado("APU no encontrado"));
    }

    /**
     * Resuelve el APU y la fila hija por sus UUIDs públicos + scope de owner y
     * verifica el cruce padre↔hijo: la sección del detalle debe ser una sección del
     * APU del path. Esto cierra la grieta donde un par de UUIDs ajenos válidos podía
     * combinarse para editar filas de un APU que no le pertenece al caller.
     */
    private record ApuConDetalle(Apu apu, ApuDetalle detalle) {}

    private ApuConDetalle resolverApuYDetalle(String apuIdStr, String detalleIdStr) {
        Apu apu = resolverApu(apuIdStr);
        UUID detallePublicId = UuidV7.parse(detalleIdStr);
        ApuDetalle detalle = apuDetalleRepository
                .findByPublicIdAndOwnerScope(detallePublicId, usuarioId())
                .orElseThrow(() -> ProblemaException.noEncontrado("Fila no encontrada en este APU"));
        if (!seccionPerteneceAApu(detalle.seccionId, apu.id)) {
            throw ProblemaException.noEncontrado("Fila no encontrada en este APU");
        }
        return new ApuConDetalle(apu, detalle);
    }

    private boolean seccionPerteneceAApu(Long seccionId, Long apuId) {
        return apuRepository
                        .getEntityManager()
                        .createQuery(
                                "select count(s) from ApuSeccion s where s.id = :seccionId and s.apuId = :apuId",
                                Long.class)
                        .setParameter("seccionId", seccionId)
                        .setParameter("apuId", apuId)
                        .getSingleResult()
                > 0;
    }

    @GET
    @Consumes(MediaType.WILDCARD)
    public ApuResponse obtener(@PathParam("apuId") String apuId) {
        Apu apu = resolverApu(apuId);
        return apuService.obtener(apu.id);
    }

    @PATCH
    public ApuResponse editarCabecera(@PathParam("apuId") String apuId, @Valid ApuPatchRequest req) {
        Apu apu = resolverApu(apuId);
        return apuService.editarCabecera(apu.id, req);
    }

    @PATCH
    @Path("/porcentaje-indirecto")
    public ApuResponse actualizarPorcentajeIndirecto(@PathParam("apuId") String apuId, BigDecimal valor) {
        Apu apu = resolverApu(apuId);
        return apuService.actualizarPorcentajeIndirecto(apu.id, valor);
    }

    /** P-45 (N04 §ESP). GET retorna la forma JSON estable del ET del APU. */
    @GET
    @Path("/especificacion-tecnica")
    @Consumes(MediaType.WILDCARD)
    public EspecificacionTecnicaResponse obtenerEspecificacionTecnica(@PathParam("apuId") String apuId) {
        Apu apu = resolverApu(apuId);
        return apuService.obtenerEspecificacionTecnica(apu.id);
    }

    /** P-45 (N04 §ESP). PUT persiste el ET. {@code texto} null o vacío = limpiar. */
    @PUT
    @Path("/especificacion-tecnica")
    public ApuResponse guardarEspecificacionTecnica(
            @PathParam("apuId") String apuId, EspecificacionTecnicaRequest req) {
        Apu apu = resolverApu(apuId);
        return apuService.guardarEspecificacionTecnica(apu.id, req == null ? null : req.texto());
    }

    @DELETE
    public Response eliminar(@PathParam("apuId") String apuId) {
        Apu apu = resolverApu(apuId);
        apuService.eliminar(apu.id);
        return Response.noContent().build();
    }

    @POST
    @Path("/detalles")
    public Response agregarDetalle(@PathParam("apuId") String apuId, @Valid ApuDetalleCrearRequest req) {
        Apu apu = resolverApu(apuId);
        ApuResponse actualizado = apuService.agregarDetalle(apu.id, req, usuarioId());
        return Response.status(Response.Status.CREATED).entity(actualizado).build();
    }

    @PATCH
    @Path("/detalles/{detalleId}")
    public ApuResponse editarDetalle(
            @PathParam("apuId") String apuId,
            @PathParam("detalleId") String detalleId,
            @Valid ApuDetallePatchRequest req) {
        ApuConDetalle par = resolverApuYDetalle(apuId, detalleId);
        return apuService.editarDetalle(par.apu().id, par.detalle().id, req);
    }

    @DELETE
    @Path("/detalles/{detalleId}")
    public ApuResponse eliminarDetalle(@PathParam("apuId") String apuId, @PathParam("detalleId") String detalleId) {
        ApuConDetalle par = resolverApuYDetalle(apuId, detalleId);
        return apuService.eliminarDetalle(par.apu().id, par.detalle().id);
    }

    /** POST /apus/{apuId}/duplicar (dossier §B.7 opción a). Body opcional: {@code copiarET} (default false). */
    @POST
    @Path("/duplicar")
    public Response duplicar(@PathParam("apuId") String apuId, ApuDuplicarRequest req) {
        Apu apu = resolverApu(apuId);
        Boolean copiarET = req == null ? null : req.copiarET();
        ApuResponse copia = duplicarService.duplicar(apu.id, copiarET);
        return Response.status(Response.Status.CREATED).entity(copia).build();
    }

    /** P-27 (dossier §B.8). Desglose de cálculo del APU (solo proyecta, no recalcula). */
    @GET
    @Path("/calculo")
    @Consumes(MediaType.WILDCARD)
    public ApuCalculoResponse calculo(@PathParam("apuId") String apuId) {
        Apu apu = resolverApu(apuId);
        return calculoService.proyectar(apu);
    }
}
