package ec.uce.propuestas.insumo.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;

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

@QuarkusTest
class AdminBaseCentralLogAuditoriaIT {

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE log_actividad, apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    @Test
    void crear_emite_detalle_exacto() throws Exception {
        String token = registrarSuperAdmin("audit-crear@ex.com");
        String baseId = crearBase(token, "Audit crear");

        assertLog(baseId, "crear", 0);
    }

    @Test
    void get_listado_no_emite() throws Exception {
        String token = registrarSuperAdmin("audit-get@ex.com");
        String baseId = crearBase(token, "Audit get");
        long eventosAntes = contarEventosDeBase(baseId);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(200);

        org.junit.jupiter.api.Assertions.assertEquals(eventosAntes, contarEventosDeBase(baseId));
    }

    @Test
    void renombrar_emite_detalle_exacto() throws Exception {
        String token = registrarSuperAdmin("audit-renombrar@ex.com");
        String baseId = crearBase(token, "Antes");
        given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of("nombre", "Después"))
                .when()
                .put("/api/v1/admin/bases-centrales/" + baseId)
                .then()
                .statusCode(200);
        assertLog(baseId, "renombrar", 0);
    }

    @Test
    void archivar_emite_detalle_exacto() throws Exception {
        String token = registrarSuperAdmin("audit-archivar@ex.com");
        String baseId = crearBase(token, "Archivar");
        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/admin/bases-centrales/" + baseId + "/archivar")
                .then()
                .statusCode(200);
        assertLog(baseId, "archivar", 0);
    }

    @Test
    void borrar_base_archivada_emite_antes_del_delete() throws Exception {
        String token = registrarSuperAdmin("audit-borrar@ex.com");
        String baseId = crearBase(token, "Borrar");
        crearInsumo(token, baseId, "BOR-1");
        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/admin/bases-centrales/" + baseId + "/archivar")
                .then()
                .statusCode(200);
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/admin/bases-centrales/" + baseId)
                .then()
                .statusCode(204);
        assertLog(baseId, "borrar", 1);
    }

    @Test
    void importar_emite_detalle_exacto() throws Exception {
        String token = registrarSuperAdmin("audit-importar@ex.com");
        String baseId = crearBase(token, "Importar");
        String csv = "codigo,descripcion,unidad,precio\nIMP-1,Uno,kg,1.00\nIMP-2,Dos,kg,2.00\n";

        given().header("Authorization", "Bearer " + token)
                .multiPart("archivo", "insumos.csv", csv.getBytes(), "text/csv")
                .when()
                .post("/api/v1/admin/bases-centrales/" + baseId + "/insumos/import")
                .then()
                .statusCode(200);

        assertLog(baseId, "importar", 2);
    }

    @Test
    void importar_solo_validar_no_emite() throws Exception {
        String token = registrarSuperAdmin("audit-dry-run@ex.com");
        String baseId = crearBase(token, "Dry run");
        String csv = "codigo,descripcion,unidad,precio\nDRY-1,Uno,kg,1.00\n";

        given().header("Authorization", "Bearer " + token)
                .multiPart("archivo", "insumos.csv", csv.getBytes(), "text/csv")
                .when()
                .post("/api/v1/admin/bases-centrales/" + baseId + "/insumos/import?soloValidar=true")
                .then()
                .statusCode(200);

        org.junit.jupiter.api.Assertions.assertEquals(0, contar(baseId, "importar"));
    }

    @Test
    void crear_insumo_emite_detalle_exacto() throws Exception {
        String token = registrarSuperAdmin("audit-crear-insumo@ex.com");
        String baseId = crearBase(token, "Crear insumo");
        crearInsumo(token, baseId, "CRE-1");
        assertLog(baseId, "crearInsumo", 1);
    }

    @Test
    void editar_insumo_emite_detalle_exacto() throws Exception {
        String token = registrarSuperAdmin("audit-editar-insumo@ex.com");
        String baseId = crearBase(token, "Editar insumo");
        String insumoId = crearInsumo(token, baseId, "EDI-1");
        given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of("descripcion", "Editado", "unidad", "kg", "precioUnitario", 3.0))
                .when()
                .put("/api/v1/admin/bases-centrales/" + baseId + "/insumos/" + insumoId)
                .then()
                .statusCode(200);
        assertLog(baseId, "editarInsumo", 1);
    }

    @Test
    void borrar_insumo_no_referenciado_emite_detalle_exacto() throws Exception {
        String token = registrarSuperAdmin("audit-borrar-insumo@ex.com");
        String baseId = crearBase(token, "Borrar insumo");
        String insumoId = crearInsumo(token, baseId, "DEL-1");
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/admin/bases-centrales/" + baseId + "/insumos/" + insumoId)
                .then()
                .statusCode(204);
        assertLog(baseId, "borrarInsumo", 0);
    }

    @Test
    void borrar_base_activa_rechazada_no_emite() throws Exception {
        String token = registrarSuperAdmin("audit-reject-base@ex.com");
        String baseId = crearBase(token, "Base activa");

        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/admin/bases-centrales/" + baseId)
                .then()
                .statusCode(409)
                .body("codigo", equalTo("base-no-archivada"));

        org.junit.jupiter.api.Assertions.assertEquals(0, contar(baseId, "borrar"));
    }

    @Test
    void borrar_insumo_referenciado_rechazado_no_emite() throws Exception {
        String email = "audit-reject-insumo@ex.com";
        String token = registrarSuperAdmin(email);
        String baseId = crearBase(token, "Base referenciada");
        String insumoId = crearInsumo(token, baseId, "REF-1");
        insertarReferenciaApu(email, insumoId);

        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/admin/bases-centrales/" + baseId + "/insumos/" + insumoId)
                .then()
                .statusCode(409)
                .body("codigo", equalTo("insumo-en-uso"));

        org.junit.jupiter.api.Assertions.assertEquals(0, contar(baseId, "borrarInsumo"));
    }

    private String crearBase(String token, String nombre) {
        return given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of("nombre", nombre))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String crearInsumo(String token, String baseId, String codigo) {
        return given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of(
                        "codigo",
                        codigo,
                        "tipo",
                        "MATERIAL",
                        "descripcion",
                        codigo,
                        "unidad",
                        "kg",
                        "precioUnitario",
                        1.0))
                .when()
                .post("/api/v1/admin/bases-centrales/" + baseId + "/insumos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private void assertLog(String baseId, String operacion, int cantidadInsumos) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT entidad, entidad_public_id, detalle, jsonb_typeof(detalle->'cantidadInsumos') tipo "
                                + "FROM log_actividad WHERE evento = 'admin.base_editada' "
                                + "AND entidad_public_id = ?::uuid AND detalle->>'operacion' = ?")) {
            ps.setString(1, baseId);
            ps.setString(2, operacion);
            try (ResultSet rs = ps.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(rs.next(), "Falta log para " + operacion);
                org.junit.jupiter.api.Assertions.assertEquals("base_insumos", rs.getString("entidad"));
                org.junit.jupiter.api.Assertions.assertEquals(
                        baseId, rs.getObject("entidad_public_id").toString());
                org.junit.jupiter.api.Assertions.assertEquals("number", rs.getString("tipo"));
                String detalle = rs.getString("detalle");
                com.fasterxml.jackson.databind.JsonNode json =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(detalle);
                org.junit.jupiter.api.Assertions.assertEquals(2, json.size());
                org.junit.jupiter.api.Assertions.assertEquals(
                        operacion, json.get("operacion").asText());
                org.junit.jupiter.api.Assertions.assertEquals(
                        cantidadInsumos, json.get("cantidadInsumos").intValue());
                org.junit.jupiter.api.Assertions.assertFalse(rs.next(), "Más de un log para " + operacion);
            }
        }
    }

    private long contar(String baseId, String operacion) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT count(*) FROM log_actividad WHERE evento = 'admin.base_editada' "
                                + "AND entidad_public_id = ?::uuid AND detalle->>'operacion' = ?")) {
            ps.setString(1, baseId);
            ps.setString(2, operacion);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long contarEventosDeBase(String baseId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT count(*) FROM log_actividad WHERE evento = 'admin.base_editada' "
                                + "AND entidad_public_id = ?::uuid")) {
            ps.setString(1, baseId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void insertarReferenciaApu(String email, String insumoPublicId) throws Exception {
        try (Connection con = ds.getConnection()) {
            long usuarioId = scalar(con, "SELECT id FROM usuario WHERE email = ?", email);
            long insumoId = scalar(con, "SELECT id FROM insumo WHERE public_id = ?::uuid", insumoPublicId);
            long proyectoId = insertarRetornandoId(
                    con,
                    "INSERT INTO proyecto(usuario_id,nombre_proyecto,anio,direccion_institucional) "
                            + "VALUES (?, 'Fixture auditoría', 2026, 'UCE') RETURNING id",
                    usuarioId);
            long presupuestoId = insertarRetornandoId(
                    con,
                    "INSERT INTO presupuesto(proyecto_id,version,es_vigente) VALUES (?, 1, true) RETURNING id",
                    proyectoId);
            long apuId = insertarRetornandoId(
                    con,
                    "INSERT INTO apu(presupuesto_id,codigo,descripcion,unidad) "
                            + "VALUES (?, 'AUD-1', 'Auditoría', 'u') RETURNING id",
                    presupuestoId);
            long seccionId = insertarRetornandoId(
                    con, "INSERT INTO apu_seccion(apu_id,tipo,orden) VALUES (?, 'MATERIAL', 1) RETURNING id", apuId);
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO apu_detalle(seccion_id,insumo_id,descripcion,orden,cantidad,unidad) "
                            + "VALUES (?, ?, 'Referencia directa', 1, 1, 'kg')")) {
                ps.setLong(1, seccionId);
                ps.setLong(2, insumoId);
                ps.executeUpdate();
            }
        }
    }

    private long scalar(Connection con, String sql, Object value) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long insertarRetornandoId(Connection con, String sql, long parentId) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, parentId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private String registrarSuperAdmin(String email) {
        AuthSupport.registrarConToken(mailbox, email);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE usuario SET rol = 'SUPER_ADMIN' WHERE email = ?")) {
            ps.setString(1, email);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Response login = given().contentType(JSON)
                .body(Map.of("email", email, "password", "Pass1234", "recordarSesion", false))
                .when()
                .post("/api/v1/auth/login");
        login.then().statusCode(200).body("accessToken", org.hamcrest.Matchers.notNullValue());
        return login.path("accessToken");
    }
}
