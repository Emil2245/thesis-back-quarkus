package ec.uce.propuestas.proyecto.admin;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ValorReferenciaAdminResourceIT {

    @Inject
    DataSource ds;

    @Inject
    RecordingEnviadorCorreo mailbox;

    @BeforeEach
    void reset() throws Exception {
        Plan037TestSupport.reset(ds, mailbox);
    }

    @Test
    void TC_P41_02_lista_las_cuatro_filas_v004_con_page_canonica() throws Exception {
        String admin = Plan037TestSupport.registrarSuperAdmin(ds, mailbox, "p41-list@ex.com");
        given().header("Authorization", "Bearer " + admin)
                .queryParam("page", 0)
                .queryParam("size", 2)
                .when()
                .get("/api/v1/admin/valores-referencia")
                .then()
                .statusCode(200)
                .body("items", hasSize(2))
                .body("items[0].clave", equalTo("APORTE_PATRONAL"))
                .body("items[0].actualizado", notNullValue())
                .body("items[0].updatedAt", equalTo(null))
                .body("total", equalTo(4))
                .body("page", equalTo(0))
                .body("size", equalTo(2))
                .body("totalPaginas", equalTo(2));
    }

    @Test
    void TC_P41_02_upsert_inserta_y_actualiza_con_auditoria() throws Exception {
        String admin = Plan037TestSupport.registrarSuperAdmin(ds, mailbox, "p41-upsert@ex.com");
        Map<String, Object> inicial =
                Map.of("valor", "999.99", "descripcion", "Referencia nueva", "fuente", "Fuente verificable");

        String creado = given().header("Authorization", "Bearer " + admin)
                .contentType(JSON)
                .body(inicial)
                .when()
                .put("/api/v1/admin/valores-referencia/SU_NUEVO")
                .then()
                .statusCode(201)
                .body("clave", equalTo("SU_NUEVO"))
                .body("actualizado", notNullValue())
                .extract()
                .path("actualizado");

        Thread.sleep(5);
        String actualizado = given().header("Authorization", "Bearer " + admin)
                .contentType(JSON)
                .body(Map.of("valor", "1000.00", "descripcion", "Referencia actualizada", "fuente", "Otra fuente"))
                .when()
                .put("/api/v1/admin/valores-referencia/SU_NUEVO")
                .then()
                .statusCode(200)
                .body("fuente", equalTo("Otra fuente"))
                .extract()
                .path("actualizado");
        assertNotEquals(creado, actualizado);

        assertEquals(1L, log("valor_referencia.insert", "SU_NUEVO"));
        assertEquals(1L, log("valor_referencia.update", "SU_NUEVO"));
    }

    @Test
    void TC_P41_02_actualiza_seed_y_elimina_sin_reseed() throws Exception {
        String admin = Plan037TestSupport.registrarSuperAdmin(ds, mailbox, "p41-delete@ex.com");
        given().header("Authorization", "Bearer " + admin)
                .contentType(JSON)
                .body(Map.of("valor", "470.00", "descripcion", "SBU vigente", "fuente", "Ministerio del Trabajo 2026"))
                .when()
                .put("/api/v1/admin/valores-referencia/SBU")
                .then()
                .statusCode(200)
                .body("valor", equalTo("470.00"));

        given().header("Authorization", "Bearer " + admin)
                .when()
                .delete("/api/v1/admin/valores-referencia/SBU")
                .then()
                .statusCode(204);
        assertEquals(1L, log("valor_referencia.delete", "SBU"));

        given().header("Authorization", "Bearer " + admin)
                .when()
                .delete("/api/v1/admin/valores-referencia/SBU")
                .then()
                .statusCode(404);
        assertEquals(1L, log("valor_referencia.delete", "SBU"));
    }

    @Test
    void validacion_roles_y_rechazos_no_emiten() throws Exception {
        String admin = Plan037TestSupport.registrarSuperAdmin(ds, mailbox, "p41-validation@ex.com");
        String user = Plan037TestSupport.registrarUsuario(mailbox, "p41-normal@ex.com");
        Map<String, Object> valido = Map.of("valor", "1", "descripcion", "D", "fuente", "F");

        given().header("Authorization", "Bearer " + user)
                .contentType(JSON)
                .body(valido)
                .when()
                .put("/api/v1/admin/valores-referencia/CLAVE")
                .then()
                .statusCode(403);
        given().header("Authorization", "Bearer " + admin)
                .contentType(JSON)
                .body(Map.of("valor", "1", "descripcion", "D", "fuente", " "))
                .when()
                .put("/api/v1/admin/valores-referencia/CLAVE")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
        given().header("Authorization", "Bearer " + admin)
                .contentType(JSON)
                .body(valido)
                .when()
                .put("/api/v1/admin/valores-referencia/" + "X".repeat(51))
                .then()
                .statusCode(400)
                .body("codigo", equalTo("clave-excedida"));
        given().header("Authorization", "Bearer " + admin)
                .queryParam("size", 201)
                .when()
                .get("/api/v1/admin/valores-referencia")
                .then()
                .statusCode(400);
        assertEquals(0L, totalLogs());
    }

    private long log(String operacion, String clave) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT count(*) FROM log_actividad WHERE evento='admin.parametros_editados' "
                                + "AND entidad='valor_referencia' AND entidad_public_id IS NULL "
                                + "AND detalle->>'operacion'=? AND detalle->>'clave'=?")) {
            ps.setString(1, operacion);
            ps.setString(2, clave);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long totalLogs() throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT count(*) FROM log_actividad WHERE evento='admin.parametros_editados'")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
