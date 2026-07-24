package ec.uce.propuestas.usuario.auth;

import ec.uce.propuestas.usuario.auth.dto.*;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Duration;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject AuthService svc;

    @ConfigProperty(name = "app.auth.reenvio-cooldown")
    Duration reenvioTtl;

    // POST /auth/registro
    @POST
    @Path("/registro")
    @PermitAll
    public Response registro(@Valid RegistroRequest r) {
        return Response.status(201).entity(svc.registrar(r)).build();
    }

    // POST /auth/verificar-email
    @POST
    @Path("/verificar-email")
    @PermitAll
    public Response verificarEmail(@Valid VerificarEmailRequest r) {
        svc.verificarEmail(r.token());
        return Response.noContent().build();
    }

    // POST /auth/reenviar-verificacion
    @POST
    @Path("/reenviar-verificacion")
    @PermitAll
    public Response reenviarVerificacion(@Valid ReenviarVerificacionRequest r) {
        svc.reenviarVerificacion(r.email(), reenvioTtl);
        return Response.accepted().build();
    }

    // POST /auth/login
    @POST
    @Path("/login")
    @PermitAll
    public Response login(@Valid LoginRequest r) {
        return Response.ok(svc.login(r)).build();
    }

    // POST /auth/refresh
    @POST
    @Path("/refresh")
    @PermitAll
    public Response refresh(@Valid RefreshRequest r) {
        return Response.ok(svc.refresh(r.refreshToken())).build();
    }

    // POST /auth/logout
    @POST
    @Path("/logout")
    @jakarta.annotation.security.RolesAllowed({"USUARIO", "SUPER_ADMIN"})
    public Response logout(@Valid RefreshRequest r) {
        svc.logout(r.refreshToken());
        return Response.noContent().build();
    }

    // POST /auth/recuperar
    @POST
    @Path("/recuperar")
    @PermitAll
    public Response recuperar(@Valid RecuperarPasswordRequest r) {
        svc.iniciarRecuperacion(r.email());
        return Response.accepted().build();  // always 202 — anti-enumeration
    }

    // POST /auth/restablecer
    @POST
    @Path("/restablecer")
    @PermitAll
    public Response restablecer(@Valid RestablecerPasswordRequest r) {
        svc.restablecerPassword(r);
        return Response.noContent().build();
    }

    // POST /auth/aceptar-invitacion
    @POST
    @Path("/aceptar-invitacion")
    @PermitAll
    public Response aceptarInvitacion(@Valid AceptarInvitacionRequest r) {
        svc.aceptarInvitacion(r);
        return Response.noContent().build();
    }
}
