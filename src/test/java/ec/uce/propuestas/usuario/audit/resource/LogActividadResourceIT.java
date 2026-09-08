package ec.uce.propuestas.usuario.audit.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LogActividadResourceIT {

    private static final String UUID_V7 =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";
    private static final UUID ENTIDAD_ID = UUID.fromString("0192f6c4-7c8a-7000-8000-000000000301");

    @Inject
    DataSource ds;

    @Inject
    RecordingEnviadorCorreo mailbox;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE log_actividad, token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    @Test
    void exige_super_admin_y_no_expone_endpoints_de_escritura() {
        String usuario = AuthSupport.registrarConToken(mailbox, "logs-user@ex.com");
        given().header("Authorization", "Bearer " + usuario)
                .get("/api/v1/admin/logs")
                .then()
                .statusCode(403);
        given().get("/api/v1/admin/logs").then().statusCode(401);

        String admin = registrarSuperAdmin("logs-no-write-admin@ex.com");
        given().header("Authorization", "Bearer " + admin)
                .post("/api/v1/admin/logs")
                .then()
                .statusCode(405);
    }

    @Test
    void lista_en_orden_determinista_con_page_canonica_y_actor() throws Exception {
        String token = registrarSuperAdmin("logs-admin@ex.com");
        Actor actor = insertarActor("Actor visible", "actor-visible@ex.com");
        insertarLog(actor.id(), "auth.login", Instant.parse("2026-09-08T10:00:00Z"), ENTIDAD_ID, null);
        insertarLog(actor.id(), "auth.logout", Instant.parse("2026-09-08T11:00:00Z"), null, null);

        given().header("Authorization", "Bearer " + token)
                .queryParam("size", 1)
                .get("/api/v1/admin/logs")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].id", matchesPattern(UUID_V7))
                .body("items[0].evento", equalTo("auth.logout"))
                .body("items[0].usuarioId", equalTo(actor.publicId().toString()))
                .body("items[0].usuarioNombre", equalTo("Actor visible"))
                .body("total", equalTo(2))
                .body("page", equalTo(0))
                .body("size", equalTo(1))
                .body("totalPaginas", equalTo(2));
    }

    @Test
    void desempata_por_public_id_descendente_cuando_la_fecha_coincide() throws Exception {
        String token = registrarSuperAdmin("logs-tie-admin@ex.com");
        Instant mismaFecha = Instant.parse("2026-09-08T10:00:00Z");
        UUID primero = insertarLog(null, "auth.login", mismaFecha, null, null);
        UUID segundo = insertarLog(null, "auth.logout", mismaFecha, null, null);
        UUID esperado = primero.compareTo(segundo) > 0 ? primero : segundo;

        given().header("Authorization", "Bearer " + token)
                .queryParam("size", 1)
                .get("/api/v1/admin/logs")
                .then()
                .statusCode(200)
                .body("items[0].id", equalTo(esperado.toString()));
    }

    @Test
    void aplica_filtros_con_semantica_and_y_rango_inclusivo() throws Exception {
        String token = registrarSuperAdmin("logs-filter-admin@ex.com");
        Actor actor = insertarActor("Actor A", "actor-a@ex.com");
        Actor otro = insertarActor("Actor B", "actor-b@ex.com");
        insertarLog(actor.id(), "auth.login", Instant.parse("2026-09-08T10:00:00Z"), null, null);
        insertarLog(actor.id(), "auth.logout", Instant.parse("2026-09-08T11:00:00Z"), null, null);
        insertarLog(otro.id(), "auth.login", Instant.parse("2026-09-08T10:30:00Z"), null, null);

        given().header("Authorization", "Bearer " + token)
                .queryParam("usuarioId", actor.publicId())
                .queryParam("evento", "auth.login")
                .queryParam("desde", "2026-09-08T09:59:59Z")
                .queryParam("hasta", "2026-09-08T10:00:00Z")
                .get("/api/v1/admin/logs")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].evento", equalTo("auth.login"))
                .body("items[0].usuarioId", equalTo(actor.publicId().toString()))
                .body("total", equalTo(1));
    }

    @Test
    void evento_seguro_desconocido_da_200_y_legacy_sigue_consultable_sin_fuga_bigint() throws Exception {
        String token = registrarSuperAdmin("logs-legacy-admin@ex.com");
        insertarLog(null, "base.insumos.copiada", Instant.parse("2026-09-08T10:00:00Z"), null, 987L);

        given().header("Authorization", "Bearer " + token)
                .queryParam("evento", "no.en.catalogo")
                .get("/api/v1/admin/logs")
                .then()
                .statusCode(200)
                .body("items", hasSize(0))
                .body("total", equalTo(0))
                .body("totalPaginas", equalTo(0));

        given().header("Authorization", "Bearer " + token)
                .queryParam("evento", "base.insumos.copiada")
                .get("/api/v1/admin/logs")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].entidadId", nullValue())
                .body("items[0]", not(hasKey("entidadIdLegacy")))
                .body("items[0]", not(hasKey("entidadIdInterno")))
                .body("items[0]", not(hasKey("entidadIdBigint")));
    }

    @Test
    void expone_entidad_publica_de_fila_nueva() throws Exception {
        String token = registrarSuperAdmin("logs-entity-admin@ex.com");
        insertarLog(null, "proyecto.creado", Instant.parse("2026-09-08T10:00:00Z"), ENTIDAD_ID, null);
        given().header("Authorization", "Bearer " + token)
                .queryParam("evento", "proyecto.creado")
                .get("/api/v1/admin/logs")
                .then()
                .statusCode(200)
                .body("items[0].entidadId", equalTo(ENTIDAD_ID.toString()));
    }

    @Test
    void valida_paginacion_uuid_evento_instants_y_rango() {
        String token = registrarSuperAdmin("logs-validation-admin@ex.com");
        for (Map.Entry<String, Object> invalid : Map.<String, Object>of(
                        "page", -1,
                        "size", 0,
                        "usuarioId", "550e8400-e29b-41d4-a716-446655440000",
                        "evento", "inseguro<<",
                        "desde", "ayer")
                .entrySet()) {
            given().header("Authorization", "Bearer " + token)
                    .queryParam(invalid.getKey(), invalid.getValue())
                    .get("/api/v1/admin/logs")
                    .then()
                    .statusCode(400)
                    .body("codigo", equalTo("validacion"));
        }
        given().header("Authorization", "Bearer " + token)
                .queryParam("size", 201)
                .get("/api/v1/admin/logs")
                .then()
                .statusCode(400)
                .body("mensaje", equalTo("tamano-pagina-invalido"));
        given().header("Authorization", "Bearer " + token)
                .queryParam("evento", "a".repeat(61))
                .get("/api/v1/admin/logs")
                .then()
                .statusCode(400)
                .body("mensaje", equalTo("evento-largo"));
        given().header("Authorization", "Bearer " + token)
                .queryParam("desde", "2026-09-09T00:00:00Z")
                .queryParam("hasta", "2026-09-08T00:00:00Z")
                .get("/api/v1/admin/logs")
                .then()
                .statusCode(400)
                .body("mensaje", equalTo("rango-fechas-invalido"));
    }

    private String registrarSuperAdmin(String email) {
        AuthSupport.registrarConToken(mailbox, email);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("update usuario set rol = 'SUPER_ADMIN' where email = ?")) {
            ps.setString(1, email);
            ps.executeUpdate();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return given().contentType(JSON)
                .body(Map.of("email", email, "password", "Pass1234", "recordarSesion", false))
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }

    private Actor insertarActor(String nombre, String email) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "insert into usuario(nombre,email,password_hash,email_verificado) values (?,?,?,true) "
                                + "returning id, public_id")) {
            ps.setString(1, nombre);
            ps.setString(2, email);
            ps.setString(3, "hash-no-utilizable");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new Actor(rs.getLong(1), rs.getObject(2, UUID.class));
            }
        }
    }

    private UUID insertarLog(Long actorId, String evento, Instant fecha, UUID entidadPublicId, Long entidadLegacyId)
            throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "insert into log_actividad(usuario_id,evento,entidad,entidad_id,entidad_public_id,detalle,created_at) "
                                + "values (?,?,'fixture',?,?, '{}'::jsonb,?) returning public_id")) {
            if (actorId == null) ps.setNull(1, java.sql.Types.BIGINT);
            else ps.setLong(1, actorId);
            ps.setString(2, evento);
            if (entidadLegacyId == null) ps.setNull(3, java.sql.Types.BIGINT);
            else ps.setLong(3, entidadLegacyId);
            ps.setObject(4, entidadPublicId);
            ps.setTimestamp(5, Timestamp.from(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private record Actor(Long id, UUID publicId) {}
}
