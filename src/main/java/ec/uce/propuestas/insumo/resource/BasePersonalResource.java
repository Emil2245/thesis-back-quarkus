package ec.uce.propuestas.insumo.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.insumo.dto.BaseInsumosResponse;
import ec.uce.propuestas.insumo.dto.BasePersonalCrearRequest;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.TipoBase;
import ec.uce.propuestas.insumo.mapper.BaseInsumosMapper;
import ec.uce.propuestas.insumo.repository.BaseInsumosRepository;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
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

@Path("/bases-personales")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class BasePersonalResource {

    @Inject
    BaseInsumosRepository baseInsumosRepository;

    @Inject
    InsumoRepository insumoRepository;

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

    @GET
    @Consumes(MediaType.WILDCARD)
    public List<BaseInsumosResponse> listar() {
        return baseInsumosRepository.listarPersonales(usuarioId()).stream()
                .map(b -> BaseInsumosMapper.toResponse(b, insumoRepository.contarDeBase(b.id)))
                .toList();
    }

    @POST
    @Transactional
    public Response crear(@Valid BasePersonalCrearRequest req) {
        Long uid = usuarioId();
        BaseInsumos base = new BaseInsumos();
        base.nombre = req.nombre();
        base.tipo = TipoBase.PERSONAL;
        base.usuarioId = uid;
        baseInsumosRepository.persist(base);
        return Response.status(Response.Status.CREATED)
                .entity(BaseInsumosMapper.toResponse(base, 0))
                .build();
    }
}
