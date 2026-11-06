package ec.uce.propuestas.plantilla.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PlantillaApuSinPreciosTest {

    private static final Pattern CAMPOS_PROHIBIDOS =
            Pattern.compile("precioUnitario|precioOverride|tarifaJornal|costoTotal");

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        PlantillaApuAdminTestSupport.reset(ds, mailbox);
    }

    @Test
    void snapshot_admin_reutiliza_writer_price_free() throws Exception {
        String apuId = PlantillaApuAdminTestSupport.crearApuDeUsuario(mailbox, "owner-pricefree@ex.com", "P40-P");
        String adminToken = PlantillaApuAdminTestSupport.registrarSuperAdmin(ds, mailbox, "admin-pricefree@ex.com");
        String plantillaId = PlantillaApuAdminTestSupport.crearPlantilla(adminToken, apuId, "Price free");

        String snapshot = PlantillaApuAdminTestSupport.snapshotCrudo(ds, plantillaId);
        assertTrue(snapshot.contains("secciones"));
        assertTrue(snapshot.contains("esHerramientaMenor"));
        assertFalse(CAMPOS_PROHIBIDOS.matcher(snapshot).find(), snapshot);
    }
}
