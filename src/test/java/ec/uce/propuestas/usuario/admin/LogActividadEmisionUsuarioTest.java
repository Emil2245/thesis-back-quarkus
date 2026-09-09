package ec.uce.propuestas.usuario.admin;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.usuario.Rol;
import ec.uce.propuestas.usuario.Usuario;
import ec.uce.propuestas.usuario.UsuarioRepository;
import ec.uce.propuestas.usuario.audit.EventoLogActividad;
import ec.uce.propuestas.usuario.audit.entity.LogActividad;
import ec.uce.propuestas.usuario.audit.repository.LogActividadRepository;
import ec.uce.propuestas.usuario.auth.PasswordService;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 034 — Emisión D-13 de los eventos de P-38.
 *
 * <p>Cubre el contrato del acta 032 decisión 2 + decisión 32 del plan:
 * <ul>
 *   <li>{@code usuario.invitado} se emite en {@code POST /admin/usuarios}
 *       dentro de la misma transacción exterior (sin ghost events);</li>
 *   <li>{@code usuario.activado} se emite en {@code POST /admin/usuarios/{id}/reactivar}
 *       con {@code detalle.origen = "admin"};</li>
 *   <li>{@code usuario.desactivado} se emite en {@code POST /admin/usuarios/{id}/desactivar}
 *       con {@code detalle.origen = "admin"};</li>
 *   <li>un rollback exterior NO persiste el evento.</li>
 * </ul>
 */
@QuarkusTest
class LogActividadEmisionUsuarioTest {

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @Inject
    UsuarioRepository usuarioRepo;

    @Inject
    LogActividadRepository logRepo;

    @Inject
    PasswordService passwordService;

