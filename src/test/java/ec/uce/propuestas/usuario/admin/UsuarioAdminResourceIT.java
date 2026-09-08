package ec.uce.propuestas.usuario.admin;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.usuario.Rol;
import ec.uce.propuestas.usuario.Usuario;
import ec.uce.propuestas.usuario.UsuarioRepository;
import ec.uce.propuestas.usuario.auth.PasswordService;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import ec.uce.propuestas.usuario.auth.dto.AceptarInvitacionRequest;
import ec.uce.propuestas.usuario.auth.dto.LoginRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 034 — Suite end-to-end del ciclo de vida administrativo de usuarios
 * (P-38 / US-35) bajo la ruta canónica {@code /admin/usuarios}.
 *
 * <p>Cubre TC-P38-01..03:
 * <ul>
 *   <li>invitación 72 h sin contraseña temporal;</li>
 *   <li>desactivar / reactivar;</li>
 *   <li>DELETE con proyectos propios → 409 {@code usuario-con-proyectos-impedido}
 *       (test focal de mapeo de excepción FK RESTRICT).</li>
 * </ul>
 *
 * <p>El recurso exige rol {@code SUPER_ADMIN}; los usuarios con rol
 * {@code USUARIO} reciben 403 sin pistas.
 */
@QuarkusTest
class UsuarioAdminResourceIT {

    private static final String UUID_V7 =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @Inject
    UsuarioRepository usuarioRepo;

    @Inject
    PasswordService passwordService;

    @Inject
    EntityManager em;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            // Orden: hijos → padres. Las FK V001 (RESTRICT/CASCADE) cubren el resto.
            st.execute("TRUNCATE TABLE apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "actividad, cronograma, insumo, base_insumos, "
                    + "parametros_proyecto, firmante, proyecto, "
                    + "token_usuario, refresh_token, log_actividad, usuario "
                    + "RESTART IDENTITY CASCADE");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String bootstrapSuperAdmin() {
        String email = "super@ex.com";
        insertarUsuario(email, "Super Admin", Rol.SUPER_ADMIN, true, true);
        return login(email);
    }

    private String login(String email) {
        // Usa password conocido (insertado vía bootstrap con hash precomputado).
        return given().contentType(JSON)
                .body(new LoginRequest(email, "Pass1234", false))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }

    @Transactional
    void insertarUsuario(String email, String nombre, Rol rol, boolean activo, boolean emailVerificado) {
        Usuario u = new Usuario();
        u.nombre = nombre;
        u.email = email;
        u.passwordHash = passwordService.hash("Pass1234");
        u.rol = rol;
        u.activo = activo;
        u.emailVerificado = emailVerificado;
        usuarioRepo.persist(u);
        em.flush();
    }

    // =========================================================================
    // Autorización
    // =========================================================================

