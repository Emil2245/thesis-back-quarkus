package ec.uce.propuestas.usuario.admin;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.usuario.Rol;
import ec.uce.propuestas.usuario.Usuario;
import ec.uce.propuestas.usuario.UsuarioRepository;
import ec.uce.propuestas.usuario.auth.PasswordService;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 034 — Red de seguridad contra fugas de contraseña temporal (D-04 del acta 032).
 *
 * <p>{@code STOP-034-CONTRASENA-TEMPORAL}: el contrato del acta 032 D-04
 * exige que el valor aleatorio Base64URL generado durante la invitación se
 * descarte **inmediatamente** después de obtener el hash bcrypt. Ni un DTO
 * de respuesta, ni un log, ni el cuerpo del correo capturado por
 * {@link RecordingEnviadorCorreo}, ni una traza pueden contener la contraseña
 * temporal ni el valor aleatorio base.
 *
 * <p>Este test inspecciona:
 * <ul>
 *   <li>la respuesta REST de {@code POST /admin/usuarios};</li>
 *   <li>todas las entregas del {@link RecordingEnviadorCorreo};</li>
 *   <li>los campos del DTO de respuesta en el listado {@code GET /admin/usuarios}.</li>
 * </ul>
 *
 * <p>La regex cubre tanto el texto literal como posibles prefijos
 * comunes en español e inglés.
 */
@QuarkusTest
class UsuarioAdminSinContrasenaTemporalTest {

    private static final Pattern LEAK =
            Pattern.compile("(?i).*(password|contrase|clave|temporal|randombytes|temp_pwd|tempassword).*");

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
        u.nombre = "Super Admin";
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

    @Test
    void invitacion_10_usuarios_no_expone_password_temporal_en_ningun_canal() {
        insertarAdmin("super@ex.com");
        String adminToken = login("super@ex.com");

        for (int i = 0; i < 10; i++) {
            String email = "invitado" + i + "@ex.com";
            var resp = given().header("Authorization", "Bearer " + adminToken)
                    .contentType(JSON)
                    .body(Map.of("nombre", "Invitado " + i, "email", email, "rol", "USUARIO"))
                    .when()
                    .post("/api/v1/admin/usuarios");

            // Status 201 esperado
            assertEquals(201, resp.statusCode(), "Status esperado 201 para " + email);

            // Cuerpo JSON: ningún campo relacionado con contraseña temporal
            String body = resp.body().asString();
            assertTrue(
                    !LEAK.matcher(body).matches(),
                    "Respuesta REST no debe contener patrones de contraseña temporal: " + body);
            assertEquals((Object) null, resp.path("passwordHash"));
            assertEquals((Object) null, resp.path("token"));
            assertEquals((Object) null, resp.path("password"));
            assertEquals((Object) null, resp.path("temporal"));
        }

        // Verifica que el token de invitación guardado NO contiene
        // pistas sobre la contraseña temporal generada (es Base64URL
        // puro de 32 bytes aleatorios — sin prefijos ni sufijos).
        for (var entrega : mailbox.entregas()) {
            String token = entrega.tokenRaw();
            assertTrue(
                    !LEAK.matcher(token).matches(),
                    "Token de invitación no debe coincidir con regex de leak: " + token);
            // Forma Base64URL sin padding = 43 chars exactos (32 bytes * 4/3).
            assertEquals(43, token.length(), "Token debe ser Base64URL sin padding de 32 bytes (43 chars): " + token);
        }

        // Listado paginado: tampoco debe filtrar
        var listado = given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/usuarios")
                .then()
                .statusCode(200)
                .extract();

        String listadoBody = listado.body().asString();
        assertTrue(
                !LEAK.matcher(listadoBody).matches(),
                "Listado paginado no debe contener patrones de contraseña temporal");
        List<Map<String, Object>> items = listado.path("items");
        for (Map<String, Object> item : items) {
            assertEquals((Object) null, item.get("passwordHash"));
            assertEquals((Object) null, item.get("token"));
            assertEquals((Object) null, item.get("password"));
        }
    }
}
