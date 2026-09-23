package ec.uce.propuestas.insumo.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.insumo.dto.BasePersonalCrearRequest;
import ec.uce.propuestas.insumo.dto.BasePersonalResponse;
import ec.uce.propuestas.insumo.dto.ImportResultadoResponse;
import ec.uce.propuestas.insumo.dto.InsumoCrearRequest;
import ec.uce.propuestas.insumo.dto.InsumoEditarRequest;
import ec.uce.propuestas.insumo.dto.InsumoResponse;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.insumo.service.BaseInsumosService;
import ec.uce.propuestas.insumo.service.BasesPersonalesService;
import ec.uce.propuestas.insumo.service.ImportacionInsumoService;
import ec.uce.propuestas.insumo.service.InsumoCrudService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * WU-05 — Bases PERSONALES del usuario autenticado (N04 §A9).
 *
 * <p>Ruta {@code /bases-personales}. La identidad del dueño, el tipo y la
 * ausencia de proyecto padre se fijan siempre desde el JWT en el servidor:
 * el cliente nunca puede elegir dueño, tipo, proyecto ni {@code publicId}
 * (RNF-05). Solo se exponen las bases PERSONALES del caller; las filas
 * CENTRAL/PROYECTO nunca aparecen aquí.</p>
 *
 * <p>La seam de lookup por {@code publicId} vive en el servicio para que el
 * siguiente bloque de copia-al-usar la reutilice sin reabrir el seam aquí.</p>
 */
@Path("/bases-personales")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class BasesPersonalesResource {

    @Inject
    BasesPersonalesService basesPersonalesService;

    @Inject
    SecurityIdentity identity;

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    BaseInsumosService baseInsumosService;

    @Inject
    InsumoCrudService crud;

    @Inject
    ImportacionInsumoService importacion;

    /** Lista bases PERSONALES del caller. CENTRAL/PROYECTO nunca aparecen. */
    @GET
    @Consumes(MediaType.WILDCARD)
    public List<BasePersonalResponse> listar() {
        return basesPersonalesService.listar(usuarioId());
    }

    /** Crea una base PERSONAL a nombre del caller. */
    @POST
    public Response crear(@Valid BasePersonalCrearRequest req) {
        BasePersonalResponse body = basesPersonalesService.crear(usuarioId(), req);
        return Response.status(Response.Status.CREATED).entity(body).build();
    }

    /**
     * Plan 05 — Borrado físico con owner-to-404. Devuelve 204 si la base
     * existe y pertenece al caller; 404 en cualquier otro caso (base
     * ajena, CENTRAL o PROYECTO — nunca 403, RNF-05). Los insumos asociados
     * se eliminan por la FK CASCADE de V001.
     *
     * <p>Plan 07 — el path param es {@code String} y se valida como UUIDv7
     * en la frontera: un UUID mal formado o de versión distinta devuelve 400
     * {@code validacion} antes de cualquier acceso al repositorio.
     */
    @DELETE
    @Path("/{id}")
    public Response borrar(@PathParam("id") String id) {
        UUID publicId = UuidV7.parse(id);
        boolean borrada = basesPersonalesService.borrar(publicId, usuarioId());
        if (!borrada) {
            throw ProblemaException.noEncontrado("Base personal no encontrada");
        }
        return Response.noContent().build();
    }

    // =========================================================================
    // Plan 044 — insumos de una base PERSONAL (bugs-pendientes §5)
    //
    // Mismo CRUD y mismas validaciones que la base PROYECTO: se reutilizan
    // InsumoCrudService e ImportacionInsumoService con el BIGINT de la base,
    // igual que ya hace AdminBaseCentralResource. La base se resuelve siempre
    // owner-scoped: base ajena, CENTRAL o PROYECTO -> 404 (nunca 403, RNF-05).
    // =========================================================================

    @GET
    @Path("/{id}/insumos")
    @Consumes(MediaType.WILDCARD)
    public Page<InsumoResponse> listarInsumos(
            @PathParam("id") String id,
            @QueryParam("tipo") TipoInsumo tipo,
            @QueryParam("q") String q,
            @QueryParam("desactualizados") boolean desactualizados,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size) {
        BaseInsumos base = basePropia(id);
        return baseInsumosService.listarInsumosBase(base.id, tipo, q, desactualizados, page, size);
    }

    @POST
    @Path("/{id}/insumos")
    public Response crearInsumo(@PathParam("id") String id, @Valid InsumoCrearRequest req) {
        BaseInsumos base = basePropia(id);
        return Response.status(Response.Status.CREATED)
                .entity(crud.crear(base.id, req))
                .build();
    }

    @PUT
    @Path("/{id}/insumos/{insumoId}")
    public InsumoResponse editarInsumo(
            @PathParam("id") String id, @PathParam("insumoId") String insumoId, @Valid InsumoEditarRequest req) {
        UUID insumoPublicId = UuidV7.parse(insumoId);
        BaseInsumos base = basePropia(id);
        return crud.actualizar(base.id, insumoPublicId, req);
    }

    @DELETE
    @Path("/{id}/insumos/{insumoId}")
    public Response eliminarInsumo(@PathParam("id") String id, @PathParam("insumoId") String insumoId) {
        UUID insumoPublicId = UuidV7.parse(insumoId);
        BaseInsumos base = basePropia(id);
        crud.eliminar(base.id, insumoPublicId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/insumos/importar")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ImportResultadoResponse importarInsumos(@PathParam("id") String id, InsumoImportForm form)
            throws IOException {
        BaseInsumos base = basePropia(id);
        byte[] contenido = java.nio.file.Files.readAllBytes(form.archivo.uploadedFile());
        return importacion.importarCsv(base.id, contenido);
    }

    private BaseInsumos basePropia(String id) {
        UUID publicId = UuidV7.parse(id);
        return basesPersonalesService
                .buscarPorPublicId(publicId, usuarioId())
                .orElseThrow(() -> ProblemaException.noEncontrado("Base personal no encontrada"));
    }

    private Long usuarioId() {
        String email = identity.getPrincipal().getName();
        return usuarioRepository
                .findByEmail(email)
                .map(u -> u.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario autenticado no encontrado"));
    }
}
