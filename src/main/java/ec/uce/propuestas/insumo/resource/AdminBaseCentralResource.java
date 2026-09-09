package ec.uce.propuestas.insumo.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.insumo.dto.AdminBaseCentralCrearRequest;
import ec.uce.propuestas.insumo.dto.AdminBaseCentralEditarRequest;
import ec.uce.propuestas.insumo.dto.AdminBaseCentralResponse;
import ec.uce.propuestas.insumo.dto.ImportResultadoResponse;
import ec.uce.propuestas.insumo.dto.InsumoCrearRequest;
import ec.uce.propuestas.insumo.dto.InsumoEditarRequest;
import ec.uce.propuestas.insumo.dto.InsumoResponse;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import ec.uce.propuestas.insumo.service.BaseInsumosService;
import ec.uce.propuestas.insumo.service.ImportacionInsumoService;
import ec.uce.propuestas.insumo.service.InsumoCrudService;
import ec.uce.propuestas.insumo.service.importacion.CsvInsumoParser;
import ec.uce.propuestas.usuario.audit.EventoLogActividad;
import ec.uce.propuestas.usuario.audit.service.LogActividadService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plan 05 — Ciclo de vida administrativo de bases CENTRALES bajo la ruta
 * canónica {@code /admin/bases-centrales} (07-api-contract.md §9, P-39).
 *
 * <p>Contrato aplicado:
 * <ul>
 *   <li>{@code GET /admin/bases-centrales?incluirArchivadas=} — listado
 *       administrativo (excluye archivadas por defecto; con
 *       {@code incluirArchivadas=true} las muestra para mantenimiento).</li>
 *   <li>{@code POST /admin/bases-centrales} — crear (201).</li>
 *   <li>{@code PUT /admin/bases-centrales/{id}} — renombrar (200).</li>
 *   <li>{@code POST /admin/bases-centrales/{id}/archivar} — archivar (200;
 *       D-12, contrato canónico: <strong>POST</strong>, no PUT).</li>
 *   <li>{@code DELETE /admin/bases-centrales/{id}} — borrado físico solo
 *       después de archivar (409 conflicto si la base sigue activa).</li>
 *   <li>CRUD de insumos bajo {@code /admin/bases-centrales/{id}/insumos[/...]}
 *       (POST/PUT/DELETE) e importación CSV canónica
 *       {@code POST /admin/bases-centrales/{id}/insumos/import} (P-15/P-39).
 *       El parser, los DTOs de insumo y los servicios
 *       {@link InsumoCrudService} y {@link ImportacionInsumoService} son los
 *       mismos del catálogo de proyecto; aquí solo se acopla el contexto
 *       baseId/admin y la autorización SUPER_ADMIN.</li>
 * </ul>
 *
 * <p>Los ids externos son UUIDv7 (columna {@code base_insumos.public_id}); la
 * FK CASCADE de V001 ({@code insumo.base_id → base_insumos(id)}) garantiza
 * que los insumos se eliminan junto con la base, sin afectar las copias
 * PROYECTO ya materializadas.</p>
 *
 * <p>Plan 07 — los path params {@code {id}} y {@code {iid}} son {@code String}
 * y se validan como UUIDv7 en la frontera con {@link UuidV7#parse(String)}:
 * UUID mal formado o de versión distinta devuelve 400 {@code validacion}
 * antes de cualquier acceso al repositorio.</p>
 */
@Path("/admin/bases-centrales")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("SUPER_ADMIN")
public class AdminBaseCentralResource {

    private static final int PAGE_SIZE_MAX = 200;

    @Inject
    BaseInsumosService baseInsumosService;

    @Inject
    InsumoCrudService insumoCrudService;

    @Inject
    ImportacionInsumoService importacionInsumoService;

    @Inject
    InsumoRepository insumoRepository;

    @Inject
    LogActividadService logActividadService;

    // ========================================================================
    // Bases
    // ========================================================================

    /**
     * Lista bases CENTRALES. Con {@code incluirArchivadas=true} muestra
     * también las archivadas para mantenimiento; por defecto excluye las
     * archivadas para alinearse con la semántica de {@code /bases-centrales}.
     */
    @GET
    @Consumes(MediaType.WILDCARD)
    public Page<AdminBaseCentralResponse> listar(
            @QueryParam("incluirArchivadas") @DefaultValue("false") boolean incluirArchivadas,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size) {
        validarPaginacion(page, size);
        Page<BaseInsumos> resultado = baseInsumosService.listarCentralesAdminEntidades(incluirArchivadas, page, size);
        return Page.of(resultado.items().stream().map(this::aAdminResponse).toList(), resultado.total(), page, size);
    }

    @POST
    @Transactional
    public Response crear(@Valid AdminBaseCentralCrearRequest req) {
        BaseInsumos creada = baseInsumosService.crearCentral(req.nombre());
        emitir(creada, "crear", 0L);
        return Response.status(Response.Status.CREATED)
                .entity(aAdminResponse(creada))
                .build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public AdminBaseCentralResponse renombrar(@PathParam("id") String id, @Valid AdminBaseCentralEditarRequest req) {
        UUID publicId = UuidV7.parse(id);
        BaseInsumos base = baseInsumosService.renombrarCentral(publicId, req.nombre());
        emitir(base, "renombrar", insumoRepository.contarDeBase(base.id));
        return aAdminResponse(base);
    }

    /**
     * N04 §D-12 — archivado canónico por POST. Fija {@code archivada = true};
     * la base deja de aparecer en {@code GET /bases-centrales} (catálogo
     * normal) pero sigue visible en
     * {@code GET /admin/bases-centrales?incluirArchivadas=true}. La operación
     * nunca se bloquea por referencias históricas: las copias PROYECTO ya
     * materializadas persisten sin cambios (N04 §A9).
     */
    @POST
    @Path("/{id}/archivar")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public AdminBaseCentralResponse archivar(@PathParam("id") String id) {
        UUID publicId = UuidV7.parse(id);
        BaseInsumos base = baseInsumosService.archivarCentral(publicId);
        emitir(base, "archivar", insumoRepository.contarDeBase(base.id));
        return aAdminResponse(base);
    }

    /**
     * Borrado físico exclusivo de bases archivadas. Si la base está activa
     * devuelve 409 con {@code base-no-archivada}; cualquier fila inexistente o
     * no-CENTRAL devuelve 404. La FK CASCADE elimina los insumos asociados;
     * las copias PROYECTO no se tocan.
     */
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response eliminar(@PathParam("id") String id) {
        UUID publicId = UuidV7.parse(id);
        BaseInsumos base = baseInsumosService.obtenerCentralPorPublicId(publicId);
        long cantidad = insumoRepository.contarDeBase(base.id);
        baseInsumosService.eliminarCentralArchivada(publicId);
        emitir(base, "borrar", cantidad);
        return Response.noContent().build();
    }

    // ========================================================================
    // Insumos bajo la base central
    // ========================================================================

    @POST
    @Path("/{id}/insumos")
    @Transactional
    public Response crearInsumo(@PathParam("id") String id, @Valid InsumoCrearRequest req) {
        UUID publicId = UuidV7.parse(id);
        BaseInsumos base = baseInsumosService.obtenerCentralPorPublicId(publicId);
        InsumoResponse body = insumoCrudService.crear(base.id, req);
        emitir(base, "crearInsumo", insumoRepository.contarDeBase(base.id));
        return Response.status(Response.Status.CREATED).entity(body).build();
    }

    @PUT
    @Path("/{id}/insumos/{iid}")
    @Transactional
    public InsumoResponse editarInsumo(
            @PathParam("id") String id, @PathParam("iid") String insumoId, @Valid InsumoEditarRequest req) {
        UUID publicId = UuidV7.parse(id);
        UUID insumoPublicId = UuidV7.parse(insumoId);
        BaseInsumos base = baseInsumosService.obtenerCentralPorPublicId(publicId);
        InsumoResponse response = insumoCrudService.actualizar(base.id, insumoPublicId, req);
        emitir(base, "editarInsumo", insumoRepository.contarDeBase(base.id));
        return response;
    }

    @DELETE
    @Path("/{id}/insumos/{iid}")
    @Transactional
    public Response eliminarInsumo(@PathParam("id") String id, @PathParam("iid") String insumoId) {
        UUID publicId = UuidV7.parse(id);
        UUID insumoPublicId = UuidV7.parse(insumoId);
        BaseInsumos base = baseInsumosService.obtenerCentralPorPublicId(publicId);
        long cantidad = insumoRepository.contarDeBase(base.id);
        insumoCrudService.eliminar(base.id, insumoPublicId);
        emitir(base, "borrarInsumo", cantidad - 1L);
        return Response.noContent().build();
    }

    /**
     * Importación CSV canónica (P-15/P-39). Mismo parser y servicio que el
     * flujo de proyecto; aquí se acopla el {@code baseId} de la CENTRAL.
     * {@code ?soloValidar=true} ejecuta solo el parser para reportar errores
     * de forma antes de invocar el upsert.
     */
    @POST
    @Path("/{id}/insumos/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional(rollbackOn = Exception.class)
    public ImportResultadoResponse importarInsumos(
            @PathParam("id") String id,
            @QueryParam("soloValidar") @DefaultValue("false") boolean soloValidar,
            InsumoImportForm form)
            throws IOException {
        UUID publicId = UuidV7.parse(id);
        BaseInsumos base = baseInsumosService.obtenerCentralPorPublicId(publicId);
        if (form == null || form.archivo == null) {
            throw ProblemaException.validacion("Archivo CSV requerido");
        }
        byte[] contenido = Files.readAllBytes(form.archivo.uploadedFile());
        if (soloValidar) {
            CsvInsumoParser.parse(contenido, TipoInsumo.MATERIAL);
            return new ImportResultadoResponse(0, 0, List.of());
        }
        ImportResultadoResponse response = importacionInsumoService.importarCsv(base.id, contenido);
        emitir(base, "importar", insumoRepository.contarDeBase(base.id));
        return response;
    }

    private void emitir(BaseInsumos base, String operacion, long cantidadInsumos) {
        logActividadService.emitir(
                null,
                EventoLogActividad.ADMIN_BASE_EDITADA,
                "base_insumos",
                base.publicId,
                Map.of("operacion", operacion, "cantidadInsumos", Math.toIntExact(cantidadInsumos)));
    }

    private static void validarPaginacion(int page, int size) {
        if (page < 0) {
            throw ProblemaException.validacion("pagina-invalida");
        }
        if (size < 1 || size > PAGE_SIZE_MAX) {
            throw new ProblemaException(
                    400, "tamano-pagina-invalido", "El parámetro size debe estar entre 1 y " + PAGE_SIZE_MAX);
        }
    }

    // ========================================================================
    // Mapping helper (entidad persistida → DTO UUIDv7)
    // ========================================================================

    private AdminBaseCentralResponse aAdminResponse(BaseInsumos b) {
        long total = b.id == null ? 0L : insumoRepository.contarDeBase(b.id);
        return new AdminBaseCentralResponse(b.publicId, b.nombre, b.tipo.name(), b.archivada, total);
    }
}
