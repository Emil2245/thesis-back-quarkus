package ec.uce.propuestas.insumo.resource;

import ec.uce.propuestas.insumo.dto.BaseInsumosResponse;
import ec.uce.propuestas.insumo.service.BaseInsumosService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
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
}
