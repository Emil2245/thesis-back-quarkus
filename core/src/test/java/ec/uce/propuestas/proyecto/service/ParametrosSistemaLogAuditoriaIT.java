package ec.uce.propuestas.proyecto.service;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.proyecto.admin.Plan037TestSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ParametrosSistemaLogAuditoriaIT {

    @Inject
    DataSource ds;

    @Inject
    RecordingEnviadorCorreo mailbox;

    @BeforeEach
    void reset() throws Exception {
        Plan037TestSupport.reset(ds, mailbox);
    }

    @Test
    void TC_P41_01_get_retorna_dto_completo_y_defaults_solo_afectan_proyectos_nuevos() throws Exception {
        String admin = Plan037TestSupport.registrarSuperAdmin(ds, mailbox, "p41-admin@ex.com");
        String usuario = Plan037TestSupport.registrarUsuario(mailbox, "p41-user@ex.com");
        String viejo = Plan037TestSupport.crearProyecto(usuario, "Proyecto previo");

        Map<String, Object> response = given().header("Authorization", "Bearer " + usuario)
                .when()
                .get("/api/v1/proyectos/parametros-sistema")
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);
        assertEquals(
                Set.of(
                        "id",
                        "porcentajeHerramientaMenor",
                        "porcentajeIndirecto",
                        "iva",
                        "rangoHmMin",
                        "rangoHmMax",
                        "rangoCiMin",
                        "rangoCiMax",
                        "rangoDescuentoMin",
                        "rangoDescuentoMax",
                        "rangoIvaMin",
                        "rangoIvaMax",
                        "moneda",
                        "mostrarSeccionesVacias",
                        "sufijosSeccionActivos",
                        "mostrarSubtotalesSeccion",
                        "mostrarSubtotalesPie",
                        "mostrarNombreProyectoHeader",
                        "enumerarApus",
                        "mensajeFooter",
                        "modoCodigoRubro",
                        "updatedAt"),
                response.keySet());

        given().header("Authorization", "Bearer " + admin)
                .contentType(JSON)
                .body(Plan037TestSupport.parametrosSistema("0.0700", null, "0.1500"))
                .when()
                .put("/api/v1/proyectos/parametros-sistema")
                .then()
                .statusCode(200);

        String nuevo = Plan037TestSupport.crearProyecto(usuario, "Proyecto posterior");
        assertEquals(0, parametroProyecto(viejo, "porcentaje_herramienta_menor").compareTo(new BigDecimal("0.0500")));
        assertEquals(0, parametroProyecto(nuevo, "porcentaje_herramienta_menor").compareTo(new BigDecimal("0.0700")));

        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT detalle->>'operacion', detalle->'camposModificados', "
                                + "entidad_public_id, usuario_id IS NOT NULL FROM log_actividad "
                                + "WHERE evento='admin.parametros_editados' AND entidad='parametros_sistema'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("defaults.update", rs.getString(1));
                assertEquals("[\"porcentajeHerramientaMenor\"]", rs.getString(2));
                assertFalse(rs.getBoolean(3));
                assertTrue(rs.getBoolean(4));
                assertFalse(rs.next());
            }
        }
    }

    @Test
    void diff_es_numerico_estable_y_rechazo_no_emite() throws Exception {
        String admin = Plan037TestSupport.registrarSuperAdmin(ds, mailbox, "p41-diff@ex.com");
        Map<String, Object> body = Plan037TestSupport.parametrosSistema("0.0700", "0.1000", "0.1600");

        given().header("Authorization", "Bearer " + admin)
                .contentType(JSON)
                .body(body)
                .when()
                .put("/api/v1/proyectos/parametros-sistema")
                .then()
                .statusCode(200);

        assertEquals("[\"porcentajeHerramientaMenor\", \"porcentajeIndirecto\", \"iva\"]", camposModificados());

        body.put("rangoHmMin", "0.9000");
        body.put("rangoHmMax", "0.1000");
        given().header("Authorization", "Bearer " + admin)
                .contentType(JSON)
                .body(body)
                .when()
                .put("/api/v1/proyectos/parametros-sistema")
                .then()
                .statusCode(400);
        assertEquals(1L, contarLogs());
    }

    private BigDecimal parametroProyecto(String publicId, String columna) throws Exception {
        String sql = "SELECT pp." + columna + " FROM parametros_proyecto pp JOIN proyecto p ON p.id=pp.proyecto_id "
                + "WHERE p.public_id=?::uuid";
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, publicId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "El proyecto debe materializar parametros_proyecto al crearse");
                return rs.getBigDecimal(1);
            }
        }
    }

    private String camposModificados() throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT detalle->'camposModificados' FROM log_actividad WHERE evento='admin.parametros_editados'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private long contarLogs() throws Exception {
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
