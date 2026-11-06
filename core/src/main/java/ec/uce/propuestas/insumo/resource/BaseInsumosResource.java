package ec.uce.propuestas.insumo.resource;

import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.insumo.dto.BaseInsumosResponse;
import ec.uce.propuestas.insumo.dto.InsumoResponse;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.insumo.service.BaseInsumosService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * Bases de insumos centrales (07-api-contract.md §4). Ruta {@code /bases-centrales}.
 * El resto del flujo insumo vive bajo {@code /proyectos/{proyectoId}/insumos}.
 */
@Path("/bases-centrales")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class BaseInsumosResource {

    @Inject
    BaseInsumosService baseInsumosService;

    /** Lista de bases centrales activas (P-13). */
    @GET
    @Consumes(MediaType.WILDCARD)
    public List<BaseInsumosResponse> listar() {
        return baseInsumosService.listarCentrales();
    }

    /**
     * Plan 044 — lectura de los insumos de una base central para cualquier
     * usuario autenticado (bugs-pendientes §5). Solo lectura: las mutaciones
     * siguen en {@code /admin/bases-centrales} (SUPER_ADMIN). Mismos filtros y
     * paginación que {@code GET /proyectos/{id}/insumos}. Una base que no sea
     * CENTRAL devuelve 404, sin filtrar existencia (RNF-05).
     */
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
        BaseInsumos base = baseInsumosService.obtenerCentralPorPublicId(UuidV7.parse(id));
        return baseInsumosService.listarInsumosBase(base.id, tipo, q, desactualizados, page, size);
    }
}
