package ec.uce.propuestas.common.config;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Plan 014 — Endpoint público {@code GET /api/v1/config/display}
 * (prefijo {@code /api/v1} lo aporta {@code common/RestApplication}).
 *
 * <p>Devuelve los valores efectivos de {@link DisplayConfig} traducidos al
 * contrato JSON público {@link DisplayConfigResponse}. Es la fuente de verdad
 * para que el frontend sepa cuántos decimales usar al renderizar dinero y
 * porcentajes.
 */
@Path("/config/display")
@Produces(MediaType.APPLICATION_JSON)
public class DisplayConfigResource {

    @Inject
    DisplayConfig config;

    @GET
    @PermitAll
    public DisplayConfigResponse get() {
        return new DisplayConfigResponse(config.precision(), config.precisionPorcentaje());
    }
}
