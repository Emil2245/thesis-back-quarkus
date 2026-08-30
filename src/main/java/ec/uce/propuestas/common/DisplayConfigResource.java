package ec.uce.propuestas.common;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/config/display")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class DisplayConfigResource {

    @ConfigProperty(name = "app.display.precision-dinero", defaultValue = "2")
    int precisionDinero;

    @ConfigProperty(name = "app.display.precision-porcentaje", defaultValue = "4")
    int precisionPorcentaje;

    @GET
    public DisplayConfigResponse get() {
        return new DisplayConfigResponse(precisionDinero, precisionPorcentaje);
    }

    public record DisplayConfigResponse(int precisionDinero, int precisionPorcentaje) {}
}