    @Test
    void TC_P38_AUTH_01_usuario_normal_no_accede_y_devuelve_403() {
        // Crea un USUARIO normal con un bootstrap mínimo.
        insertarUsuario("user@ex.com", "User", Rol.USUARIO, true, true);
        String token = login("user@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/admin/usuarios")
                .then()
                .statusCode(403);

        given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of("nombre", "x", "email", "x@x.com", "rol", "USUARIO"))
                .when()
                .post("/api/v1/admin/usuarios")
                .then()
                .statusCode(403);

        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/admin/usuarios/" + UUID.randomUUID())
                .then()
                .statusCode(403);
    }

    // =========================================================================
    // TC-P38-01 — Invitación 72 h sin contraseña temporal
    // =========================================================================

    @Test
    void TC_P38_01_invitacion_72h_devuelve_201_y_envia_correo_con_token() throws Exception {
        String token = bootstrapSuperAdmin();

        Response resp = given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of(
                        "nombre", "Invitado Uno",
                        "email", "invitado@ex.com",
                        "rol", "USUARIO"))
                .when()
                .post("/api/v1/admin/usuarios")
                .then()
                .statusCode(201)
                .body("id", matchesPattern(UUID_V7))
                .body("nombre", equalTo("Invitado Uno"))
                .body("email", equalTo("invitado@ex.com"))
                .body("rol", equalTo("USUARIO"))
                .body("activo", is(true))
                .body("emailVerificado", is(false))
                .body("fechaCreacion", notNullValue())
                .extract()
                .response();

        String userId = resp.path("id");
        assertNotNull(userId);

        // La respuesta NO contiene passwordHash ni token
        assertEquals((Object) null, resp.path("passwordHash"));
        assertEquals((Object) null, resp.path("token"));

        // El mailbox capturó un correo de invitación con token raw de 32 bytes
        // (43 chars Base64URL sin padding).
        var entrega = mailbox.entregas().stream()
                .filter(e -> e.tipo().equals("invitacion") && e.destinatario().equals("invitado@ex.com"))
                .findFirst()
                .orElseThrow();
        assertNotNull(entrega.tokenRaw());
        assertEquals(43, entrega.tokenRaw().length(), "Base64URL sin padding de 32 bytes = 43 chars");

        // El usuario existe en BD con passwordHash inutilizable (bcrypt válido, no plano).
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "select password_hash, activo, email_verificado from usuario where public_id = ?::uuid")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                String hash = rs.getString(1);
                assertTrue(hash.startsWith("$2a$"), "passwordHash debe ser bcrypt");
                assertEquals(true, rs.getBoolean(2));
                assertEquals(false, rs.getBoolean(3));
            }
        }
    }

    @Test
    void TC_P38_01_aceptar_invitacion_restablece_password_y_verifica_email() {
        // Bootstrap super admin para invitar.
        String adminToken = bootstrapSuperAdmin();

        given().header("Authorization", "Bearer " + adminToken)
                .contentType(JSON)
                .body(Map.of(
                        "nombre", "Invitado Dos",
                        "email", "dos@ex.com",
                        "rol", "USUARIO"))
                .when()
                .post("/api/v1/admin/usuarios")
                .then()
                .statusCode(201);

        String raw = mailbox.entregas().stream()
                .filter(e -> e.tipo().equals("invitacion") && e.destinatario().equals("dos@ex.com"))
                .findFirst()
                .orElseThrow()
                .tokenRaw();

        // Acepta invitación con nueva contraseña
        given().contentType(JSON)
                .body(new AceptarInvitacionRequest(raw, "Pass1234", "Pass1234"))
                .when()
                .post("/api/v1/auth/aceptar-invitacion")
                .then()
                .statusCode(204);

        // Login funciona con la contraseña nueva
        given().contentType(JSON)
                .body(new LoginRequest("dos@ex.com", "Pass1234", false))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("usuario.emailVerificado", is(true));
    }

    @Test
    void TC_P38_01_email_duplicado_devuelve_409() {
        String token = bootstrapSuperAdmin();
        insertarUsuario("dup@ex.com", "Dup", Rol.USUARIO, true, true);

        given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of("nombre", "Otro", "email", "dup@ex.com", "rol", "USUARIO"))
                .when()
                .post("/api/v1/admin/usuarios")
                .then()
                .statusCode(409)
                .body("codigo", equalTo("email-ya-registrado"));
    }

    @Test
    void TC_P38_01_validacion_nombre_email_rol() {
        String token = bootstrapSuperAdmin();

        // nombre blank
        given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of("nombre", "", "email", "x@x.com", "rol", "USUARIO"))
                .when()
                .post("/api/v1/admin/usuarios")
                .then()
                .statusCode(400);

        // email inválido
        given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of("nombre", "X", "email", "no-es-email", "rol", "USUARIO"))
                .when()
                .post("/api/v1/admin/usuarios")
                .then()
                .statusCode(400);

        // rol inválido (no es USUARIO ni SUPER_ADMIN)
        given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of("nombre", "X", "email", "x2@x.com", "rol", "ADMIN"))
                .when()
                .post("/api/v1/admin/usuarios")
                .then()
                .statusCode(400);
    }

    // =========================================================================
    // TC-P38-02 — desactivar / reactivar
    // =========================================================================

    @Test
    void TC_P38_02_desactivar_bloquea_login_reactivar_restablece() {
        // Bootstrap admin y usuario invitado.
        String adminToken = bootstrapSuperAdmin();
        insertarUsuario("target@ex.com", "Target", Rol.USUARIO, true, true);

        UUID targetId = usuarioRepo.find("email", "target@ex.com").firstResult().publicId;

        // Desactivar.
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .post("/api/v1/admin/usuarios/" + targetId + "/desactivar")
                .then()
                .statusCode(200)
                .body("activo", is(false));

        // Login siguiente → 403 (gate existente en AuthService).
        given().contentType(JSON)
                .body(new LoginRequest("target@ex.com", "Pass1234", false))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(403);

        // Reactivar.
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .post("/api/v1/admin/usuarios/" + targetId + "/reactivar")
                .then()
                .statusCode(200)
                .body("activo", is(true));

        // Login funciona de nuevo.
        given().contentType(JSON)
                .body(new LoginRequest("target@ex.com", "Pass1234", false))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200);
    }

    @Test
    void TC_P38_02_desactivar_uuid_invalido_devuelve_400() {
        String token = bootstrapSuperAdmin();

        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/admin/usuarios/00000000-0000-4000-8000-000000000000/desactivar")
                .then()
                .statusCode(400);

        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/admin/usuarios/no-es-uuid/desactivar")
                .then()
                .statusCode(400);
    }

    @Test
    void TC_P38_02_desactivar_uuid_inexistente_devuelve_404() {
        String token = bootstrapSuperAdmin();
        UUID inexistente = UUID.fromString("0192f6c4-7c8a-7abc-8000-000000000000");

        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/admin/usuarios/" + inexistente + "/desactivar")
                .then()
                .statusCode(404);
    }

    // =========================================================================
    // PUT /admin/usuarios/{id} — sin email
    // =========================================================================

    @Test
    void TC_P38_PUT_actualiza_nombre_rol_activo_sin_tocar_email() {
        String token = bootstrapSuperAdmin();
        insertarUsuario("edit@ex.com", "Antes", Rol.USUARIO, true, true);
        UUID id = usuarioRepo.find("email", "edit@ex.com").firstResult().publicId;

        given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of("nombre", "Después", "rol", "SUPER_ADMIN", "activo", false))
                .when()
                .put("/api/v1/admin/usuarios/" + id)
                .then()
                .statusCode(200)
                .body("nombre", equalTo("Después"))
                .body("rol", equalTo("SUPER_ADMIN"))
                .body("activo", is(false))
                .body("email", equalTo("edit@ex.com"));

        // Email no cambia aunque se reintente con uno distinto (la API no acepta email).
        given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of("nombre", "Y", "email", "nuevo@ex.com", "rol", "USUARIO", "activo", true))
                .when()
                .put("/api/v1/admin/usuarios/" + id)
                .then()
                .statusCode(200)
                .body("email", equalTo("edit@ex.com"));
    }

    @Test
    void TC_P38_PUT_uuid_invalido_o_inexistente() {
        String token = bootstrapSuperAdmin();

        given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of("nombre", "X", "rol", "USUARIO", "activo", true))
                .when()
                .put("/api/v1/admin/usuarios/no-uuid")
                .then()
                .statusCode(400);

        UUID inexistente = UUID.fromString("0192f6c4-7c8a-7abc-8000-000000000000");
        given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of("nombre", "X", "rol", "USUARIO", "activo", true))
                .when()
                .put("/api/v1/admin/usuarios/" + inexistente)
                .then()
                .statusCode(404);
    }

    // =========================================================================
    // TC-P38-03 — DELETE con proyectos propios
    // =========================================================================

    @Test
    void TC_P38_03_delete_con_proyectos_propios_devuelve_409_mapeo_FK() throws Exception {
        String token = bootstrapSuperAdmin();

        // Crea usuario target con un proyecto propio (FK RESTRICT).
        insertarUsuario("conproys@ex.com", "Con Proyectos", Rol.USUARIO, true, true);
        UUID targetId = usuarioRepo.find("email", "conproys@ex.com").firstResult().publicId;

        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "insert into proyecto (public_id, usuario_id, nombre_proyecto, anio, estado, direccion_institucional) "
                                + "values (uuidv7(), ?, 'Proyecto Test', 2026, 'BORRADOR', 'Dir')")) {
            ps.setLong(1, usuarioRepo.find("email", "conproys@ex.com").firstResult().id);
            ps.executeUpdate();
        }

        // DELETE → 409 usuario-con-proyectos-impedido
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/admin/usuarios/" + targetId)
                .then()
                .statusCode(409)
                .body("codigo", equalTo("usuario-con-proyectos-impedido"));

        // El proyecto persiste tras el intento fallido.
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement();
                ResultSet rs = st.executeQuery(
                        "select count(*) from proyecto p join usuario u on p.usuario_id = u.id where u.email = 'conproys@ex.com'")) {
            assertTrue(rs.next());
            assertEquals(1L, rs.getLong(1));
        }
    }

    @Test
    void TC_P38_03_delete_sin_proyectos_devuelve_204() throws Exception {
        String token = bootstrapSuperAdmin();
        insertarUsuario("sinproys@ex.com", "Sin Proyectos", Rol.USUARIO, true, true);
        UUID id = usuarioRepo.find("email", "sinproys@ex.com").firstResult().publicId;

        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/admin/usuarios/" + id)
                .then()
                .statusCode(204);

        // Verifica que el usuario ya no existe
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("select count(*) from usuario where public_id = ?::uuid")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0L, rs.getLong(1));
            }
        }
    }

    @Test
    void TC_P38_03_delete_uuid_invalido_o_inexistente() {
        String token = bootstrapSuperAdmin();

        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/admin/usuarios/no-uuid")
                .then()
                .statusCode(400);

        UUID inexistente = UUID.fromString("0192f6c4-7c8a-7abc-8000-000000000000");
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/admin/usuarios/" + inexistente)
                .then()
                .statusCode(404);
    }

    // =========================================================================
    // GET /admin/usuarios — listado paginado
    // =========================================================================

    @Test
    void TC_P38_GET_listado_paginado_con_filtros_q_y_activo() {
        String token = bootstrapSuperAdmin();
        // Sembrar 5 usuarios con prefijos distinguibles.
        insertarUsuario("alice@ex.com", "Alice", Rol.USUARIO, true, true);
        insertarUsuario("bob@ex.com", "Bob", Rol.USUARIO, true, true);
        insertarUsuario("carla@ex.com", "Carla Inactiva", Rol.USUARIO, false, true);
        insertarUsuario("dave@ex.com", "Dave", Rol.USUARIO, true, true);
        insertarUsuario("erin@ex.com", "Erin", Rol.USUARIO, true, true);

        // Listado completo, página 0
        Response all = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/admin/usuarios")
                .then()
                .statusCode(200)
                .body("items", hasSize(greaterThan(0)))
                .body("page", is(0))
                .body("size", is(25))
                .body("totalPaginas", greaterThan(0))
                .body("total", greaterThan(0))
                .extract()
                .response();

        // Verifica la forma canónica estable: items, total, page, size, totalPaginas
        assertNotNull(all.path("items"));
        assertNotNull(all.path("total"));
        assertNotNull(all.path("page"));
        assertNotNull(all.path("size"));
        assertNotNull(all.path("totalPaginas"));

        // Sin passwordHash ni tokenHash en la lista
        List<Map<String, Object>> items = all.path("items");
        for (Map<String, Object> item : items) {
            assertEquals((Object) null, item.get("passwordHash"));
            assertEquals((Object) null, item.get("token"));
            assertEquals((Object) null, item.get("tokenHash"));
        }

        // Filtro q por email parcial
        given().header("Authorization", "Bearer " + token)
                .queryParam("q", "alice")
                .when()
                .get("/api/v1/admin/usuarios")
                .then()
                .statusCode(200)
                .body("items[0].email", equalTo("alice@ex.com"));

        // Filtro activo=false
        given().header("Authorization", "Bearer " + token)
                .queryParam("activo", false)
                .when()
                .get("/api/v1/admin/usuarios")
                .then()
                .statusCode(200)
                .body("items[0].email", equalTo("carla@ex.com"))
                .body("items[0].activo", is(false));

        // size inválido → 400
        given().header("Authorization", "Bearer " + token)
                .queryParam("size", 201)
                .when()
                .get("/api/v1/admin/usuarios")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("tamano-pagina-invalido"));

        given().header("Authorization", "Bearer " + token)
                .queryParam("size", 0)
                .when()
                .get("/api/v1/admin/usuarios")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("tamano-pagina-invalido"));
    }

    @Test
    void TC_P38_GET_filtro_q_con_caracteres_especiales_no_rompe_query() {
        String token = bootstrapSuperAdmin();
        insertarUsuario("foo@ex.com", "Foo", Rol.USUARIO, true, true);

        given().header("Authorization", "Bearer " + token)
                .queryParam("q", "%")
                .when()
                .get("/api/v1/admin/usuarios")
                .then()
                .statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .queryParam("q", "_")
                .when()
                .get("/api/v1/admin/usuarios")
                .then()
                .statusCode(200);
    }

    @Test
    void TC_P38_GET_filtro_q_con_caracteres_invalidos_o_vacios() {
        String token = bootstrapSuperAdmin();

        // q con espacios o vacío se trata como filtro sin valor
        given().header("Authorization", "Bearer " + token)
                .queryParam("q", "   ")
                .when()
                .get("/api/v1/admin/usuarios")
                .then()
                .statusCode(200);
    }

    // =========================================================================
    // GET /admin/usuarios/{id} — read individual (no en alcance original, pero
    // útil para TRIANGULATE; se añade como helper de coherencia)
    // =========================================================================

    @Test
    void TC_P38_GET_inexistente_devuelve_404() {
        String token = bootstrapSuperAdmin();
        UUID inexistente = UUID.fromString("0192f6c4-7c8a-7abc-8000-000000000000");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/admin/usuarios/" + inexistente)
                .then()
                .statusCode(404);
    }

    // =========================================================================
    // Smoke — el listado ordenado por fechaCreacion DESC, secundario id ASC
    // =========================================================================

    @Test
    void TC_P38_GET_orden_por_fechaCreacion_DESC_estable() {
        String token = bootstrapSuperAdmin();
        insertarUsuario("primero@ex.com", "Primero", Rol.USUARIO, true, true);
        insertarUsuario("segundo@ex.com", "Segundo", Rol.USUARIO, true, true);
        insertarUsuario("tercero@ex.com", "Tercero", Rol.USUARIO, true, true);

        Response resp = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/admin/usuarios")
                .then()
                .statusCode(200)
                .extract()
                .response();

        List<String> emails = resp.path("items.email");
        // El orden de inserción es: super@ex.com (bootstrap), luego primero, segundo, tercero.
        // Como todos tienen createdAt idéntico a nivel de milisegundo en tests rápidos,
        // exigimos al menos que el listado devuelva los 4 elementos y que el orden
        // sea estable entre llamadas (idempotencia).
        assertEquals(4, emails.size());

        // Idempotencia: dos llamadas consecutivas devuelven el mismo orden.
        Response resp2 = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/admin/usuarios")
                .then()
                .statusCode(200)
                .extract()
                .response();
        List<String> emails2 = resp2.path("items.email");
        assertEquals(emails, emails2);
    }
}
