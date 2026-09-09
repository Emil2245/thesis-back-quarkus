package ec.uce.propuestas.plantilla.admin;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.plantilla.admin.dto.PlantillaApuAdminEditarRequest;
import ec.uce.propuestas.plantilla.admin.dto.PlantillaApuAdminResponse;
import ec.uce.propuestas.plantilla.admin.dto.PlantillaSistemaCrearRequest;
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

/** Recurso SUPER_ADMIN para gestionar plantillas APU de sistema (P-40). */
@Path("/admin/plantillas-apu")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("SUPER_ADMIN")
public class PlantillaApuAdminResource {

    private static final int PAGE_SIZE_MAX = 200;

    @Inject
    PlantillaApuAdminService service;

    @GET
    @Consumes(MediaType.WILDCARD)
    public Page<PlantillaApuAdminResponse> listar(
            @QueryParam("q") String q,
            @QueryParam("tipo") @DefaultValue("SISTEMA") String tipo,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size) {
        validarPaginacion(page, size);
        if (!"SISTEMA".equalsIgnoreCase(tipo)) {
            throw ProblemaException.validacion("tipo debe ser SISTEMA");
        }
        var items = service.listar(q, page, size).stream()
                .map(PlantillaApuAdminResponse::from)
                .toList();
        return Page.of(items, service.contar(q), page, size);
    }

    @POST
    public Response crear(@Valid PlantillaSistemaCrearRequest request) {
        UUID desdeApuId = UuidV7.parse(request.desdeApuId().toString());
        var plantilla = service.crear(
                new PlantillaSistemaCrearRequest(desdeApuId, request.nombre(), request.descripcionRubro()));
        return Response.status(Response.Status.CREATED)
                .entity(PlantillaApuAdminResponse.from(plantilla))
                .build();
    }

    @PUT
    @Path("/{id}")
    public PlantillaApuAdminResponse editar(@PathParam("id") String id, @Valid PlantillaApuAdminEditarRequest request) {
        UUID publicId = UuidV7.parse(id);
        var plantilla = service.editar(
                publicId,
                request.nombrePresente(),
                request.nombreOrNull(),
                request.descripcionPresente(),
                request.descripcionOrNull());
        return PlantillaApuAdminResponse.from(plantilla);
    }

    @DELETE
    @Path("/{id}")
    @Consumes(MediaType.WILDCARD)
    public Response eliminar(@PathParam("id") String id) {
        service.eliminar(UuidV7.parse(id));
        return Response.noContent().build();
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
}
