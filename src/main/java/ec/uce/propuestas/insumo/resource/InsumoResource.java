package ec.uce.propuestas.insumo.resource;

import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.insumo.dto.CopiarBaseRequest;
import ec.uce.propuestas.insumo.dto.ImportResultadoResponse;
import ec.uce.propuestas.insumo.dto.InsumoBusquedaResponse;
import ec.uce.propuestas.insumo.dto.InsumoCrearRequest;
import ec.uce.propuestas.insumo.dto.InsumoEditarRequest;
import ec.uce.propuestas.insumo.dto.InsumoResponse;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.insumo.service.BaseInsumosService;
import ec.uce.propuestas.insumo.service.CopiaBaseService;
import ec.uce.propuestas.insumo.service.ImportacionInsumoService;
import ec.uce.propuestas.insumo.service.InsumoCatalogoService;
import ec.uce.propuestas.insumo.service.InsumoCrudService;
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

/**
 * Insumos del proyecto (07-api-contract.md §4). Ruta {@code /proyectos/{proyectoId}/insumos}.
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

    /** Lista insumos de la base PROYECTO (P-13). */
    @GET
    @Consumes(MediaType.WILDCARD)
    public Response listInsumos(
            @PathParam("proyectoId") Long proyectoId,
            @QueryParam("tipo") TipoInsumo tipo,
            @QueryParam("q") String q,
            @QueryParam("desactualizados") boolean desactualizados,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size) {
        var base = baseInsumosService.asegurarBaseProyecto(proyectoId);
        return Response.ok(baseInsumosService.listarInsumosBase(base.id, tipo, q, desactualizados, page, size)).build();
    }

    /** Selector multi-fuente (P-16/P-21): central + proyecto. */
    @GET
    @Path("/selector")
    @Consumes(MediaType.WILDCARD)
    public Page<InsumoBusquedaResponse> selector(
            @PathParam("proyectoId") Long proyectoId,
            @QueryParam("q") String q,
            @QueryParam("soloCentrales") boolean soloCentrales,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size) {
        return catalogo.buscar(proyectoId, q, soloCentrales, page, size);
    }

    /** Crear un insumo en la base PROYECTO. */
    @POST
    public Response crear(@PathParam("proyectoId") Long proyectoId, @Valid InsumoCrearRequest req) {
        var base = baseInsumosService.asegurarBaseProyecto(proyectoId);
        return Response.status(Response.Status.CREATED).entity(crud.crear(base.id, req)).build();
    }

    @PUT
    @Path("/{insumoId}")
    public InsumoResponse editar(@PathParam("proyectoId") Long proyectoId,
                                 @PathParam("insumoId") Long insumoId,
                                 @Valid InsumoEditarRequest req) {
        var base = baseInsumosService.asegurarBaseProyecto(proyectoId);
        return crud.actualizar(base.id, insumoId, req);
    }

    @DELETE
    @Path("/{insumoId}")
    public Response eliminar(@PathParam("proyectoId") Long proyectoId,
                             @PathParam("insumoId") Long insumoId) {
        var base = baseInsumosService.asegurarBaseProyecto(proyectoId);
        crud.eliminar(base.id, insumoId);
        return Response.noContent().build();
    }

    /** Importación CSV (P-15). Multipart: { archivo, tipo }. */
    @POST
    @Path("/importar")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ImportResultadoResponse importar(@PathParam("proyectoId") Long proyectoId,
                                            InsumoImportForm form) throws IOException {
        var base = baseInsumosService.asegurarBaseProyecto(proyectoId);
        byte[] contenido = java.nio.file.Files.readAllBytes(form.archivo.uploadedFile());
        return importacion.importarCsv(base.id, contenido);
    }

    /** Copiar una base hacia la base PROYECTO (P-14). */
    @POST
    @Path("/copiar")
    public Response copiar(@PathParam("proyectoId") Long proyectoId, @Valid CopiarBaseRequest request) {
        var body = new CopiarBaseRequest(request.fuenteTipo(), request.baseId(), proyectoId);
        return Response.ok(copia.copiar(body)).build();
    }
}