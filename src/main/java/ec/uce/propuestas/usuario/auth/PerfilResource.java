package ec.uce.propuestas.usuario.auth;

import ec.uce.propuestas.usuario.auth.dto.PasswordCambiarRequest;
import ec.uce.propuestas.usuario.auth.dto.PerfilActualizarRequest;
import ec.uce.propuestas.usuario.auth.dto.PerfilResponse;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/perfil")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class PerfilResource {

    @Inject SecurityIdentity identity;
    @Inject AuthService svc;

    @GET
    public PerfilResponse leer() {
        String email = identity.getPrincipal().getName();
        return svc.leerPerfil(email);
    }

    @PUT
    public PerfilResponse actualizar(@Valid PerfilActualizarRequest r) {
        String email = identity.getPrincipal().getName();
        return svc.actualizarPerfil(email, r);
    }

    @PUT
    @Path("/password")
    public Response cambiarPassword(@Valid PasswordCambiarRequest r) {
        String email = identity.getPrincipal().getName();
        svc.cambiarPassword(email, r);
        return Response.noContent().build();
    }
}
