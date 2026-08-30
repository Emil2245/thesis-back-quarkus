package ec.uce.propuestas.admin.resource;

import ec.uce.propuestas.admin.dto.BaseCentralAdminResponse;
import ec.uce.propuestas.admin.service.LogService;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.insumo.dto.BaseInsumosCrearRequest;
import ec.uce.propuestas.insumo.dto.InsumoCrearRequest;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.TipoBase;
import ec.uce.propuestas.insumo.repository.BaseInsumosRepository;
import ec.uce.propuestas.insumo.service.ImportacionInsumoService;
import ec.uce.propuestas.insumo.service.InsumoCrudService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/admin/bases-centrales")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("SUPER_ADMIN")
public class AdminBasesCentralesResource {

    @Inject
    BaseInsumosRepository baseInsumosRepository;

    @Inject
    InsumoCrudService insumoCrud;

    @Inject
    ImportacionInsumoService importacion;

    @Inject
    LogService logService;

    @Inject
    SecurityIdentity identity;

    @Inject
    UsuarioRepository usuarioRepository;

    private Long adminId() {
        String email = identity.getPrincipal().getName();
        return usuarioRepository
                .findByEmail(email)
                .map(u -> u.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Admin no encontrado"));
    }

    @GET
    @Consumes(MediaType.WILDCARD)
    public List<BaseCentralAdminResponse> listar(
            @QueryParam("incluirArchivadas") @DefaultValue("false") boolean inclArch) {
        List<BaseInsumos> bases;
        if (inclArch) {
            bases = baseInsumosRepository.find("tipo", TipoBase.CENTRAL).list();
        } else {
            bases = baseInsumosRepository
                    .find("tipo = ?1 and archivada = false", TipoBase.CENTRAL)
                    .list();
        }
        return bases.stream().map(b -> toResp(b)).toList();
    }

    @POST
    @Transactional
    public Response crear(@Valid BaseInsumosCrearRequest req) {
        BaseInsumos b = new BaseInsumos();
        b.nombre = req.nombre();
        b.tipo = TipoBase.CENTRAL;
        baseInsumosRepository.persist(b);
        logService.registrar(adminId(), "base.creada", "base_insumos", b.id, Map.of("nombre", b.nombre));
        return Response.status(Response.Status.CREATED).entity(toResp(b)).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public BaseCentralAdminResponse renombrar(@PathParam("id") Long id, @Valid BaseInsumosCrearRequest req) {
        BaseInsumos b = findCentral(id);
        b.nombre = req.nombre();
        baseInsumosRepository.persist(b);
        return toResp(b);
    }

    @POST
    @Path("/{id}/archivar")
    @Transactional
    @Consumes(MediaType.WILDCARD)
    public BaseCentralAdminResponse archivar(@PathParam("id") Long id) {
        BaseInsumos b = findCentral(id);
        b.archivada = !b.archivada;
        baseInsumosRepository.persist(b);
        logService.registrar(
                adminId(),
                b.archivada ? "base.archivada" : "base.desarchivada",
                "base_insumos",
                b.id,
                Map.of("nombre", b.nombre));
        return toResp(b);
    }

    @POST
    @Path("/{id}/insumos")
    @Transactional
    public Response agregarInsumo(@PathParam("id") Long id, @Valid InsumoCrearRequest req) {
        findCentral(id);
        return Response.status(Response.Status.CREATED)
                .entity(insumoCrud.crear(id, req))
                .build();
    }

    @DELETE
    @Path("/{id}/insumos/{insumoId}")
    @Transactional
    @Consumes(MediaType.WILDCARD)
    public Response eliminarInsumo(@PathParam("id") Long id, @PathParam("insumoId") Long insumoId) {
        findCentral(id);
        insumoCrud.eliminar(id, insumoId);
        return Response.noContent().build();
    }

    private BaseCentralAdminResponse toResp(BaseInsumos b) {
        return new BaseCentralAdminResponse(b.id, b.nombre, b.archivada, b.createdAt);
    }

    private BaseInsumos findCentral(Long id) {
        return baseInsumosRepository
                .findByIdOptional(id)
                .filter(b -> b.tipo == TipoBase.CENTRAL)
                .orElseThrow(() -> ProblemaException.noEncontrado("Base central no encontrada"));
    }
}
