package ec.uce.propuestas.presupuesto.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.presupuesto.dto.ComparacionVersionesResponse;
import ec.uce.propuestas.presupuesto.service.VersionadoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

/**
 * Plan 024 (P-31) — endpoint REST de comparación de dos versiones del
 * presupuesto. Ruta
 * {@code GET /presupuestos/{presupuestoId}/comparar?con=<UUIDv7>}.
 *
 * <p>Devuelve {@link ComparacionVersionesResponse}: totales lado a lado y el
 * desglose por capítulo raíz para cada versión (sólo el primer nivel del
 * árbol, ordenado por {@code item} ascendente). El orden de las dos
 * versiones en la respuesta es estable: la del path primero, la del
 * {@code con} después.</p>
 *
 * <p>Plan 07 / WU-03 — los path/query params son UUIDv7; el parse ocurre en
 * este resource con {@link UuidV7#parse(String)} para que cualquier entrada
 * malformada o de versión incorrecta rechace con 400 {@code validacion}
 * antes de tocar la BD. Roles: {@code USUARIO} y {@code SUPER_ADMIN}.</p>
 *
 * <p>Reglas de error (07-api-contract.md §6 + P-31):
 * <ul>
 *   <li>400 {@code validacion}: UUIDv7 malformado o no-v7 en path o query;
 *       comparación contra el mismo id (self); comparación contra una
 *       versión de otro proyecto.</li>
 *   <li>404 {@code no-encontrado}: presupuesto del path o del query ajeno o
 *       inexistente (nunca 403 — RNF-05).</li>
 * </ul>
 */
@Path("/presupuestos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class ComparacionResource {

    @Inject
    VersionadoService versionadoService;

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

    /**
     * Compara el presupuesto del path con el indicado por {@code con}.
     * Devuelve ambas cabeceras con su total persistido y el desglose por
     * capítulo raíz. Self comparison y cross-project comparison devuelven
     * 400 {@code validacion}.
     */
    @GET
    @Path("/{presupuestoId}/comparar")
    @Consumes(MediaType.WILDCARD)
    public ComparacionVersionesResponse comparar(
            @PathParam("presupuestoId") String presupuestoId, @QueryParam("con") String con) {
        UUID presupuestoPublicId = UuidV7.parse(presupuestoId);
        UUID conPublicId = UuidV7.parse(con);
        return versionadoService.comparar(presupuestoPublicId, conPublicId, usuarioId());
    }
}
