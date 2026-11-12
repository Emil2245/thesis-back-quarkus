package ec.uce.propuestas.insumo.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Plan 022 — la importación CSV carga los cuatro tipos y actualiza precios existentes. */
@QuarkusTest
class ImportacionInsumoCsvIT {

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    private static final String CSV_INICIAL = "codigo,tipo,descripcion,unidad,precio\n"
            + "M-1,MATERIAL,Cemento,kg,1.50\n"
            + "MO-1,MANO_OBRA,Peón,,3.20\n"
            + "EQ-1,EQUIPO,Mezcladora,h,25.00\n"
            + "TR-1,TRANSPORTE,Volqueta,m3,0.80\n";

    @Test
    void importa_cuatro_tipos_actualiza_precios_y_detecta_sin_cambios() throws Exception {
        String token = registrarSuperAdmin("admin-022@ex.com");
        String base = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "Base 022"))
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        importar(token, base, CSV_INICIAL)
                .body("creados", equalTo(4))
                .body("actualizados", equalTo(0))
                .body("sinCambios", equalTo(0))
                .body("errores", hasSize(0));
        assertEquals("MATERIAL", campo("M-1", "tipo"));
        assertEquals("MANO_OBRA", campo("MO-1", "tipo"));
        assertEquals("EQUIPO", campo("EQ-1", "tipo"));
        assertEquals("TRANSPORTE", campo("TR-1", "tipo"));
        assertEquals("h", campo("MO-1", "unidad"));

        // Re-importación sin cambios.
        importar(token, base, CSV_INICIAL)
                .body("creados", equalTo(0))
                .body("actualizados", equalTo(0))
                .body("sinCambios", equalTo(4));

        // Cambia dos precios; los demás quedan igual; un tipo en conflicto es error de fila.
        String csv2 = "codigo,tipo,descripcion,unidad,precio\n"
                + "M-1,MATERIAL,Cemento,kg,1.75\n"
                + "MO-1,MANO_OBRA,Peón,,3.20\n"
                + "EQ-1,EQUIPO,Mezcladora,h,30.00\n"
                + "TR-1,MATERIAL,Volqueta,m3,0.80\n";
        importar(token, base, csv2)
                .body("creados", equalTo(0))
                .body("actualizados", equalTo(2))
                .body("sinCambios", equalTo(1))
                .body("errores", hasSize(1))
                .body("errores[0].fila", equalTo(5))
                .body("errores[0].mensaje", equalTo("El código existe con otro tipo"));
        assertEquals("1.750000", campo("M-1", "precio_unitario"));
        assertEquals("30.000000", campo("EQ-1", "precio_unitario"));
        assertEquals("0.800000", campo("TR-1", "precio_unitario"));
        assertEquals("TRANSPORTE", campo("TR-1", "tipo"));
    }

    private io.restassured.response.ValidatableResponse importar(String token, String base, String csv) {
        return given().header("Authorization", "Bearer " + token)
                .multiPart("archivo", "insumos.csv", csv.getBytes(), "text/csv")
                .post("/api/v1/admin/bases-centrales/" + base + "/insumos/import")
                .then()
                .statusCode(200);
    }

    private String campo(String codigo, String columna) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT " + columna + "::text FROM insumo WHERE codigo = ?")) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private String registrarSuperAdmin(String email) {
        String token = AuthSupport.registrarConToken(mailbox, email);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE usuario SET rol = 'SUPER_ADMIN' WHERE email = ?")) {
            ps.setString(1, email);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Response login = given().contentType(JSON)
                .body(Map.of("email", email, "password", "Pass1234", "recordarSesion", false))
                .post("/api/v1/auth/login");
        login.then().statusCode(200);
        return login.path("accessToken");
    }
}