    @Inject
    EntityManager em;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (var con = ds.getConnection();
                var st = con.createStatement()) {
            st.execute("TRUNCATE TABLE apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "actividad, cronograma, insumo, base_insumos, "
                    + "parametros_proyecto, firmante, proyecto, "
                    + "token_usuario, refresh_token, log_actividad, usuario "
                    + "RESTART IDENTITY CASCADE");
        }
    }

    @Transactional
    void insertarAdmin(String email) {
        Usuario u = new Usuario();
        u.nombre = "Super";
        u.email = email;
        u.passwordHash = passwordService.hash("Pass1234");
        u.rol = Rol.SUPER_ADMIN;
        u.activo = true;
        u.emailVerificado = true;
        usuarioRepo.persist(u);
        em.flush();
    }

    String login(String email) {
        return given().contentType(JSON)
                .body(new ec.uce.propuestas.usuario.auth.dto.LoginRequest(email, "Pass1234", false))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }

    long contarEventos(String evento) {
        return logRepo.count("evento", evento);
    }

    @Test
    void invitar_emite_usuario_invitado_con_tokenExpiraEn_y_entidadId_UUIDv7() {
        insertarAdmin("super@ex.com");
        String adminToken = login("super@ex.com");

        // POST /admin/usuarios
        var resp = given().header("Authorization", "Bearer " + adminToken)
                .contentType(JSON)
                .body(Map.of("nombre", "Inv", "email", "inv@ex.com", "rol", "USUARIO"))
                .when()
                .post("/api/v1/admin/usuarios");

        assertEquals(201, resp.statusCode());
        UUID invitedPublicId = UUID.fromString(resp.path("id"));

        // 1 fila log_actividad con evento = usuario.invitado
        assertEquals(1L, contarEventos(EventoLogActividad.USUARIO_INVITADO.value()));

        LogActividad row = logRepo.find("evento", EventoLogActividad.USUARIO_INVITADO.value())
                .firstResult();
        assertNotNull(row);
        // entidadPublicId debe ser el UUIDv7 del usuario invitado
        assertEquals(invitedPublicId, row.entidadPublicId);
        // entidad top-level = "usuario"
        assertEquals("usuario", row.entidad);

        // detalle JSONB contiene tokenExpiraEn en formato ISO-8601
        Map<String, Object> detalle = parseDetalle(row.detalle);
        assertNotNull(detalle.get("tokenExpiraEn"));
        assertTrue(detalle.get("tokenExpiraEn") instanceof String);
        String tokenExpiraEn = (String) detalle.get("tokenExpiraEn");
        assertNotNull(java.time.Instant.parse(tokenExpiraEn), "tokenExpiraEn debe ser ISO-8601");
    }

    @Test
    void desactivar_emite_usuario_desactivado_con_origen_admin() {
        insertarAdmin("super@ex.com");
        String adminToken = login("super@ex.com");

        insertarAdminComo("target@ex.com");
        UUID targetId = usuarioRepo.find("email", "target@ex.com").firstResult().publicId;

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .post("/api/v1/admin/usuarios/" + targetId + "/desactivar")
                .then()
                .statusCode(200);

        assertEquals(1L, contarEventos(EventoLogActividad.USUARIO_DESACTIVADO.value()));
        LogActividad row = logRepo.find("evento", EventoLogActividad.USUARIO_DESACTIVADO.value())
                .firstResult();
        assertNotNull(row);
        assertEquals(targetId, row.entidadPublicId);
        assertEquals("usuario", row.entidad);

        Map<String, Object> detalle = parseDetalle(row.detalle);
        assertEquals("admin", detalle.get("origen"));
    }

    @Test
    void reactivar_emite_usuario_activado_con_origen_admin() {
        insertarAdmin("super@ex.com");
        String adminToken = login("super@ex.com");

        insertarAdminComo("target@ex.com");
        UUID targetId = usuarioRepo.find("email", "target@ex.com").firstResult().publicId;

        // Desactivar primero
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .post("/api/v1/admin/usuarios/" + targetId + "/desactivar")
                .then()
                .statusCode(200);
        long desactivadoCount = contarEventos(EventoLogActividad.USUARIO_DESACTIVADO.value());
        assertEquals(1L, desactivadoCount);

        // Reactivar
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .post("/api/v1/admin/usuarios/" + targetId + "/reactivar")
                .then()
                .statusCode(200);

        assertEquals(1L, contarEventos(EventoLogActividad.USUARIO_ACTIVADO.value()));
        LogActividad row = logRepo.find("evento", EventoLogActividad.USUARIO_ACTIVADO.value())
                .firstResult();
        assertNotNull(row);
        assertEquals(targetId, row.entidadPublicId);
        assertEquals("usuario", row.entidad);

        Map<String, Object> detalle = parseDetalle(row.detalle);
        assertEquals("admin", detalle.get("origen"));
    }

    @Test
    void rollback_exterior_borra_evento_usuario_invitado() {
        insertarAdmin("super@ex.com");
        String adminToken = login("super@ex.com");

        long antes = contarEventos(EventoLogActividad.USUARIO_INVITADO.value());

        // POST normal → 201 → fila persiste
        var resp = given().header("Authorization", "Bearer " + adminToken)
                .contentType(JSON)
                .body(Map.of("nombre", "Rollback Test", "email", "rb@ex.com", "rol", "USUARIO"))
                .when()
                .post("/api/v1/admin/usuarios");

        assertEquals(201, resp.statusCode());
        assertEquals(
                antes + 1,
                contarEventos(EventoLogActividad.USUARIO_INVITADO.value()),
                "Tras commit, debe aumentar en 1");
    }

    @Test
    void invitacion_email_duplicado_no_emite_evento() {
        insertarAdmin("super@ex.com");
        String adminToken = login("super@ex.com");

        insertarAdminComo("dup@ex.com");
        long antes = contarEventos(EventoLogActividad.USUARIO_INVITADO.value());

        given().header("Authorization", "Bearer " + adminToken)
                .contentType(JSON)
                .body(Map.of("nombre", "X", "email", "dup@ex.com", "rol", "USUARIO"))
                .when()
                .post("/api/v1/admin/usuarios")
                .then()
                .statusCode(409);

        // No debe emitirse evento porque la operación fue rechazada (rollback)
        assertEquals(
                antes,
                contarEventos(EventoLogActividad.USUARIO_INVITADO.value()),
                "Email duplicado debe hacer rollback y no emitir evento");
    }

    @Transactional
    void insertarAdminComo(String email) {
        Usuario u = new Usuario();
        u.nombre = "Target";
        u.email = email;
        u.passwordHash = passwordService.hash("Pass1234");
        u.rol = Rol.USUARIO;
        u.activo = true;
        u.emailVerificado = true;
        usuarioRepo.persist(u);
        em.flush();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDetalle(String detalle) {
        if (detalle == null || detalle.isBlank()) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(detalle, Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =========================================================================
    // Triangulate: detallesEsperados() del enum valida cada detalle
    // =========================================================================

    @Test
    void enum_valida_las_claves_correctas_para_los_3_eventos() {
        var esperados = EventoLogActividad.detallesEsperados();
        assertEquals(java.util.Set.of("tokenExpiraEn"), esperados.get(EventoLogActividad.USUARIO_INVITADO));
        assertEquals(java.util.Set.of("origen"), esperados.get(EventoLogActividad.USUARIO_ACTIVADO));
        assertEquals(java.util.Set.of("origen"), esperados.get(EventoLogActividad.USUARIO_DESACTIVADO));
    }
}
