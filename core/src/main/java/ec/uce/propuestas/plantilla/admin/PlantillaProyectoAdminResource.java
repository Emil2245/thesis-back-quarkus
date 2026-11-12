package ec.uce.propuestas.plantilla.admin;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.plantilla.admin.dto.PlantillaProyectoAdminEditarRequest;
import ec.uce.propuestas.plantilla.admin.dto.PlantillaProyectoAdminResponse;
import ec.uce.propuestas.plantilla.admin.dto.PlantillaProyectoSistemaCrearRequest;
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
import java.util.UUID;

/**
 * Plan 044 — recurso SUPER_ADMIN para plantillas de proyecto SISTEMA. Mismo
 * contrato que {@link PlantillaApuAdminResource} con {@code desdeProyectoId}
 * en lugar de {@code desdeApuId} y {@code descripcion} en lugar de
 * {@code descripcionRubro}.
 */
@Path("/admin/plantillas-proyecto")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("SUPER_ADMIN")
public class PlantillaProyectoAdminResource {

    private static final int PAGE_SIZE_MAX = 200;

    @Inject
    PlantillaProyectoAdminService service;

    @GET
    @Consumes(MediaType.WILDCARD)
    public Page<PlantillaProyectoAdminResponse> listar(
            @QueryParam("q") String q,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size) {
        if (page < 0) {
            throw ProblemaException.validacion("pagina-invalida");
        }
        if (size < 1 || size > PAGE_SIZE_MAX) {
            throw new ProblemaException(
                    400, "tamano-pagina-invalido", "El parámetro size debe estar entre 1 y " + PAGE_SIZE_MAX);
        }
        var items = service.listar(q, page, size).stream()
                .map(PlantillaProyectoAdminResponse::from)
                .toList();
        return Page.of(items, service.contar(q), page, size);
    }

    @POST
    public Response crear(@Valid PlantillaProyectoSistemaCrearRequest request) {
        UUID desdeProyectoId = UuidV7.parse(request.desdeProyectoId().toString());
        var plantilla = service.crear(
                new PlantillaProyectoSistemaCrearRequest(desdeProyectoId, request.nombre(), request.descripcion()));
        return Response.status(Response.Status.CREATED)
                .entity(PlantillaProyectoAdminResponse.from(plantilla))
                .build();
    }

    @PUT
    @Path("/{id}")
    public PlantillaProyectoAdminResponse editar(
            @PathParam("id") String id, @Valid PlantillaProyectoAdminEditarRequest request) {
        UUID publicId = UuidV7.parse(id);
        var plantilla = service.editar(
                publicId,
                request.nombrePresente(),
                request.nombreOrNull(),
                request.descripcionPresente(),
                request.descripcionOrNull());
        return PlantillaProyectoAdminResponse.from(plantilla);
    }

    @DELETE
    @Path("/{id}")
    @Consumes(MediaType.WILDCARD)
    public Response eliminar(@PathParam("id") String id) {
        service.eliminar(UuidV7.parse(id));
        return Response.noContent().build();
    }
}
