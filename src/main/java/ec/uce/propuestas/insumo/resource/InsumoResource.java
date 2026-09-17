package ec.uce.propuestas.insumo.resource;

import ec.uce.propuestas.apu.service.ApuCalculoService;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.insumo.dto.*;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import ec.uce.propuestas.insumo.service.*;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.service.ProyectoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.UUID;

/**
 * Insumos del proyecto (07-api-contract.md §4). Ruta {@code /proyectos/{proyectoId}/insumos}.
 *
 * <p>Plan 07 — los identificadores públicos son UUIDv7. El resource valida los path params antes
 * de resolver el owner y mantiene los BIGINT debajo de la frontera REST.
 */
@Path("/proyectos/{proyectoId}/insumos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class InsumoResource {

    @Inject
    BaseInsumosService baseInsumosService;

    @Inject
    InsumoCrudService crud;

    @Inject
    InsumoCatalogoService catalogo;

    @Inject
    ImportacionInsumoService importacion;

    @Inject
    CopiaBaseService copia;

    @Inject
    InsumoRepository insumoRepository;

    @Inject
    ProyectoService proyectoService;

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    SecurityIdentity identity;

    private Long usuarioId() {
        String email = identity.getPrincipal().getName();
        return usuarioRepository
                .findByEmail(email)
                .map(u -> u.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario autenticado no encontrado"));
    }

    private record ProyectoYBase(Proyecto proyecto, BaseInsumos base) {}

    private ProyectoYBase resolverProyectoYBase(String proyectoId) {
        UUID publicId = UuidV7.parse(proyectoId);
        Proyecto proyecto = proyectoService.validarPropietario(usuarioId(), publicId);
        BaseInsumos base = baseInsumosService.asegurarBaseProyecto(proyecto.id);
        return new ProyectoYBase(proyecto, base);
    }

    @GET
    @Consumes(MediaType.WILDCARD)
    public Response listInsumos(
            @PathParam("proyectoId") String proyectoId,
            @QueryParam("tipo") TipoInsumo tipo,
            @QueryParam("q") String q,
            @QueryParam("desactualizados") boolean desactualizados,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size) {
        ProyectoYBase contexto = resolverProyectoYBase(proyectoId);
        return Response.ok(
                        baseInsumosService.listarInsumosBase(contexto.base().id, tipo, q, desactualizados, page, size))
                .build();
    }

    @GET
    @Path("/selector")
    @Consumes(MediaType.WILDCARD)
    public Page<InsumoBusquedaResponse> selector(
            @PathParam("proyectoId") String proyectoId,
            @QueryParam("q") String q,
            @QueryParam("soloCentrales") boolean soloCentrales,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size) {
        ProyectoYBase contexto = resolverProyectoYBase(proyectoId);
        return catalogo.buscar(contexto.proyecto().id, q, soloCentrales, page, size);
    }

    @POST
    public Response crear(@PathParam("proyectoId") String proyectoId, @Valid InsumoCrearRequest req) {
        ProyectoYBase contexto = resolverProyectoYBase(proyectoId);
        return Response.status(Response.Status.CREATED)
                .entity(crud.crear(contexto.base().id, req))
                .build();
    }

    @PUT
    @Path("/{insumoId}")
    public InsumoResponse editar(
            @PathParam("proyectoId") String proyectoId,
            @PathParam("insumoId") String insumoId,
            @Valid InsumoEditarRequest req) {
        UUID insumoPublicId = UuidV7.parse(insumoId);
        ProyectoYBase contexto = resolverProyectoYBase(proyectoId);
        return crud.actualizar(contexto.base().id, insumoPublicId, req);
    }

    @DELETE
    @Path("/{insumoId}")
    public Response eliminar(@PathParam("proyectoId") String proyectoId, @PathParam("insumoId") String insumoId) {
        UUID insumoPublicId = UuidV7.parse(insumoId);
        ProyectoYBase contexto = resolverProyectoYBase(proyectoId);
        crud.eliminar(contexto.base().id, insumoPublicId);
        return Response.noContent().build();
    }

    @POST
    @Path("/importar")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ImportResultadoResponse importar(@PathParam("proyectoId") String proyectoId, InsumoImportForm form)
            throws IOException {
        ProyectoYBase contexto = resolverProyectoYBase(proyectoId);
        byte[] contenido = java.nio.file.Files.readAllBytes(form.archivo.uploadedFile());
        return importacion.importarCsv(contexto.base().id, contenido);
    }

    @POST
    @Path("/copiar")
    public Response copiar(@PathParam("proyectoId") String proyectoId, @Valid CopiarBaseRequest request) {
        UUID destinoProyecto = UuidV7.parse(proyectoId);
        CopiarBaseRequest body = new CopiarBaseRequest(request.fuenteTipo(), request.baseId(), destinoProyecto);
        return Response.ok(copia.copiar(body, usuarioId())).build();
    }

    /**
     * P-18 — dónde se usa un insumo: un elemento por fila de APU que lo
     * referencia, con su bloque SERCOP M/N/O/P y si el precio de esa fila es
     * override manual o heredado del insumo.
     *
     * <p>Plan 032 — la consulta se acota al proyecto del path: {@code apu_detalle}
     * no lleva proyecto encima (RNF-05). Un insumo sin usos devuelve {@code []}
     * con 200, no 404.</p>
     *
     * <p>El mismo insumo en dos filas del mismo APU (p. ej. en dos secciones)
     * produce <b>dos</b> entradas: cada una es un uso con su bloque y su
     * override. No se deduplica por {@code apuId}. Las filas de herramienta
     * menor no tienen {@code insumoId}, así que la consulta ya las deja
     * fuera.</p>
     */
    @GET
    @Path("/{insumoId}/usos")
    @Consumes(MediaType.WILDCARD)
    public java.util.List<InsumoUsoResponse> usos(
            @PathParam("proyectoId") String proyectoId, @PathParam("insumoId") String insumoId) {
        UUID insumoPublicId = UuidV7.parse(insumoId);
        ProyectoYBase contexto = resolverProyectoYBase(proyectoId);
        Insumo insumo = insumoRepository
                .findByPublicIdAndBase(insumoPublicId, contexto.base().id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Insumo no encontrado en esta base"));

        return insumoRepository.listarUsosEnApuDetalle(insumo.id, contexto.proyecto().id).stream()
                .map(InsumoResource::aUso)
                .toList();
    }

    /**
     * Plan 032 — {@code override = true} cuando la fila lleva precio manual. La
     * semántica {@code null-means-inherit} la resuelve
     * {@link ApuCalculoService#overrideDeDetalle}, que es {@code public static}
     * justamente para esto; re-implementar su {@code switch} aquí sería una
     * segunda tabla capaz de divergir.
     */
    private static InsumoUsoResponse aUso(InsumoRepository.UsoEnApu uso) {
        boolean override = ApuCalculoService.overrideDeDetalle(uso.detalle(), uso.tipo()) != null;
        return new InsumoUsoResponse(
                uso.apuPublicId(), uso.codigo(), uso.descripcion(), uso.tipo().bloque(), override);
    }
}
